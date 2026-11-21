package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Akhlaq
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.CheckAnswer
import com.istiqamah.app.core.DebtEntry
import com.istiqamah.app.core.DebtKind
import com.istiqamah.app.core.HaqqArea
import com.istiqamah.app.core.Ihsan
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.engine.Engine
import java.time.DayOfWeek
import java.time.LocalDate

// =====================================================================
// v0.10 — আখলাক ও হুকুকুল ইবাদ
// =====================================================================

private val DAY_NAMES = listOf(
    DayOfWeek.SATURDAY to "শনিবার", DayOfWeek.SUNDAY to "রবিবার", DayOfWeek.MONDAY to "সোমবার", DayOfWeek.TUESDAY to "মঙ্গলবার",
    DayOfWeek.WEDNESDAY to "বুধবার", DayOfWeek.THURSDAY to "বৃহস্পতিবার", DayOfWeek.FRIDAY to "শুক্রবার",
)

@Composable
fun AkhlaqScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val today = Engine.today(ctx)
    val week = Ihsan.weekKey(today)
    var answers by remember(week) { mutableStateOf(p.akhlaqWeek(week)) }
    var doneToday by remember { mutableStateOf(today in p.akhlaqDone) }
    val (area, action) = Akhlaq.actionFor(today)

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("আখলাক ও বান্দার হক", "ইহসান — মানুষের সাথে", onBack)
        SectionCard {
            Akhlaq.INTRO_HADITH.mapNotNull { ContentRepo.byId(ctx, it) }.forEachIndexed { i, h ->
                if (i > 0) HorizontalDivider(color = Emerald50, modifier = Modifier.padding(vertical = 8.dp))
                Text(if (h.id == "h_muslim_2581") h.bn else h.shortBn, fontSize = 15.sp, lineHeight = 23.sp, color = Ink)
                Text(h.refBn, fontSize = 12.sp, color = Muted)
            }
            Text(
                "নামাজ-রোজার হক আল্লাহ চাইলে মাফ করেন; কিন্তু মানুষের হক — যার হক তাকেই ফিরিয়ে দিতে বা তার কাছে মাফ চেয়ে নিতে হয়।",
                fontSize = 13.sp, color = Emerald700, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        // ---------- আজকের ভালো আচরণ ----------
        SectionCard {
            Text("আজকের ভালো আচরণ · ${area.title}", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold)
            Text(action, fontSize = 17.sp, lineHeight = 25.sp, color = Ink)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        doneToday = !doneToday
                        p.akhlaqDone = if (doneToday) p.akhlaqDone + today else p.akhlaqDone - today
                        vm.refresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (doneToday) Emerald50 else Emerald700),
                ) { Text(if (doneToday) "✓ করেছি" else "করেছি", color = if (doneToday) Emerald800 else Color.White) }
                Spacer(Modifier.width(8.dp))
                Text("গত ৩০ দিনে ${Bn.num(p.akhlaqDone.count { !it.isBefore(today.minusDays(29)) })} দিন", fontSize = 13.sp, color = Muted)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("আজ ট্যাবে এই কার্ড", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = p.akhlaqToday, onCheckedChange = { p.akhlaqToday = it; vm.settingsChanged() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- ছয় ক্ষেত্র ----------
        Text("ছয়টি হক", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
        Akhlaq.AREAS.forEach { a ->
            SectionCard(Modifier.clickable { open("akhlaq:${a.key}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
                        Text(a.subtitle, fontSize = 13.sp, color = Muted)
                    }
                    answers[a.key]?.let { s -> Text(CheckAnswer.fromScore(s)?.bn ?: "", fontSize = 13.sp, color = if (s == 2) Emerald700 else Gold) }
                    Text("  ›", color = Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))

        // ---------- সাপ্তাহিক আত্মজিজ্ঞাসা ----------
        SectionCard {
            Text("এ সপ্তাহের আত্মজিজ্ঞাসা", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("শুধু নিজের কাছে সৎ উত্তর — এটা আপনার ফোনেই থাকে। প্রতি সোমবার নতুন সপ্তাহ।", fontSize = 13.sp, color = Muted)
            Akhlaq.AREAS.forEach { a ->
                Text(a.check, fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CheckAnswer.entries.reversed().forEach { c ->
                        FilterChip(
                            selected = answers[a.key] == c.score,
                            onClick = { p.setAkhlaqAnswer(week, a.key, c.score); answers = p.akhlaqWeek(week); vm.refresh() },
                            label = { Text(c.bn) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val weeks = (3 downTo 0).map { w -> today.minusWeeks(w.toLong()) }
            Text("গত ৪ সপ্তাহ", fontSize = 13.sp, color = Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                weeks.forEachIndexed { i, d ->
                    val pct = Akhlaq.weekPercent(if (i == 3) answers else p.akhlaqWeek(Ihsan.weekKey(d)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.width(26.dp).height(44.dp)) {
                            drawRoundRect(Emerald50, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                            pct?.let { v0 ->
                                val h = size.height * v0 / 100f
                                drawRoundRect(if (v0 >= 70) Emerald700 else Gold, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(4.dp.toPx()))
                            }
                        }
                        Text(pct?.let { "${Bn.num(it)}%" } ?: "—", fontSize = 11.sp, color = Muted)
                        Text(if (i == 3) "এ সপ্তাহ" else "${Bn.num(3 - i)} সপ্তাহ আগে", fontSize = 10.sp, color = Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("ব্যাখ্যা ও হাদিস আলেমের রিভিউয়ের অপেক্ষায়। লেনদেন, উত্তরাধিকার বা কারও হক নিয়ে জটিল প্রশ্নে আলেমের কাছে যান।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AkhlaqAreaScreen(vm: AppVm, key: String, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val a = Akhlaq.byKey(key) ?: run {
        androidx.compose.runtime.LaunchedEffect(key) { onBack() }
        return
    }
    val p = vm.prefs
    val data = rememberQuran()
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader(a.title, a.subtitle, onBack)
        SectionCard {
            Text("আজই করা যায়", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold)
            a.actions.forEach { Text("• $it", fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(top = 4.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        if (a.key == "kin" || a.key == "parents") { KinDayCard(vm); Spacer(Modifier.height(12.dp)) }
        if (a.key == "debt") { DebtsCard(vm); Spacer(Modifier.height(12.dp)) }
        Text("কুরআন থেকে", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
        a.ayat.forEach { AyahBlock(data, it, open, p.quranScript == 0); Spacer(Modifier.height(8.dp)) }
        Text("হাদিস থেকে", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
        a.hadith.forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KinDayCard(vm: AppVm) {
    val p = vm.prefs
    var day by remember { mutableStateOf(p.kinDay) }
    SectionCard {
        Text("সাপ্তাহিক আত্মীয়তার দিন", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("বেছে নেওয়া দিনে মাগরিবের পরে মনে করাব — বাবা-মা বা কোনো আত্মীয়কে একটা ফোন।", fontSize = 13.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = day == null, onClick = { day = null; p.kinDay = null; vm.settingsChanged() }, label = { Text("বন্ধ") })
            DAY_NAMES.forEach { (d, name) ->
                FilterChip(
                    selected = day == d, onClick = { day = d; p.kinDay = d; vm.settingsChanged() }, label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                )
            }
        }
    }
}

// =====================================================================
// দেনা, পাওনা, আমানত — এনক্রিপ্ট করে শুধু ফোনে
// =====================================================================

@Composable
private fun DebtsCard(vm: AppVm) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val today = Engine.today(ctx)
    var list by remember { mutableStateOf(p.debts) }
    var edit by remember { mutableStateOf<DebtEntry?>(null) }
    var showDone by remember { mutableStateOf(false) }
    fun save(v: List<DebtEntry>) { list = v; p.debts = v; vm.settingsChanged() }

    SectionCard {
        Text("আমার দেনা-পাওনা ও আমানত", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("নাম ও অঙ্ক এনক্রিপ্ট করে শুধু এই ফোনে থাকে। রিমাইন্ডার আসে তারিখের ৩ দিন আগে ও সেদিন — নাম বা অঙ্ক ছাড়া।", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
        val owe = Akhlaq.openTotal(list, DebtKind.I_OWE)
        val lent = Akhlaq.openTotal(list, DebtKind.OWED_TO_ME)
        if (owe > 0 || lent > 0) Text(
            listOfNotNull(owe.takeIf { it > 0 }?.let { "দেনা ৳${taka(it)}" }, lent.takeIf { it > 0 }?.let { "পাওনা ৳${taka(it)}" }).joinToString(" · "),
            fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
        )
        val overdue = Akhlaq.overdue(list, today)
        if (overdue.isNotEmpty()) Text("${Bn.num(overdue.size)}টির তারিখ পেরিয়েছে — অল্প হলেও দিন, আর পাওনাদারকে জানিয়ে রাখুন।", fontSize = 13.sp, color = StatusQaza, modifier = Modifier.padding(top = 4.dp))
        val open = list.filter { !it.done }.sortedWith(compareBy({ it.due ?: LocalDate.MAX }, { it.id }))
        open.forEach { e -> DebtRow(e, today) { edit = e } }
        if (open.isEmpty()) Text("এখনো কিছু লেখা নেই।", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 6.dp)) {
            Button(onClick = { edit = DebtEntry(0, DebtKind.I_OWE, "", 0, "", null, false) }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("+ যোগ করুন") }
            Spacer(Modifier.width(8.dp))
            val doneCount = list.count { it.done }
            if (doneCount > 0) TextButton(onClick = { showDone = !showDone }) { Text(if (showDone) "শোধ হওয়াগুলো লুকান" else "শোধ হয়েছে (${Bn.num(doneCount)})", color = Emerald700) }
        }
        if (showDone) list.filter { it.done }.forEach { e -> DebtRow(e, today) { edit = e } }
    }

    edit?.let { e ->
        DebtDialog(
            e, onDismiss = { edit = null },
            onSave = { n -> save(if (e.id == 0L) list + n.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) else list.map { if (it.id == e.id) n else it }); edit = null },
            onDelete = if (e.id != 0L) ({ save(list.filter { it.id != e.id }); edit = null }) else null,
        )
    }
}

@Composable
private fun DebtRow(e: DebtEntry, today: LocalDate, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (e.done) "✓ " else "") + e.who.ifBlank { "(নাম নেই)" }, fontSize = 16.sp, color = if (e.done) Muted else Ink)
            Text(
                listOfNotNull(e.kind.bn, e.note.takeIf { it.isNotBlank() }, e.due?.takeIf { !e.done }?.let { Akhlaq.dueLabel(it, today) }).joinToString(" · "),
                fontSize = 12.sp, color = if (!e.done && e.due != null && e.due.isBefore(today) && e.kind.remind) StatusQaza else Muted,
            )
        }
        if (e.amount > 0) Text("৳${taka(e.amount)}", fontSize = 15.sp, color = Ink)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtDialog(e: DebtEntry, onDismiss: () -> Unit, onSave: (DebtEntry) -> Unit, onDelete: (() -> Unit)?) {
    var kind by remember { mutableStateOf(e.kind) }
    var who by remember { mutableStateOf(e.who) }
    var amount by remember { mutableStateOf(if (e.amount > 0) e.amount.toString() else "") }
    var note by remember { mutableStateOf(e.note) }
    var due by remember { mutableStateOf(e.due) }
    var done by remember { mutableStateOf(e.done) }
    val today = Engine.today(LocalContext.current)
    var dueInDays by remember { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e.id == 0L) "নতুন যোগ" else "বদলান") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                DebtKind.entries.forEach { k ->
                    Row(Modifier.fillMaxWidth().clickable { kind = k }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = kind == k, onClick = { kind = k })
                        Text(k.bn, fontSize = 15.sp)
                    }
                }
                OutlinedTextField(who, { who = it.take(40) }, label = { Text("কার") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount, { amount = it.map { c -> if (c in '০'..'৯') '0' + (c - '০') else c }.filter { c -> c.isDigit() }.joinToString("").take(12) },
                    label = { Text("টাকা (আমানত জিনিস হলে খালি রাখুন)") }, prefix = { Text("৳") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("নোট (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("ফেরতের তারিখ: " + (due?.let { "${Bn.date(it)} (${Akhlaq.dueLabel(it, today)})" } ?: "নেই"), fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-1 to "নেই", 7 to "৭ দিন", 15 to "১৫ দিন", 30 to "১ মাস", 60 to "২ মাস", 90 to "৩ মাস", 180 to "৬ মাস").forEach { (n, l) ->
                        FilterChip(selected = dueInDays == n, onClick = { dueInDays = n; due = if (n < 0) null else today.plusDays(n.toLong()) }, label = { Text(l) })
                    }
                }
                if (e.id != 0L) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("শোধ / ফেরত হয়ে গেছে", fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = done, onCheckedChange = { done = it }, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
                }
                onDelete?.let { d -> TextButton(onClick = d) { Text("মুছে ফেলুন", color = StatusQaza) } }
            }
        },
        confirmButton = {
            TextButton(
                enabled = who.isNotBlank(),
                onClick = { onSave(e.copy(kind = kind, who = who.trim(), amount = amount.toLongOrNull() ?: 0, note = note.trim(), due = due, done = done)) },
            ) { Text("রাখুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

// =====================================================================
// আজ ট্যাবের কার্ড
// =====================================================================

@Composable
fun AkhlaqTodayCard(vm: AppVm, area: HaqqArea, action: String, done: Boolean, open: (String) -> Unit) {
    Card16(Modifier.clickable { open("akhlaq") }) {
        Text("আজকের ভালো আচরণ · ${area.title}", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(action, fontSize = 16.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.toggleAkhlaqToday() }) {
                Text(if (done) "✓ করেছি" else "করেছি", color = Emerald700, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
