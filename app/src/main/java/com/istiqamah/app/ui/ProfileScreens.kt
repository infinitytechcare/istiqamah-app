package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Jamaat
import com.istiqamah.app.core.JamaatMode
import com.istiqamah.app.core.JamaatRule
import com.istiqamah.app.core.Pause
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.engine.Engine
import java.time.LocalTime
import kotlin.concurrent.thread

/**
 * v0.5 — "আমি ও জামাত": পুরুষ/মহিলা, মসজিদ ও অফিসের জামাতের সময়, নীরব রিমাইন্ডার, (মহিলা) বিরতি।
 */
@Composable
fun JamaatScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val today = Engine.today(ctx)
    var dialog by remember { mutableStateOf<String?>(null) }
    val changed = { vm.settingsChanged() }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("আমি ও জামাত", "পুরুষ/মহিলা অনুযায়ী আলাদা রিমাইন্ডার", onBack)

        SectionCard {
            PTitle("আমি")
            Gender.entries.filter { it != Gender.UNSET }.forEach { g ->
                Row(Modifier.fillMaxWidth().clickable { p.gender = g; changed() }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = p.gender == g, onClick = { p.gender = g; changed() })
                    Column {
                        Text(g.bn, fontSize = 17.sp, color = Ink)
                        Text(
                            if (g == Gender.MALE) "মসজিদে জামাত — রিমাইন্ডার জামাতের সময় ধরে"
                            else "ঘরে আউয়াল ওয়াক্তে — রিমাইন্ডার ওয়াক্তের শুরুতে, বিরতি মোড",
                            fontSize = 13.sp, color = Muted,
                        )
                    }
                }
            }
            val ref = if (p.gender == Gender.FEMALE) "h_abudawud_570" else "h_bukhari_645"
            ContentRepo.byId(ctx, ref)?.let {
                Text("${it.shortBn} — ${it.refBn}", fontSize = 14.sp, color = Emerald800, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (p.gender == Gender.MALE) {
            SectionCard {
                PTitle("জামাতের সময়")
                PToggle("জামাত ধরে রিমাইন্ডার", "জামাতের ${Bn.num(p.jamaatLead)} মিনিট আগে ডাক, ছুটে গেলে আলাদা ডাক", p.jamaatOn) { p.jamaatOn = it; changed() }
                if (p.jamaatOn) {
                    val ws = Engine.windows(ctx, today)?.associateBy { it.prayer }
                    Prayer.entries.forEach { pr ->
                        val r = p.jamaatRule(pr)
                        val t = ws?.get(pr)?.let { r.at(it) }?.let { " · আজ ${Bn.time(it.toLocalTime())}" } ?: ""
                        PItem("${pr.bn}-এর জামাত (মসজিদ)", r.describe(pr) + t) { dialog = "rule:${pr.name}" }
                    }
                    PItem("রওনা দেওয়ার সময়", "জামাতের ${Bn.num(p.jamaatLead)} মিনিট আগে ডাকবে") { dialog = "lead" }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (p.jamaatOn) SectionCard {
                PTitle("অফিসের জামাত")
                Text("অফিসের দিনে অফিসের সময়ের ভেতরে পড়লে এই সময় ধরা হবে, নইলে মসজিদের সময়।", fontSize = 14.sp, color = Muted)
                PItem("যোহর (অফিস)", p.officeDhuhr?.let { Bn.time(it) } ?: "নেই") { dialog = "officeDhuhr" }
                PItem("আসর (অফিস)", p.officeAsr?.let { Bn.time(it) } ?: "নেই") { dialog = "officeAsr" }
                Engine.windows(ctx, today)?.firstOrNull { it.prayer == Prayer.DHUHR }?.let { w ->
                    Jamaat.at(w, p.jamaatSettings, p.homeMode.schedule)?.let {
                        Text("আজ যোহর: ${if (it.office) "অফিসে" else "মসজিদে"} ${Bn.time(it.at.toLocalTime())}", fontSize = 14.sp, color = Emerald700, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (p.gender == Gender.FEMALE) {
            SectionCard {
                PTitle("বিরতি")
                Text("হায়েজ/নিফাসের দিনগুলোতে নামাজ মাফ। বিরতি চালু থাকলে নামাজের রিমাইন্ডার বন্ধ থাকবে, দিনগুলো \"বিরতি\" হিসেবে লেখা হবে, স্ট্রিক ভাঙবে না। " +
                    "নোটিফিকেশনে কখনো কারণ লেখা হয় না।", fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
                val from = p.pausedFrom
                if (from == null) {
                    OutlinedButton(onClick = { p.pausedFrom = today; thread { Engine.startPause(ctx, today) }; changed() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("আজ থেকে বিরতি শুরু", color = Emerald800)
                    }
                } else {
                    Text("বিরতির ${Bn.num(Pause.dayNumber(today, from).toInt())}তম দিন (${Bn.date(from)} থেকে)", fontSize = 15.sp, color = Emerald700, modifier = Modifier.padding(top = 8.dp))
                    OutlinedButton(onClick = { p.pausedFrom = null; thread { Engine.endPause(ctx) }; changed() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("বিরতি শেষ — গোসল করে নামাজ শুরু", color = Emerald800)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        SectionCard {
            PTitle("নীরব রিমাইন্ডার")
            PToggle(
                "পড়া না হওয়া পর্যন্ত প্রতি ১০ মিনিটে",
                "শব্দ ও কাঁপুনি ছাড়া, ওয়াক্ত শেষ হওয়া পর্যন্ত; চেক-ইন করলেই বন্ধ",
                p.silentRepeat,
            ) { p.silentRepeat = it; changed() }
        }
        Spacer(Modifier.height(24.dp))
    }

    val close = { dialog = null }
    val d = dialog
    when {
        d == null -> {}
        d.startsWith("rule:") -> RuleDialog(Prayer.valueOf(d.removePrefix("rule:")), p.jamaatRule(Prayer.valueOf(d.removePrefix("rule:"))), close) { pr, r ->
            p.setJamaatRule(pr, r); changed()
        }
        d == "lead" -> PChoice("জামাতের কত আগে ডাকবে?", listOf(5, 10, 15, 20, 25, 30).map { "${Bn.num(it)} মিনিট আগে" },
            listOf(5, 10, 15, 20, 25, 30).indexOf(p.jamaatLead), close) { p.jamaatLead = listOf(5, 10, 15, 20, 25, 30)[it]; changed() }
        d == "officeDhuhr" || d == "officeAsr" -> {
            val opts = (if (d == "officeDhuhr") (12 * 4..15 * 4) else (15 * 4..18 * 4)).map { LocalTime.of(it / 4, (it % 4) * 15) }
            val cur = if (d == "officeDhuhr") p.officeDhuhr else p.officeAsr
            PChoice("অফিসের জামাত", listOf("নেই") + opts.map { Bn.time(it) }, cur?.let { opts.indexOf(it) + 1 } ?: 0, close) { i ->
                val t = if (i == 0) null else opts[i - 1]
                if (d == "officeDhuhr") p.officeDhuhr = t else p.officeAsr = t
                changed()
            }
        }
    }
}

/** একটা ওয়াক্তের মসজিদের জামাতের নিয়ম বাছাই */
@Composable
private fun RuleDialog(pr: Prayer, cur: JamaatRule, onDismiss: () -> Unit, onSave: (Prayer, JamaatRule) -> Unit) {
    // ফজরে "সূর্যোদয়ের আগে", বাকিতে "আযানের পরে" স্বাভাবিক
    val modes = listOf(JamaatMode.FIXED, if (pr == Prayer.FAJR) JamaatMode.BEFORE_END else JamaatMode.AFTER_START, JamaatMode.OFF)
    var mode by remember { mutableStateOf(if (cur.mode in modes) cur.mode else modes[1]) }
    var minutes by remember { mutableIntStateOf(if (cur.minutes > 0) cur.minutes else 20) }
    var fixed by remember { mutableStateOf(cur.fixed ?: LocalTime.of(13, 30)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${pr.bn}-এর জামাত") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                modes.forEach { m ->
                    Row(Modifier.fillMaxWidth().clickable { mode = m }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(if (m == JamaatMode.BEFORE_END) "সূর্যোদয়ের আগে" else m.bn)
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (mode) {
                    JamaatMode.FIXED -> {
                        Text("সময়: ${Bn.time(fixed)}", fontSize = 16.sp, color = Ink)
                        Row {
                            TextButton(onClick = { fixed = fixed.minusMinutes(15) }) { Text("−১৫") }
                            TextButton(onClick = { fixed = fixed.minusMinutes(5) }) { Text("−৫") }
                            TextButton(onClick = { fixed = fixed.plusMinutes(5) }) { Text("+৫") }
                            TextButton(onClick = { fixed = fixed.plusMinutes(15) }) { Text("+১৫") }
                        }
                        Text("শীত-গ্রীষ্মে মসজিদ সময় বদলালে এখানেও বদলে নিন।", fontSize = 12.sp, color = Muted)
                    }
                    JamaatMode.AFTER_START, JamaatMode.BEFORE_END -> {
                        Text(
                            if (mode == JamaatMode.BEFORE_END) "সূর্যোদয়ের ${Bn.num(minutes)} মিনিট আগে" else "আযানের ${Bn.num(minutes)} মিনিট পরে",
                            fontSize = 16.sp, color = Ink,
                        )
                        Row {
                            TextButton(onClick = { minutes = (minutes - 5).coerceAtLeast(0) }) { Text("−৫") }
                            TextButton(onClick = { minutes = (minutes - 1).coerceAtLeast(0) }) { Text("−১") }
                            TextButton(onClick = { minutes = (minutes + 1).coerceAtMost(120) }) { Text("+১") }
                            TextButton(onClick = { minutes = (minutes + 5).coerceAtMost(120) }) { Text("+৫") }
                        }
                    }
                    JamaatMode.OFF -> Text("এই ওয়াক্তে ওয়াক্ত শুরুতেই রিমাইন্ডার আসবে।", fontSize = 14.sp, color = Muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(pr, when (mode) {
                    JamaatMode.FIXED -> JamaatRule(mode, fixed = fixed)
                    JamaatMode.OFF -> JamaatRule(mode)
                    else -> JamaatRule(mode, minutes)
                })
                onDismiss()
            }) { Text("রাখুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

@Composable
private fun PTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun PItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontSize = 17.sp, color = Ink)
        Text(subtitle, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun PToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Ink)
            Text(subtitle, fontSize = 14.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
    }
}

@Composable
private fun PChoice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } },
    )
}

/** হোম স্ক্রিনের ছোট কার্ড: লিঙ্গ বাছাই বাকি থাকলে, বা (মহিলা) বিরতি চলছে */
@Composable
fun ProfileTodayCard(vm: AppVm, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    when {
        p.gender == Gender.UNSET -> SectionCard {
            Text("আপনি পুরুষ না মহিলা?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("পুরুষদের জামাতের সময় ধরে, মহিলাদের আউয়াল ওয়াক্তে ঘরে — রিমাইন্ডার আলাদা হবে।", fontSize = 14.sp, color = Muted)
            Row {
                TextButton(onClick = { p.gender = Gender.MALE; vm.settingsChanged(); open("jamaat") }) { Text("পুরুষ", color = Emerald700) }
                TextButton(onClick = { p.gender = Gender.FEMALE; vm.settingsChanged() }) { Text("মহিলা", color = Emerald700) }
            }
        }
        p.gender == Gender.FEMALE && p.pausedFrom != null -> SectionCard {
            val today = Engine.today(ctx)
            Text("বিরতি চলছে · ${Bn.num(Pause.dayNumber(today, p.pausedFrom!!).toInt())}তম দিন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("নামাজের রিমাইন্ডার বন্ধ। যিকির ও দোয়া চালু থাকুক।", fontSize = 14.sp, color = Muted)
            TextButton(onClick = { p.pausedFrom = null; thread { Engine.endPause(ctx) }; vm.settingsChanged() }) { Text("বিরতি শেষ — নামাজ শুরু ›", color = Emerald700) }
        }
        else -> {}
    }
}
