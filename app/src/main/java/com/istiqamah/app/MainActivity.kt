package com.istiqamah.app

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.istiqamah.app.notify.Notifier
import com.istiqamah.app.ui.App
import com.istiqamah.app.ui.AppVm
import com.istiqamah.app.ui.IstiqamahTheme

/** FragmentActivity — আঙুলের ছাপ/পিনের BiometricPrompt-এর জন্য দরকার */
class MainActivity : FragmentActivity() {
    private val vm: AppVm by viewModels()
    /** নোটিফিকেশন থেকে কোন আয়াত/হাদিস খুলতে হবে */
    private val openContent = mutableStateOf<String?>(null)
    /** নোটিফিকেশন থেকে কোন স্ক্রিন: "azkar:MORNING", "muhasaba", "weekly" */
    private val openRoute = mutableStateOf<String?>(null)

    /** Android 8.x: সিস্টেমের লক-স্ক্রিন যাচাই (BiometricPrompt সেখানে AppCompat থিম চায়) */
    private var pendingUnlock: (() -> Unit)? = null
    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) {
            vm.markPrivateUnlocked()
            pendingUnlock?.invoke()
        }
        pendingUnlock = null
    }

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
                App(
                    vm = vm,
                    openContentId = openContent.value,
                    onContentOpened = { openContent.value = null },
                    openRoute = openRoute.value,
                    onRouteOpened = { openRoute.value = null },
                    requestUnlock = ::unlockPrivate,
                )
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

    override fun onStop() {
        super.onStop()
        // অ্যাপ থেকে বের হলেই গোপন অংশ আবার তালাবদ্ধ
        vm.lockPrivate()
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN_CONTENT)?.let { openContent.value = it }
        intent?.getStringExtra(Notifier.EXTRA_ROUTE)?.let { openRoute.value = it }
    }

    /**
     * গোপন অংশ (গুনাহ ট্র্যাকার, মুহাসাবার ইতিহাস) খোলার আগে ফোনের লক।
     * ফোনে কোনো লক সেট না থাকলে সরাসরি খুলে যায়, সাথে পরামর্শ।
     */
    private fun unlockPrivate(onOk: () -> Unit) {
        if (!vm.prefs.lockPrivate || vm.privateUnlocked) return onOk()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val km = getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val i = if (km?.isDeviceSecure == true) km.createConfirmDeviceCredentialIntent("নিজের হিসাব", "এই অংশ শুধু আপনার জন্য") else null
            if (i == null) {
                Toast.makeText(this, "ফোনে স্ক্রিন লক নেই — গোপনীয়তার জন্য একটা পিন/প্যাটার্ন সেট করুন", Toast.LENGTH_LONG).show()
                vm.markPrivateUnlocked()
                return onOk()
            }
            pendingUnlock = onOk
            confirmCredential.launch(i)
            return
        }
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "ফোনে স্ক্রিন লক নেই — গোপনীয়তার জন্য একটা পিন/প্যাটার্ন সেট করুন", Toast.LENGTH_LONG).show()
            vm.markPrivateUnlocked()
            return onOk()
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    vm.markPrivateUnlocked()
                    onOk()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("নিজের হিসাব")
                .setSubtitle("এই অংশ শুধু আপনার জন্য")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}
