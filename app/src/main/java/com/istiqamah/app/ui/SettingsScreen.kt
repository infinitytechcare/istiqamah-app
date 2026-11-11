package com.istiqamah.app.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.BuildConfig
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.engine.Engine
import java.time.LocalTime

@Composable
fun SettingsScreen(vm: AppVm, onOpenBattery: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    // settingsVersion পড়লে সেটিং বদলের পর এই স্ক্রিন আবার আঁকা হয়
    key(vm.settingsVersion) {
        var dialog by remember { mutableStateOf<String?>(null) }
        Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("সেটিংস", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Title("নামাজের সময়")
                Item("লোকেশন", p.placeName) { dialog = "location" }
                Item("হিসাব পদ্ধতি", p.method.bn) { dialog = "method" }
                Item("আসরের মাযহাব", p.madhab.bn) { dialog = "madhab" }
                Item("মসজিদের সময়ের সাথে মেলান", "প্রতি ওয়াক্তে মিনিট যোগ/বিয়োগ") { dialog = "adjust" }
                Item("হিজরি তারিখ সংশোধন", "${Bn.num(p.hijriAdjust)} দিন") { dialog = "hijri" }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Title("রিমাইন্ডার")
                Item("তীব্রতা", "${p.intensity.bn} — ${p.intensity.desc}") { dialog = "intensity" }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("দৈনিক আয়াত", fontSize = 17.sp, color = Ink)
                        Text(p.dailyAyahAt?.let { Bn.time(it) } ?: "বন্ধ", fontSize = 14.sp, color = Muted)
                    }
                    Switch(
                        checked = p.dailyAyahAt != null,
                        onCheckedChange = { on -> p.dailyAyahAt = if (on) LocalTime.of(9, 0) else null; vm.settingsChanged() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald700),
                    )
                }
                if (p.dailyAyahAt != null) Item("দৈনিক আয়াতের সময়", Bn.time(p.dailyAyahAt!!)) { dialog = "ayahTime" }
                Item("ব্যাটারি গাইড", "রিমাইন্ডার না এলে এখানে দেখুন", onClick = onOpenBattery)
                Item("টেস্ট রিমাইন্ডার", "১ মিনিট পরে একটা টেস্ট নোটিফিকেশন — ফোন লক করে দেখুন") {
                    Engine.scheduleTestAlarm(ctx)
                    Toast.makeText(ctx, "১ মিনিট পরে টেস্ট রিমাইন্ডার আসবে", Toast.LENGTH_LONG).show()
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Title("অ্যাপ সম্পর্কে")
                Text(
                    "ইস্তিকামাহ ${BuildConfig.VERSION_NAME}\n\n" +
                        "• আপনার সব তথ্য শুধু এই ফোনে। অ্যাপের ইন্টারনেট অনুমতি নেই, কোনো বিজ্ঞাপন বা ট্র্যাকিং নেই।\n" +
                        "• কুরআনের আরবি: Noble Qur'an Encyclopedia (quranenc.com), বাংলা অনুবাদ: মুহিউদ্দীন খান।\n" +
                        "• হাদিসের বাংলা ভাবার্থ ও সূত্র রিলিজের আগে আলেমের রিভিউ প্রয়োজন।\n" +
                        "• মাসআলা জানতে স্থানীয় আলেমকে জিজ্ঞেস করুন — এই অ্যাপ ফতোয়ার উৎস নয়।",
                    fontSize = 14.sp, color = Muted, lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        val close = { dialog = null }
        when (dialog) {
            "location" -> AlertDialog(
                onDismissRequest = close, confirmButton = { TextButton(onClick = close) { Text("বাতিল") } },
                containerColor = Cream,
                text = {
                    Column(Modifier.heightIn(max = 520.dp)) {
                        LocationStep(p.placeName) { name, loc: Location ->
                            p.placeName = name; p.location = loc; close(); vm.settingsChanged()
                        }
                    }
                },
            )
            "method" -> Choice("হিসাব পদ্ধতি", CalcMethod.entries.map { it.bn }, p.method.ordinal, close) {
                p.method = CalcMethod.entries[it]; vm.settingsChanged()
            }
            "madhab" -> Choice("আসরের মাযহাব", AsrMadhab.entries.map { it.bn }, p.madhab.ordinal, close) {
                p.madhab = AsrMadhab.entries[it]; vm.settingsChanged()
            }
            "intensity" -> Choice("রিমাইন্ডারের তীব্রতা", Intensity.entries.map { "${it.bn} — ${it.desc}" }, p.intensity.ordinal, close) {
                p.intensity = Intensity.entries[it]; vm.settingsChanged()
            }
            "hijri" -> Choice("হিজরি তারিখ সংশোধন", (-2..2).map { "${Bn.num(it)} দিন — ${Bn.hijri(java.time.LocalDate.now(), it)}" }, p.hijriAdjust + 2, close) {
                p.hijriAdjust = it - 2; vm.settingsChanged()
            }
            "ayahTime" -> Choice("দৈনিক আয়াতের সময়", (5..22).map { Bn.time(LocalTime.of(it, 0)) }, (p.dailyAyahAt?.hour ?: 9) - 5, close) {
                p.dailyAyahAt = LocalTime.of(it + 5, 0); vm.settingsChanged()
            }
            "adjust" -> AdjustDialog(vm, close)
        }
    }
}

@Composable
private fun Title(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Item(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontSize = 17.sp, color = Ink)
        Text(subtitle, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } },
        title = { Text(title) },
        containerColor = Cream,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(label, fontSize = 16.sp)
                    }
                }
            }
        },
    )
}

@Composable
private fun AdjustDialog(vm: AppVm, onDismiss: () -> Unit) {
    val p = vm.prefs
    var tick by remember { mutableStateOf(0) }
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { vm.settingsChanged(); onDismiss() },
        confirmButton = { TextButton(onClick = { vm.settingsChanged(); onDismiss() }) { Text("সংরক্ষণ") } },
        title = { Text("মিনিট যোগ/বিয়োগ") },
        containerColor = Cream,
        text = {
            key(tick) {
                val w = Engine.windows(ctx, Engine.today(ctx))
                Column {
                    Text("আপনার মসজিদের আজানের সময়ের সাথে মিলিয়ে নিন।", fontSize = 14.sp, color = Muted)
                    Prayer.entries.forEach { pr ->
                        val start = w?.firstOrNull { it.prayer == pr }?.start?.toLocalTime()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pr.bn, fontSize = 16.sp)
                                start?.let { Text(Bn.time(it), fontSize = 13.sp, color = Muted) }
                            }
                            TextButton(onClick = { p.setAdjust(pr, p.adjust(pr) - 1); tick++ }) { Text("−", fontSize = 20.sp) }
                            Text(Bn.num(p.adjust(pr)), Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { p.setAdjust(pr, p.adjust(pr) + 1); tick++ }) { Text("+", fontSize = 20.sp) }
                        }
                    }
                }
            }
        },
    )
}

// ---------- ব্যাটারি গাইড ----------

private data class BrandGuide(val name: String, val steps: List<String>, val autostart: List<ComponentName>)

private fun brandGuide(): BrandGuide {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        "xiaomi" in m || "redmi" in m || "poco" in m -> BrandGuide(
            "Xiaomi / Redmi / POCO",
            listOf(
                "নিচের \"অটোস্টার্ট\" বাটন চাপুন → ইস্তিকামাহ চালু করুন।",
                "Settings → Apps → ইস্তিকামাহ → Battery saver → \"No restrictions\"।",
                "সাম্প্রতিক অ্যাপের তালিকায় ইস্তিকামাহ-কে নিচে টেনে \"লক\" করে রাখুন।",
            ),
            listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            ),
        )
        "realme" in m || "oppo" in m || "oneplus" in m -> BrandGuide(
            "Realme / Oppo / OnePlus",
            listOf(
                "Settings → Battery → ইস্তিকামাহ → \"Allow background activity\" চালু।",
                "Settings → Apps → ইস্তিকামাহ → \"Auto launch\" চালু।",
                "সাম্প্রতিক অ্যাপের তালিকায় অ্যাপটা লক করে রাখুন।",
            ),
            listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ),
        )
        "vivo" in m || "iqoo" in m -> BrandGuide(
            "Vivo / iQOO",
            listOf(
                "i Manager → App manager → Autostart → ইস্তিকামাহ চালু।",
                "Settings → Battery → Background power consumption → ইস্তিকামাহ \"Allow\"।",
            ),
            listOf(
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ),
        )
        "samsung" in m -> BrandGuide(
            "Samsung",
            listOf(
                "Settings → Battery → Background usage limits → \"Never sleeping apps\"-এ ইস্তিকামাহ যোগ করুন।",
                "Settings → Apps → ইস্তিকামাহ → Battery → \"Unrestricted\"।",
            ),
            emptyList(),
        )
        else -> BrandGuide(
            Build.MANUFACTURER,
            listOf("Settings → Apps → ইস্তিকামাহ → Battery → \"Unrestricted\" / \"No restrictions\" বেছে নিন।"),
            emptyList(),
        )
    }
}

private fun tryStart(ctx: Context, intents: List<Intent>): Boolean {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (e: ActivityNotFoundException) { } catch (e: SecurityException) { }
    }
    return false
}

@Composable
fun BatteryGuide(vm: AppVm, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)
    val g = remember { brandGuide() }
    val pm = ctx.getSystemService(PowerManager::class.java)
    val ignoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "ফিরে যান", tint = Emerald800) }
            Text("ব্যাটারি গাইড", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        }
        Text(
            "অনেক ফোন ব্যাটারি বাঁচাতে ব্যাকগ্রাউন্ডের অ্যাপ বন্ধ করে দেয় — তখন নামাজের রিমাইন্ডার আসে না। নিচের ধাপগুলো একবার করে নিন।",
            fontSize = 15.sp, color = Ink, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 8.dp),
        )
        SectionCard {
            Text("১. ব্যাটারি সীমা তুলে দিন", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (ignoring) Text("✓ হয়ে গেছে", color = Emerald700)
            else Button(
                onClick = {
                    tryStart(ctx, listOf(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")),
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
            ) { Text("সীমা তুলে দিন") }
        }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Text("২. আপনার ফোন: ${g.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            g.steps.forEachIndexed { i, s -> Text("${Bn.num(i + 1)}) $s", fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            if (g.autostart.isNotEmpty()) {
                OutlinedButton(onClick = {
                    if (!tryStart(ctx, g.autostart.map { Intent().setComponent(it) })) {
                        Toast.makeText(ctx, "এই ফোনে সরাসরি খোলা গেল না — উপরের ধাপ অনুযায়ী সেটিংসে যান", Toast.LENGTH_LONG).show()
                    }
                }) { Text("অটোস্টার্ট সেটিং খুলুন") }
            }
            OutlinedButton(onClick = {
                tryStart(ctx, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))))
            }) { Text("অ্যাপের সেটিংস খুলুন") }
        }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Text("৩. পরীক্ষা করুন", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("বাটন চেপে ফোন লক করে রেখে দিন। ১ মিনিট পরে রিমাইন্ডার এলে সব ঠিক আছে।", fontSize = 15.sp, lineHeight = 22.sp)
            Button(
                onClick = {
                    Engine.scheduleTestAlarm(ctx)
                    vm.prefs.batteryGuideSeen = true
                    Toast.makeText(ctx, "১ মিনিট পরে টেস্ট রিমাইন্ডার আসবে", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald900),
            ) { Text("টেস্ট রিমাইন্ডার পাঠান") }
        }
        HorizontalDivider(Modifier.padding(vertical = 16.dp), color = Divider)
        Text("আরও তথ্য: dontkillmyapp.com", fontSize = 13.sp, color = Muted)
    }
}
