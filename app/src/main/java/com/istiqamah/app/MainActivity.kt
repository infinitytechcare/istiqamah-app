package com.istiqamah.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.istiqamah.app.notify.Notifier
import com.istiqamah.app.ui.App
import com.istiqamah.app.ui.AppVm
import com.istiqamah.app.ui.IstiqamahTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()
    /** নোটিফিকেশন থেকে কোন আয়াত/হাদিস খুলতে হবে */
    private val openContent = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        Notifier.createChannels(this)
        handle(intent)
        setContent {
            IstiqamahTheme {
                App(vm, openContent.value) { openContent.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // অনুমতি বদলে ফিরে এলে বা সময় পেরোলে সব হালনাগাদ
        vm.onResume()
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN_CONTENT)?.let { openContent.value = it }
    }
}
