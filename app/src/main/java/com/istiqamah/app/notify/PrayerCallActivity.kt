package com.istiqamah.app.notify

import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.ui.Emerald900
import com.istiqamah.app.ui.Gold
import com.istiqamah.app.ui.GoldLight
import com.istiqamah.app.ui.IstiqamahTheme
import com.istiqamah.app.ui.PrimingFlow
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * ঘরে-একা ওয়াক্তে অ্যালার্ম-ঘড়ির মতো পুরো পর্দার ডাক (লক স্ক্রিনের ওপরেও)।
 * "বন্ধ" বাটন ইচ্ছা করে ছোট — বড় বাটন শুধু প্রথম ছোট ধাপের: "ওযু করতে যাচ্ছি"।
 */
class PrayerCallActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_CONTENT = "content"
        private const val EXTRA_STRONG = "strong"
        private const val EXTRA_TITLE = "title"
        /** নোটিফিকেশনের আইডির সাথে না মেলার জন্য */
        private const val RC_OFFSET = 30_000_000

        fun callIntent(ctx: Context, date: LocalDate, prayer: Prayer, contentId: String?, strong: Boolean = false, title: String? = null): Intent =
            Intent(ctx, PrayerCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(Engine.EXTRA_DATE, date.toString())
                .putExtra(Engine.EXTRA_PRAYER, prayer.ordinal)
                .putExtra(EXTRA_CONTENT, contentId)
                .putExtra(EXTRA_STRONG, strong)
                .putExtra(EXTRA_TITLE, title)

        fun pendingIntent(ctx: Context, notifId: Int, date: LocalDate, prayer: Prayer, contentId: String?, strong: Boolean = false, title: String? = null): PendingIntent =
            PendingIntent.getActivity(
                ctx, RC_OFFSET + notifId, callIntent(ctx, date, prayer, contentId, strong, title),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val prayer = intent.getIntExtra(Engine.EXTRA_PRAYER, -1).takeIf { it in 0..4 }?.let(Prayer::fromOrdinal)
        val date = runCatching { LocalDate.parse(intent.getStringExtra(Engine.EXTRA_DATE)) }.getOrNull()
        if (prayer == null || date == null) { finish(); return }
        val item = intent.getStringExtra(EXTRA_CONTENT)?.let { ContentRepo.byId(this, it) }
            ?: ContentRepo.byId(this, "q_26_218_219")
        val end = Engine.windows(this, date)?.firstOrNull { it.prayer == prayer }?.end
        val prefs = Prefs(this)
        val strong = intent.getBooleanExtra(EXTRA_STRONG, false)

        if (strong) {
            // v0.15 কড়া মাত্রা: ফিরে যাওয়ার বোতামে বন্ধ হয় না — একটা বোতাম বেছে নিতে হয়
            onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {}
            })
            setFinishOnTouchOutside(false)
            val name = if (prayer == Prayer.DHUHR && date.dayOfWeek == java.time.DayOfWeek.FRIDAY) "জুমা/যোহর" else prayer.bn
            setContent {
                IstiqamahTheme {
                    StrongCallScreen(
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "এখন $name",
                        endMillis = end?.toInstant()?.toEpochMilli(),
                        item = intent.getStringExtra(EXTRA_CONTENT)?.let { ContentRepo.byId(this, it) },
                        female = prefs.gender == com.istiqamah.app.core.Gender.FEMALE,
                        onGo = { act { Engine.wuduGoing(this, date, prayer) } },
                        onLater = {
                            act {
                                Notifier.cancelFor(this, date, prayer)
                                Engine.snooze(this, date, prayer, com.istiqamah.app.core.NotifKind.STRONG_CALL)
                            }
                        },
                        onPrayed = { s -> act { Engine.checkIn(this, date, prayer, s) } },
                    )
                }
            }
            return
        }

        setContent {
            IstiqamahTheme {
                var priming by remember { mutableStateOf(false) }
                if (priming) {
                    PrimingFlow(
                        onWudu = { act { Engine.wuduGoing(this, date, prayer) } },
                        onPrayed = { act { Engine.checkIn(this, date, prayer, PrayerStatus.ALONE) } },
                        onClose = { priming = false },
                        dark = true,
                    )
                } else {
                    CallScreen(
                        name = if (prayer == Prayer.DHUHR && date.dayOfWeek == java.time.DayOfWeek.FRIDAY) "জুমা/যোহর" else prayer.bn,
                        endMillis = end?.toInstant()?.toEpochMilli(),
                        item = item,
                        pledge = prefs.pledge,
                        why = prefs.pledgeWhy,
                        onWudu = { act { Engine.wuduGoing(this, date, prayer) } },
                        onPrayed = { s -> act { Engine.checkIn(this, date, prayer, s) } },
                        onPrime = { priming = true },
                        onLater = { finish() },
                    )
                }
            }
        }
    }

    /** পরের ডাক এলে (singleTop) নতুন লেখা দেখাই */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    /** কাজ আলাদা থ্রেডে (ডেটাবেস), তারপর পর্দা বন্ধ */
    private fun act(block: () -> Unit) {
        thread(name = "istiqamah-call") { block() }
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // পর্দা খুললে লক স্ক্রিন সরানোর অনুরোধ — পিন থাকলে সিস্টেম জিজ্ঞেস করবে
        getSystemService(KeyguardManager::class.java)?.let { km ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !km.isDeviceSecure) km.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
private fun CallScreen(
    name: String,
    endMillis: Long?,
    item: ContentItem?,
    pledge: String,
    why: String,
    onWudu: () -> Unit,
    onPrayed: (PrayerStatus) -> Unit,
    onPrime: () -> Unit,
    onLater: () -> Unit,
) {
    var left by remember { mutableStateOf("") }
    LaunchedEffect(endMillis) {
        while (endMillis != null) {
            val ms = endMillis - System.currentTimeMillis()
            if (ms <= 0) { left = "ওয়াক্ত শেষ — এখনই কাযা করে নিন"; break }
            val d = Duration.ofMillis(ms)
            left = "শেষ হতে বাকি ${Bn.duration(d.toMinutes())}"
            delay(15_000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Emerald900).statusBarsPadding().navigationBarsPadding().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("আল্লাহু আকবার", color = GoldLight, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("এখন $name", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            if (left.isNotEmpty()) Text(left, color = Gold, fontSize = 16.sp)
            Spacer(Modifier.height(22.dp))
            Text("আপনি ঘরে — কেউ না দেখলেও আল্লাহ দেখছেন", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
            item?.let {
                Spacer(Modifier.height(14.dp))
                Text(it.shortBn, color = Color.White, fontSize = 19.sp, lineHeight = 29.sp, textAlign = TextAlign.Center)
                Text("— ${it.refBn}", color = GoldLight, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (pledge.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text("🤝 আপনার ওয়াদা", color = Gold, fontSize = 14.sp)
                Text(pledge, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
                if (why.isNotBlank()) Text("কেন: $why", color = GoldLight, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        Button(
            onClick = onWudu, modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald900),
        ) { Text("ওযু করতে যাচ্ছি", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        Row {
            OutlinedButton(onClick = { onPrayed(PrayerStatus.ALONE) }, modifier = Modifier.weight(1f)) { Text("পড়েছি ✓", color = Color.White) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { onPrayed(PrayerStatus.JAMAAH) }, modifier = Modifier.weight(1f)) { Text("জামাতে পড়েছি", color = Color.White) }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrime) { Text("৬০ সেকেন্ডের প্রস্তুতি", color = GoldLight) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLater) { Text("পরে", color = Color(0xFF9FBFB2), fontSize = 13.sp) }
        }
    }
}

/**
 * v0.15 কড়া মাত্রার পুরো পর্দা — বন্ধের কোনো বোতাম নেই, শুধু:
 * এখনই যাচ্ছি · ১০ মিনিট পরে · (পুরুষ) জামাতে পড়েছি · পড়েছি (একা) · কাযা পড়েছি
 */
@Composable
private fun StrongCallScreen(
    title: String,
    endMillis: Long?,
    item: ContentItem?,
    female: Boolean,
    onGo: () -> Unit,
    onLater: () -> Unit,
    onPrayed: (PrayerStatus) -> Unit,
) {
    var left by remember { mutableStateOf("") }
    LaunchedEffect(endMillis) {
        while (endMillis != null) {
            val ms = endMillis - System.currentTimeMillis()
            if (ms <= 0) { left = "ওয়াক্ত শেষ — এখনই কাযা করে নিন"; break }
            left = "ওয়াক্ত শেষ হতে বাকি ${Bn.duration(Duration.ofMillis(ms).toMinutes())}"
            delay(15_000)
        }
    }
    Column(
        Modifier.fillMaxSize().background(Emerald900).statusBarsPadding().navigationBarsPadding().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("আল্লাহু আকবার", color = GoldLight, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (left.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(left, color = Gold, fontSize = 16.sp)
            }
            item?.let {
                Spacer(Modifier.height(20.dp))
                Text(it.shortBn, color = Color.White, fontSize = 19.sp, lineHeight = 29.sp, textAlign = TextAlign.Center)
                Text("— ${it.refBn}", color = GoldLight, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Button(
            onClick = onGo, modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald900),
        ) { Text("এখনই যাচ্ছি", fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onLater, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("১০ মিনিট পরে", color = Color.White, fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
        Row {
            if (!female) {
                OutlinedButton(onClick = { onPrayed(PrayerStatus.JAMAAH) }, modifier = Modifier.weight(1f)) { Text("জামাতে পড়েছি", color = Color.White, fontSize = 13.sp) }
                Spacer(Modifier.width(6.dp))
            }
            OutlinedButton(onClick = { onPrayed(PrayerStatus.ALONE) }, modifier = Modifier.weight(1f)) {
                Text(if (female) "পড়েছি" else "পড়েছি (একা)", color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = { onPrayed(PrayerStatus.QAZA) }, modifier = Modifier.weight(1f)) { Text("কাযা পড়েছি", color = Color.White, fontSize = 13.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Text("একটা বোতাম না চাপা পর্যন্ত এই পর্দা সরবে না", color = Color(0xFF9FBFB2), fontSize = 12.sp)
    }
}
