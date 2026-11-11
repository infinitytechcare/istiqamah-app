package com.istiqamah.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.istiqamah.app.data.ContentRepo

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("আজ", Icons.Outlined.Today),
    Tab("ক্যালেন্ডার", Icons.Outlined.CalendarMonth),
    Tab("সেটিংস", Icons.Outlined.Settings),
)

/** পুরো অ্যাপের মূল: অনবোর্ডিং, নিচের ট্যাব, আর ওপরের পূর্ণ পর্দা (আয়াত / ব্যাটারি গাইড) */
@Composable
fun App(vm: AppVm, openContentId: String?, onContentOpened: () -> Unit) {
    if (!vm.onboarded) {
        OnboardingScreen(vm)
        return
    }
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf<String?>(null) }
    var battery by remember { mutableStateOf(false) }

    // নোটিফিকেশন থেকে এলে সরাসরি আয়াতটা খুলি
    LaunchedEffect(openContentId) {
        if (openContentId != null) {
            content = openContentId
            onContentOpened()
        }
    }

    val item = content?.let { ContentRepo.byId(ctx, it) }
    when {
        item != null -> ContentDetail(item) { content = null }
        battery -> BatteryGuide(vm) { battery = false }
        else -> Scaffold(
            containerColor = Cream,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Emerald800, selectedTextColor = Emerald800, indicatorColor = Emerald50,
                            ),
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
                when (tab) {
                    0 -> TodayScreen(vm, onOpenContent = { content = it }, onOpenBattery = { battery = true })
                    1 -> CalendarScreen(vm)
                    2 -> SettingsScreen(vm, onOpenBattery = { battery = true })
                }
            }
        }
    }
}
