package com.istiqamah.app.notify

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.ui.IstiqamahTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * v0.16 কড়া মাত্রার পুরো পর্দা — অন্য সব অ্যাপের ওপরে একটা জানালা ("অন্য অ্যাপের ওপরে দেখানো" অনুমতি লাগে)।
 *
 * Activity নয় বলে Home বা Recents চাপলেও সরে না: লঞ্চারের ওপরেও থেকে যায়। Back-ও কিছু করে না।
 * সরে শুধু এর কোনো বোতাম চাপলে, অথবা ফোন/ম্যাক/নোটিফিকেশন থেকে এই নামাজে চিহ্ন দিলে।
 *
 * নিরাপত্তা: ফোনে কথা চলার সময় দেখাই না, আর কল শুরু হলে সাথে সাথে সরে যায় (নোটিফিকেশন থেকে যায়) —
 * নামাজের ডাক যেন কখনো কল ধরতে বা জরুরি কিছুতে বাধা না দেয়।
 */
object CallOverlay {
    private val main = Handler(Looper.getMainLooper())

    private class Shown(val key: String, val root: View, val owner: OverlayOwner)
    private var shown: Shown? = null

    private val _resolved = MutableStateFlow<Set<String>>(emptySet())
    /** যে ডাকগুলোর মীমাংসা হয়েছে — লক স্ক্রিনের পুরো পর্দার Activity এটা দেখে নিজে বন্ধ হয় */
    val resolved: StateFlow<Set<String>> = _resolved

    fun key(date: LocalDate, prayer: Prayer) = "$date|${prayer.ordinal}"

    fun canShow(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    /** ফোনে কথা চলছে বা রিং হচ্ছে (কোনো অনুমতি ছাড়াই অডিও মোড দেখে) */
    fun inCall(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        return am.mode == AudioManager.MODE_IN_CALL || am.mode == AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == AudioManager.MODE_RINGTONE
    }

    /** যেকোনো থ্রেড থেকে ডাকা যায় — দেখাতে পারলে true */
    fun show(ctx: Context, date: LocalDate, prayer: Prayer, title: String, endMillis: Long?, item: ContentItem?): Boolean {
        val app = ctx.applicationContext
        if (!canShow(app) || inCall(app)) return false
        val k = key(date, prayer)
        _resolved.value = _resolved.value - k
        main.post { runCatching { attach(app, k, date, prayer, title, endMillis, item) } }
        return true
    }

    /** এই নামাজের পর্দা সরানো (চিহ্ন দেওয়া, পরে, যাচ্ছি — সব পথ থেকে) */
    fun resolve(date: LocalDate, prayer: Prayer) {
        val k = key(date, prayer)
        _resolved.value = (_resolved.value + k).toList().takeLast(20).toSet()
        main.post { if (shown?.key == k) detach() }
    }

    private fun attach(ctx: Context, k: String, date: LocalDate, prayer: Prayer, title: String, endMillis: Long?, item: ContentItem?) {
        detach() // আগেরটা (অন্য ওয়াক্তের হলেও) সরিয়ে নতুনটা
        val wm = ctx.getSystemService(WindowManager::class.java) ?: return
        val owner = OverlayOwner().also { it.start() }
        val female = Prefs(ctx).gender == Gender.FEMALE

        val compose = ComposeView(ctx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                IstiqamahTheme {
                    // কল শুরু হলে সরে যাই — কল ধরা বা কথা বলায় যেন বাধা না হয়
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(1500)
                            if (inCall(ctx)) { main.post { if (shown?.key == k) detach() }; break }
                        }
                    }
                    StrongCallScreen(
                        title = title, endMillis = endMillis, item = item, female = female,
                        onGo = { work(ctx, date, prayer) { Engine.wuduGoing(ctx, date, prayer) } },
                        onLater = {
                            work(ctx, date, prayer) {
                                Notifier.cancelFor(ctx, date, prayer)
                                Engine.snooze(ctx, date, prayer, NotifKind.STRONG_CALL)
                            }
                        },
                        onPrayed = { s -> work(ctx, date, prayer) { Engine.checkIn(ctx, date, prayer, s) } },
                    )
                }
            }
        }
        // Back কিছু করে না
        val root = object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }.apply {
            addView(compose)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            isFocusableInTouchMode = true
        }

        @Suppress("DEPRECATION")
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            setTitle("IstiqamahCall")
        }
        wm.addView(root, lp)
        root.requestFocus()
        shown = Shown(k, root, owner)
    }

    private fun detach() {
        val s = shown ?: return
        shown = null
        runCatching { s.root.context.getSystemService(WindowManager::class.java)?.removeViewImmediate(s.root) }
        s.owner.stop()
    }

    /** কাজটা আলাদা থ্রেডে (ডেটাবেস), সাথে সাথে পর্দা সরাই */
    private fun work(ctx: Context, date: LocalDate, prayer: Prayer, f: () -> Unit) {
        resolve(date, prayer)
        thread(name = "istiqamah-call") { runCatching(f) }
    }

    /** ComposeView-এর জন্য ছোট্ট Lifecycle — Activity ছাড়া জানালায় Compose চালাতে লাগে */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
