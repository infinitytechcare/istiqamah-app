package com.istiqamah.mac

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.SyncPair
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// =====================================================================
// জানালাগুলো: আজকের নামাজ, সেটিংস, কোণের ডাক, পুরো পর্দার শেষ সতর্কতা
// =====================================================================

/** কোন অবস্থাগুলো বোতাম হিসেবে দেখাব — বিরতি (মহিলাদের) ম্যাকে দেখাই না, গোপন থাকে */
fun statusChoices(g: Gender): List<PrayerStatus> =
    if (g == Gender.FEMALE) listOf(PrayerStatus.ALONE, PrayerStatus.QAZA, PrayerStatus.MISSED)
    else listOf(PrayerStatus.JAMAAH, PrayerStatus.ALONE, PrayerStatus.QAZA, PrayerStatus.MISSED)

fun statusLabel(s: PrayerStatus, g: Gender): String = when {
    s == PrayerStatus.ALONE && g == Gender.FEMALE -> "আউয়াল ওয়াক্তে"
    s == PrayerStatus.EXCUSED -> "চিহ্নিত" // ফোনের বিরতি — ম্যাকের পর্দায় কারণ দেখাই না
    else -> s.bn
}

@Composable
fun Card(modifier: Modifier = Modifier, bg: Color = Color.White, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = bg,
        border = BorderStroke(1.dp, Line),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun ContentBlock(item: ContentItem, dark: Boolean = false, compact: Boolean = false) {
    val fg = if (dark) Color.White else Ink
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.arabic.isNotBlank() && item.arabic.length < (if (compact) 110 else 400)) {
            Text(
                item.arabic, fontFamily = Quranic, fontSize = 22.sp, lineHeight = 42.sp, color = if (dark) GoldLight else Emerald800,
                textAlign = TextAlign.Right, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            if (compact) item.shortBn.ifBlank { item.bn } else item.bn.ifBlank { item.shortBn },
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge, color = fg,
        )
        Text(
            "— ${item.refBn}" + (item.grading?.let { " ($it)" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = if (dark) GoldLight else Muted,
        )
    }
}

// ---------------------------------------------------------------------
// আজকের নামাজ
// ---------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(app: MacApp) {
    val s = app.settings
    val now = app.now
    val today = now.toLocalDate()
    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Emerald800, Emerald900))).padding(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Bn.date(today), color = GoldLight, style = MaterialTheme.typography.titleSmall)
                Text(app.summary.ifBlank { "আজকের নামাজ" }, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("📍 ${s.city}", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }

        val ws = app.windows[today].orEmpty()
        for (w in ws) {
            val p = w.prayer
            val started = !now.isBefore(w.start)
            val st = app.status(today, p)
            val cur = w.contains(now)
            val j = app.jamaat[today to p]
            Card(bg = if (cur && st == null) Color(0xFFFFF8E5) else Color.White) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.bn + if (cur) "  · চলছে" else "", style = MaterialTheme.typography.titleMedium, color = Emerald900)
                            Text(
                                "${Bn.time(w.start.toLocalTime())} – ${Bn.time(w.end.toLocalTime())}" +
                                    (j?.let { "   ·   জামাত ${Bn.time(it.at.toLocalTime())}" + if (it.office) " (অফিস)" else "" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Muted,
                            )
                        }
                        if (st != null) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(st)))
                        }
                    }
                    if (started) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in statusChoices(s.gender)) {
                                val on = st == c
                                Box(
                                    Modifier.clip(RoundedCornerShape(50))
                                        .background(if (on) statusColor(c) else Color.Transparent)
                                        .border(1.dp, if (on) statusColor(c) else Line, RoundedCornerShape(50))
                                        .clickable { app.mark(today, p, if (on) null else c) }
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text(statusLabel(c, s.gender), color = if (on) Color.White else Ink, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    } else {
                        Text("ওয়াক্ত এখনো শুরু হয়নি", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
        }

        app.daily?.let { item ->
            Card(bg = Emerald50) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("আজকের বাণী", style = MaterialTheme.typography.titleSmall, color = Emerald600)
                    ContentBlock(item)
                }
            }
        }

        Text(
            syncLine(app) + " গুনাহের ট্র্যাকার, যাকাত, দেনা ও ওসিয়ত শুধু ফোনেই থাকে — ম্যাকে কখনো আসে না।",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { app.showSettings = true }) { Text("সেটিংস") }
            OutlinedButton(onClick = { app.showPair = true }) { Text("ফোনের সাথে সিঙ্ক") }
        }
    }
}

// ---------------------------------------------------------------------
// সেটিংস
// ---------------------------------------------------------------------

private val HM = DateTimeFormatter.ofPattern("H:mm")
private fun parseTime(t: String): LocalTime? = runCatching { LocalTime.parse(t.trim(), HM) }.getOrNull()

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Emerald900)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, sub: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> RadioList(options: List<T>, selected: T, label: (T) -> String, sub: (T) -> String? = { null }, onPick: (T) -> Unit) {
    Column {
        for (o in options) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(o) }.padding(vertical = 2.dp),
            ) {
                RadioButton(selected = o == selected, onClick = { onPick(o) })
                Column {
                    Text(label(o), style = MaterialTheme.typography.bodyMedium)
                    sub(o)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, value: LocalTime?, allowEmpty: Boolean = false, onChange: (LocalTime?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.format(HM) ?: "") }
    val bad = text.isNotBlank() && parseTime(text) == null
    OutlinedTextField(
        value = text, singleLine = true, isError = bad,
        label = { Text(label) }, placeholder = { Text("যেমন 13:15") },
        onValueChange = {
            text = it
            val t = parseTime(it)
            if (t != null) onChange(t) else if (allowEmpty && it.isBlank()) onChange(null)
        },
        modifier = Modifier.width(160.dp),
    )
}

private val WEEK = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: MacApp) {
    val s = app.settings
    val fromPhone = app.pair.paired
    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (fromPhone) {
            Section("ফোন থেকে আসছে") {
                Text(
                    "জেলা, হিসাবের পদ্ধতি, জামাত, অফিসের সময় আর ডাকের মাত্রা ${app.pair.phoneName ?: "ফোন"}-এর সেটিংস থেকে আসে। বদলাতে চাইলে ফোনে বদলান — পরের সিঙ্কে এখানে চলে আসবে।",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "📍 ${s.city} · ${s.method.bn} · আসর ${s.madhab.bn}" + if (s.gender != Gender.UNSET) " · ${s.gender.bn}" else "",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
                OutlinedButton(onClick = { app.showPair = true }) { Text("ফোনের সাথে সিঙ্ক…") }
            }
        }
        if (!fromPhone) Section("আপনার জেলা") {
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }) { Text("📍 ${s.city}  ▾") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.height(420.dp)) {
                    for (c in MacEngine.CITIES) {
                        DropdownMenuItem(text = { Text(c.bn) }, onClick = {
                            open = false
                            app.update { it.copy(city = c.bn, lat = c.lat, lng = c.lng) }
                        })
                    }
                }
            }
            Text(
                "অক্ষাংশ ${"%.4f".format(s.lat)}, দ্রাঘিমাংশ ${"%.4f".format(s.lng)} — জেলা সদরের হিসাবে; কয়েক মিনিট এদিক-ওদিক হলে নিচে ঠিক করে নিন।",
                style = MaterialTheme.typography.bodySmall, color = Muted,
            )
            Text("মিনিট ঠিক করা (মসজিদের সাথে মেলাতে)", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Prayer.entries.forEachIndexed { i, p ->
                    val v = s.adjust.getOrElse(i) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.bn, style = MaterialTheme.typography.bodySmall, modifier = Modifier.widthIn(min = 44.dp))
                        TextButton(onClick = { app.update { it.copy(adjust = it.adjust.toMutableList().also { a -> a[i] = (v - 1).coerceAtLeast(-30) }) } }) { Text("−") }
                        Text((if (v > 0) "+" else "") + Bn.num(v), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { app.update { it.copy(adjust = it.adjust.toMutableList().also { a -> a[i] = (v + 1).coerceAtMost(30) }) } }) { Text("+") }
                    }
                }
            }
        }

        if (!fromPhone) Section("হিসাবের পদ্ধতি") {
            RadioList(CalcMethod.entries, s.method, { it.bn }) { m -> app.update { it.copy(method = m) } }
            HorizontalDivider(color = Line)
            Text("আসরের সময়", style = MaterialTheme.typography.bodyMedium)
            RadioList(AsrMadhab.entries, s.madhab, { it.bn }) { m -> app.update { it.copy(madhab = m) } }
        }

        if (!fromPhone) Section("আপনি") {
            RadioList(listOf(Gender.MALE, Gender.FEMALE), s.gender, { it.bn }, {
                if (it == Gender.MALE) "জামাতের সময় ধরে ডাকব" else "আউয়াল ওয়াক্তে ঘরে পড়ার ডাক"
            }) { g -> app.update { it.copy(gender = g, pausedFrom = if (g == Gender.FEMALE) it.pausedFrom else null) } }
            if (s.gender == Gender.FEMALE) {
                SwitchRow(
                    "রিমাইন্ডার বিরতি", "চালু থাকলে নামাজের ডাক বন্ধ থাকবে। কোথাও কারণ লেখা হয় না।",
                    s.pausedFrom != null,
                ) { on -> app.update { it.copy(pausedFrom = if (on) LocalDate.now() else null) } }
            }
        }

        if (!fromPhone && s.gender == Gender.MALE) {
            Section("জামাত") {
                SwitchRow("জামাতের সময় ধরে ডাক", "মসজিদের সাধারণ সময় — যোহর ১:৩০, আসর আযানের ২০ মিনিট পর…", s.jamaat.enabled) { on ->
                    app.update { it.copy(jamaat = it.jamaat.copy(enabled = on)) }
                }
                if (s.jamaat.enabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField("অফিসে যোহর", s.jamaat.officeDhuhr, allowEmpty = true) { t -> app.update { it.copy(jamaat = it.jamaat.copy(officeDhuhr = t)) } }
                        TimeField("অফিসে আসর", s.jamaat.officeAsr, allowEmpty = true) { t -> app.update { it.copy(jamaat = it.jamaat.copy(officeAsr = t)) } }
                    }
                    Text("জামাতের ${Bn.num(s.jamaat.leadMin)} মিনিট আগে ডাক", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = s.jamaat.leadMin.toFloat(), valueRange = 5f..30f, steps = 4,
                        onValueChange = { v -> app.update { it.copy(jamaat = it.jamaat.copy(leadMin = (Math.round(v / 5f) * 5).coerceIn(5, 30))) } },
                    )
                }
            }
        }

        if (!fromPhone) Section("অফিসের সময়") {
            SwitchRow("অফিস আছে", "অফিসের সময়ে অফিসের জামাত ধরা হয়", s.work.enabled) { on -> app.update { it.copy(work = it.work.copy(enabled = on)) } }
            if (s.work.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("শুরু", s.work.start) { t -> if (t != null) app.update { it.copy(work = it.work.copy(start = t)) } }
                    TimeField("শেষ", s.work.end) { t -> if (t != null) app.update { it.copy(work = it.work.copy(end = t)) } }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in WEEK) {
                        val on = d in s.work.days
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (on) Emerald600 else Color.Transparent)
                                .border(1.dp, if (on) Emerald600 else Line, RoundedCornerShape(50))
                                .clickable { app.update { it.copy(work = it.work.copy(days = if (on) it.work.days - d else it.work.days + d)) } }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) { Text(Bn.weekdayShort(LocalDate.of(2026, 1, 5).with(java.time.temporal.TemporalAdjusters.nextOrSame(d))), color = if (on) Color.White else Ink) }
                    }
                }
            }
        }

        if (!fromPhone) Section("ডাকের মাত্রা") {
            RadioList(Intensity.entries, s.intensity, { it.bn }, { it.desc }) { v -> app.update { it.copy(intensity = v) } }
        }

        Section("ম্যাকে কীভাবে ডাকবে") {
            SwitchRow("কোণে ছোট জানালা", "ওয়াক্ত/জামাতের ডাক পর্দার কোণে — \"পড়েছি\" বা \"১০ মিনিট পরে\"", s.callWindow) { on -> app.update { it.copy(callWindow = on) } }
            SwitchRow("শেষ সতর্কতা পুরো পর্দায়", "ওয়াক্ত শেষের ২০ মিনিট আগে, তখনো না পড়লে", s.fullScreenLast) { on -> app.update { it.copy(fullScreenLast = on) } }
            SwitchRow("শব্দ", null, s.sound) { on -> app.update { it.copy(sound = on) } }
            SwitchRow("ম্যাক চালু হলে নিজে থেকে চালু", null, s.launchAtLogin) { on -> app.update { it.copy(launchAtLogin = on) } }
        }

        Text(
            "সব তথ্য শুধু এই ম্যাকে থাকে (~/Library/Application Support/Istiqamah)। কোনো সার্ভারে কিছু যায় না।",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
    }
}

// ---------------------------------------------------------------------
// কোণের ডাক ও পুরো পর্দা
// ---------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkButtons(app: MacApp, call: ActiveCall, dark: Boolean) {
    val n = call.reminder.notif
    val p = n.prayer ?: return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in statusChoices(app.settings.gender).filter { it.prayed }) {
            Button(
                onClick = { app.mark(n.date, p, c) },
                colors = ButtonDefaults.buttonColors(containerColor = if (dark) Gold else Emerald600, contentColor = if (dark) Emerald900 else Color.White),
            ) { Text("পড়েছি · ${statusLabel(c, app.settings.gender)}") }
        }
    }
}

@Composable
fun CallCard(app: MacApp, call: ActiveCall) {
    val r = call.reminder
    Surface(
        Modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp), color = Color.White,
        border = BorderStroke(1.dp, Gold), shadowElevation = 8.dp,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🕌", fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text(r.title, style = MaterialTheme.typography.titleMedium, color = Emerald900, modifier = Modifier.weight(1f))
                TextButton(onClick = { app.dismiss(call) }) { Text("✕", color = Muted) }
            }
            Text(r.body, style = MaterialTheme.typography.bodyMedium, color = Ink)
            call.content?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Emerald50).padding(12.dp)) { ContentBlock(it, compact = true) }
            }
            MarkButtons(app, call, dark = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { app.snooze(call) }) { Text("${Bn.num(MacEngine.SNOOZE_MIN)} মিনিট পরে") }
                TextButton(onClick = { app.dismiss(call) }) { Text("ওযু করতে যাচ্ছি") }
            }
        }
    }
}

@Composable
fun FullScreenCall(app: MacApp, call: ActiveCall) {
    val r = call.reminder
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Emerald800, Emerald900))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🕌", fontSize = 56.sp)
            Text(r.title, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White, textAlign = TextAlign.Center)
            Text(r.body, style = MaterialTheme.typography.titleMedium, color = GoldLight, textAlign = TextAlign.Center)
            call.content?.let {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)).padding(20.dp)) {
                    ContentBlock(it, dark = true)
                }
            }
            MarkButtons(app, call, dark = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { app.dismiss(call) }, border = BorderStroke(1.dp, GoldLight)) {
                    Text("এখনই নামাজে যাচ্ছি", color = Color.White)
                }
                TextButton(onClick = { app.snooze(call) }) { Text("${Bn.num(MacEngine.SNOOZE_MIN)} মিনিট পরে", color = Color.White.copy(alpha = 0.7f)) }
            }
        }
    }
}

// ---------------------------------------------------------------------
// ফোনের সাথে জোড়া (QR)
// ---------------------------------------------------------------------

fun syncLine(app: MacApp): String {
    val p = app.pair
    if (!p.paired) return "ফোনের সাথে এখনো জোড়া লাগেনি।"
    val at = if (p.lastSyncAt > 0) java.time.Instant.ofEpochMilli(p.lastSyncAt).atZone(java.time.ZoneId.systemDefault()) else null
    val whenText = at?.let {
        val today = java.time.LocalDate.now()
        (if (it.toLocalDate() == today) "আজ " else Bn.date(it.toLocalDate()) + ", ") + Bn.time(it.toLocalTime())
    } ?: "—"
    return "${p.phoneName ?: "ফোন"}-এর সাথে সর্বশেষ সিঙ্ক: $whenText।"
}

@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
                mapOf(
                    com.google.zxing.EncodeHintType.MARGIN to 2,
                    com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                ),
            )
        }.getOrNull()
    }
    androidx.compose.foundation.Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = minOf(size.width / m.width, size.height / m.height)
        val ox = (size.width - cell * m.width) / 2
        val oy = (size.height - cell * m.height) / 2
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(
                Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(ox + x * cell, oy + y * cell),
                size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f),
            )
        }
    }
}

@Composable
fun PairScreen(app: MacApp) {
    val p = app.pair
    val info = remember(p.key.contentHashCode(), app.syncPort) { app.pairInfo() }
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (p.paired) {
            Card(bg = Emerald50) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("✓ ${p.phoneName ?: "ফোন"}-এর সাথে জোড়া লাগানো", style = MaterialTheme.typography.titleMedium, color = Emerald900)
                    Text(syncLine(app), style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(
                        "ফোনে চিহ্ন দিলে সাথে সাথে এখানে আসে; এখানে দিলে ফোন পরের বার অ্যাপ খুললে বা ডাক বাজার ঠিক আগে নিয়ে নেয় — তাই একই নামাজে দুই জায়গায় ডাক বাজে না।",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        } else {
            Text("ফোনের সাথে জোড়া লাগান", style = MaterialTheme.typography.titleLarge, color = Emerald900)
        }
        if (info.hosts.isEmpty() || app.syncPort == 0) {
            Card(bg = Color(0xFFFFF4E5)) {
                Text(
                    if (app.syncPort == 0) "সিঙ্ক চালু করা যায়নি — অন্য কোনো প্রোগ্রাম পোর্ট আটকে রেখেছে। অ্যাপটা বন্ধ করে আবার খুলুন।"
                    else "ম্যাক এখন কোনো Wi-Fi-তে নেই। ফোন আর ম্যাক একই Wi-Fi-তে যুক্ত করুন।",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (!p.paired || confirmReset) {
            Text(
                "১. ফোনে ইস্তিকামাহ → সেটিংস → \"ম্যাকের সাথে সিঙ্ক\" → \"QR স্ক্যান করুন\"\n২. এই কোডটা ফোনের ক্যামেরায় ধরুন\n৩. ফোন আর ম্যাক একই Wi-Fi-তে থাকলেই হবে",
                style = MaterialTheme.typography.bodyMedium,
            )
            QrCode(SyncPair.uri(info), Modifier.size(260.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)))
            Text(
                "এই কোডে গোপন চাবি আছে — অন্য কাউকে দেখাবেন না বা ছবি তুলে পাঠাবেন না।",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB5452F), textAlign = TextAlign.Center,
            )
        } else {
            OutlinedButton(onClick = { app.resetPair(); confirmReset = true }) { Text("অন্য ফোন জোড়া লাগান (নতুন QR)") }
            Text("নতুন QR বানালে আগের ফোন আর সিঙ্ক করতে পারবে না।", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(
            "যা মেলে: পাঁচ ওয়াক্তের চিহ্ন (দুই দিকে), আর ফোনের জেলা, হিসাব, জামাত, অফিস ও ডাকের মাত্রা (ফোন → ম্যাক)। " +
                "সব এনক্রিপ্ট করা, শুধু আপনার Wi-Fi-র ভেতরে — ইন্টারনেটে বা কোনো সার্ভারে যায় না। গুনাহের ট্র্যাকার, যাকাত, দেনা, ওসিয়ত কখনো আসে না।",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
    }
}
