package com.istiqamah.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.data.ContentRepo

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("আজ", Icons.Outlined.Today),
    Tab("কুরআন", Icons.AutoMirrored.Outlined.MenuBook),
    Tab("আমল", Icons.Outlined.AutoAwesome),
    Tab("ক্যালেন্ডার", Icons.Outlined.CalendarMonth),
    Tab("সেটিংস", Icons.Outlined.Settings),
)

/**
 * পুরো অ্যাপের মূল: অনবোর্ডিং, নিচের ট্যাব, আর ওপরের পূর্ণ পর্দা।
 * পূর্ণ পর্দার রুট: "azkar:MORNING" · "muhasaba" · "sins" · "weekly" · "battery" · "home" · "keys" · "prime" · "jamaat" · "reader[:পৃষ্ঠা]" · "fasting" · "zakat" · "hajj" · "iman" · "lesson:KEY" · "akhirah" · "ihsan" · "akhlaq[:KEY]" · "family" · "ilm" · "deen" · "companions" · "questions" · "masail:KEY"
 */
@Composable
fun App(
    vm: AppVm,
    openContentId: String?,
    onContentOpened: () -> Unit,
    openRoute: String?,
    onRouteOpened: () -> Unit,
    requestUnlock: (() -> Unit) -> Unit,
) {
    if (!vm.onboarded) {
        OnboardingScreen(vm)
        return
    }
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var content by remember { mutableStateOf<String?>(null) }
    var route by remember { mutableStateOf<String?>(null) }

    // নোটিফিকেশন থেকে এলে সরাসরি আয়াত বা স্ক্রিন খুলি
    LaunchedEffect(openContentId) {
        if (openContentId != null) {
            content = openContentId
            onContentOpened()
        }
    }
    LaunchedEffect(openRoute) {
        if (openRoute != null) {
            content = null
            route = openRoute
            onRouteOpened()
        }
    }
    // গোপন অংশ আবার তালাবদ্ধ হলে (অ্যাপ থেকে বের হলে) পর্দা বন্ধ
    LaunchedEffect(vm.privateUnlocked) {
        if (!vm.privateUnlocked && route == "sins") route = null
    }

    val open: (String) -> Unit = { r -> route = r }
    val back: () -> Unit = { route = null }
    val item = content?.let { ContentRepo.byId(ctx, it) }
    val r = route
    when {
        item != null -> ContentDetail(item) { content = null }
        r == "battery" -> BatteryGuide(vm, back)
        r != null && r.startsWith("azkar:") -> {
            val set = runCatching { AzkarSet.valueOf(r.removePrefix("azkar:")) }.getOrDefault(AzkarSet.MORNING)
            AzkarScreen(vm, set, back)
        }
        r == "muhasaba" -> MuhasabaScreen(vm, requestUnlock, back)
        r == "sins" -> SinsScreen(vm, back)
        r == "weekly" -> WeeklyScreen(vm, back)
        r == "home" -> HomeAloneScreen(vm, open, back)
        r == "keys" -> EaseKeysScreen(onOpenContent = { content = it }, onBack = back)
        r == "prime" -> PrimeScreen(vm, back)
        r == "jamaat" -> JamaatScreen(vm, back)
        r == "fasting" -> FastingScreen(vm, back)
        r == "zakat" -> ZakatScreen(vm, open, back)
        r == "hajj" -> HajjScreen(vm, back)
        r == "iman" -> ImanHomeScreen(vm, open, back)
        r != null && r.startsWith("lesson:") -> ImanLessonScreen(vm, r.removePrefix("lesson:"), open) { route = "iman" }
        r == "akhirah" -> AkhirahScreen(vm, open, back)
        r == "ihsan" -> IhsanScreen(vm, open, back)
        r == "akhlaq" -> AkhlaqScreen(vm, open, back)
        r == "family" -> FamilyScreen(vm, open, back)
        r == "ilm" -> IlmScreen(vm, open, back)
        r == "body" -> BodyScreen(vm, back)
        r == "deen" -> DeenHubScreen(vm, open, back)
        r == "companions" -> CompanionsScreen(vm, open, back)
        r == "questions" -> QuestionsScreen(vm, back)
        r == "sync" -> MacSyncScreen(vm, back)
        r != null && r.startsWith("masail:") -> MasailLessonScreen(vm, r.removePrefix("masail:"), open) { route = "deen" }
        r != null && r.startsWith("akhlaq:") -> AkhlaqAreaScreen(vm, r.removePrefix("akhlaq:"), open) { route = "akhlaq" }
        r != null && (r == "reader" || r.startsWith("reader:")) -> QuranReaderScreen(vm, r.removePrefix("reader").removePrefix(":").toIntOrNull(), back)
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
                    0 -> TodayScreen(vm, onOpenContent = { content = it }, open = open)
                    1 -> QuranHomeScreen(vm, open)
                    2 -> AmalScreen(vm, open, requestUnlock)
                    3 -> CalendarScreen(vm)
                    4 -> SettingsScreen(vm, onOpenBattery = { route = "battery" }, requestUnlock = requestUnlock, onOpenProfile = { route = "jamaat" }, onOpenSync = { route = "sync" })
                }
            }
        }
    }
}
