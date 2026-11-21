package com.istiqamah.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.FastStatus
import com.istiqamah.app.core.Fasting
import com.istiqamah.app.core.HijriDay
import com.istiqamah.app.core.NaflFast
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.QuranGoal
import com.istiqamah.app.core.QuranUnit
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.QuranRepo
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val WEEKDAY_BN = mapOf(
    java.time.DayOfWeek.SATURDAY to "শনি", java.time.DayOfWeek.SUNDAY to "রবি", java.time.DayOfWeek.MONDAY to "সোম",
    java.time.DayOfWeek.TUESDAY to "মঙ্গল", java.time.DayOfWeek.WEDNESDAY to "বুধ", java.time.DayOfWeek.THURSDAY to "বৃহস্পতি",
    java.time.DayOfWeek.FRIDAY to "শুক্র",
)

/**
 * v0.7 — রোজা ও রমজান: আজকের হিজরি, রমজানের সাহরি-ইফতার ও লগ, রমজানে খতমের প্ল্যান,
 * সামনের নফল রোজা, শাওয়ালের ছয়, কাযা রোজা, রিমাইন্ডারের সেটিং।
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FastingScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    val ver = vm.settingsVersion
    var refresh by remember { mutableIntStateOf(0) }
    val today = Engine.today(ctx)
    val h = p.hijri(today)
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    val w = Engine.windows(ctx, today)?.associateBy { it.prayer }
    val log by produceState<Map<LocalDate, FastStatus>>(emptyMap(), ver, refresh) {
        value = withContext(Dispatchers.IO) { LogStore.get(ctx).fasts(today.minusDays(40), today) }
    }
    val shawwal by produceState(0, ver, refresh) { value = withContext(Dispatchers.IO) { Engine.shawwalDone(ctx, today) } }
    fun logToday(st: FastStatus?) {
        scope.launch { withContext(Dispatchers.IO) { Engine.logFast(ctx, today, st) }; refresh++; vm.settingsChanged() }
    }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("রোজা ও রমজান", h?.let { "আজ ${it.bn} হিজরি" } ?: "", onBack)

        // ---------- আজ ----------
        SectionCard {
            if (h?.isRamadan == true) {
                Text("রমজানের ${Bn.num(h.day)}তম দিন", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                w?.let {
                    Text(
                        "সাহরি শেষ ${Bn.time(it.getValue(Prayer.FAJR).start.toLocalTime())} · ইফতার ${Bn.time(it.getValue(Prayer.MAGHRIB).start.toLocalTime())}",
                        fontSize = 16.sp, color = Ink,
                    )
                }
            } else {
                val reasons = Fasting.naflReasons(today, h, shawwal).filter { it in p.naflFasts }
                Text(if (Fasting.forbidden(h)) "আজ রোজা রাখা নিষেধ (ঈদ/তাশরিক)" else if (reasons.isEmpty()) "আজ বিশেষ নফল রোজার দিন নয়" else "আজ: ${reasons.joinToString(" · ") { it.bn }}",
                    fontSize = 16.sp, color = Emerald800, fontWeight = FontWeight.SemiBold)
            }
            val st = log[today]
            Spacer(Modifier.height(8.dp))
            Text("আজকের রোজা: ${st?.bn ?: "লেখা হয়নি"}", fontSize = 14.sp, color = Muted)
            if (!Fasting.forbidden(h)) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FastStatus.entries.forEach { s ->
                    FilterChip(
                        selected = st == s, onClick = { logToday(if (st == s) null else s) },
                        label = { Text(s.bn) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- রমজান ----------
        if (h?.isRamadan == true) {
            SectionCard {
                FTitle("এই রমজান")
                val start = today.minusDays(h.day.toLong() - 1)
                val days = (0 until h.day).map { start.plusDays(it.toLong()) }
                val fasted = days.count { log[it] == FastStatus.FASTED }
                Text("রোজা রেখেছেন ${Bn.num(fasted)}/${Bn.num(h.day)} দিন", fontSize = 16.sp, color = Ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEachIndexed { i, d ->
                        val s = log[d]
                        Text(
                            Bn.num(i + 1),
                            modifier = Modifier.padding(2.dp).background(
                                when (s) { FastStatus.FASTED -> Emerald50; null -> Cream; else -> androidx.compose.ui.graphics.Color(0xFFFCE7D6) },
                                androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                            ).padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 13.sp, color = if (s == FastStatus.FASTED) Emerald800 else Muted,
                        )
                    }
                }
                RamadanKhatmPlan(vm, h)
            }
            Spacer(Modifier.height(12.dp))
        } else {
            SectionCard {
                FTitle("সামনের নফল রোজা (৩০ দিন)")
                val upcoming = (1L..30L).map { today.plusDays(it) }
                    .map { d -> d to Fasting.naflReasons(d, p.hijri(d), shawwal).filter { it in p.naflFasts && it != NaflFast.MON_THU } }
                    .filter { it.second.isNotEmpty() }
                if (upcoming.isEmpty()) Text("সামনে শুধু সোম/বৃহস্পতিবারের সুন্নত রোজা।", fontSize = 14.sp, color = Muted)
                upcoming.take(8).forEach { (d, rs) ->
                    Text("${WEEKDAY_BN[d.dayOfWeek]}, ${Bn.date(d)} — ${rs.joinToString(" · ") { it.bn }}", fontSize = 15.sp, color = Ink, modifier = Modifier.padding(vertical = 3.dp))
                }
                daysToRamadan(today, p::hijri)?.let { Text("রমজান আসতে প্রায় ${Bn.num(it)} দিন বাকি", fontSize = 14.sp, color = Emerald700, modifier = Modifier.padding(top = 6.dp)) }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (h?.month == 10) {
            SectionCard {
                FTitle("শাওয়ালের ছয় রোজা")
                Text("${Bn.num(shawwal)}/৬ রাখা হয়েছে — রমজানের সাথে মিলে যেন সারা বছর রোজা (মুসলিম ১১৬৪)", fontSize = 15.sp, color = Ink)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- কাযা ----------
        SectionCard {
            FTitle("কাযা রোজা")
            Text("বাকি: ${Bn.num(p.qazaFasts)}টি", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("রমজানে \"রাখিনি\" বা \"অসুস্থ/সফর/বিরতি\" দিলে নিজে থেকেই যোগ হয়।", fontSize = 13.sp, color = Muted)
            Row(Modifier.padding(top = 6.dp)) {
                Button(
                    enabled = p.qazaFasts > 0,
                    onClick = { scope.launch { withContext(Dispatchers.IO) { Engine.qazaFastDone(ctx) }; refresh++; vm.settingsChanged(); Toast.makeText(ctx, "আল্লাহ কবুল করুন", Toast.LENGTH_SHORT).show() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                ) { Text("আজ একটা কাযা রাখলাম") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { p.qazaFasts = p.qazaFasts + 1; vm.settingsChanged() }) { Text("+১ যোগ", color = Emerald800) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- সেটিং ----------
        SectionCard {
            FTitle("রিমাইন্ডার")
            FToggle("রমজানের রিমাইন্ডার", "সাহরি, সাহরি শেষ, ইফতার, কদরের বেজোড় রাত, রমজানের আগের সন্ধ্যা", p.fastingOn) { p.fastingOn = it; vm.settingsChanged() }
            if (p.fastingOn) Row(Modifier.fillMaxWidth().clickable { dialog = "lead" }.padding(vertical = 10.dp)) {
                Column {
                    Text("সাহরির ডাক", fontSize = 17.sp, color = Ink)
                    Text("ফজরের ${Bn.num(p.suhoorLead)} মিনিট আগে", fontSize = 14.sp, color = Muted)
                }
            }
            val routineOn = NotifKind.FAST_EVE in p.routine
            FToggle("নফল রোজার আগের রাতে মনে করাবে", "ইশার ৩০ মিনিট পরে", routineOn) { on ->
                p.routine = if (on) p.routine + NotifKind.FAST_EVE else p.routine - NotifKind.FAST_EVE; vm.settingsChanged()
            }
            if (routineOn) NaflFast.entries.forEach { n ->
                FToggle(n.bn, "", n in p.naflFasts) { on -> p.naflFasts = if (on) p.naflFasts + n else p.naflFasts - n; vm.settingsChanged() }
            }
            Text("হিজরি তারিখ চাঁদ দেখার সাথে না মিললে:", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            Row {
                (-1..1).forEach { a ->
                    FilterChip(
                        selected = p.hijriAdjust == a, onClick = { p.hijriAdjust = a; vm.settingsChanged() },
                        label = { Text(if (a == 0) "ঠিক আছে" else "${if (a > 0) "+" else "−"}${Bn.num(Math.abs(a))} দিন") },
                        modifier = Modifier.padding(end = 6.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (dialog == "lead") {
        val opts = listOf(30, 45, 60, 75, 90)
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("ফজরের কত আগে সাহরির ডাক?") },
            text = {
                Column {
                    opts.forEach { m ->
                        Row(Modifier.fillMaxWidth().clickable { p.suhoorLead = m; vm.settingsChanged(); dialog = null }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = p.suhoorLead == m, onClick = { p.suhoorLead = m; vm.settingsChanged(); dialog = null })
                            Text("${Bn.num(m)} মিনিট আগে")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("বন্ধ") } },
        )
    }
}

/** রমজানের বাকি দিনে খতমের হিসাব — এক ট্যাপে কুরআনের দৈনিক লক্ষ্য বানানো */
@Composable
fun RamadanKhatmPlan(vm: AppVm, h: HijriDay) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by produceState(QuranRepo.loaded(), Unit) { if (value == null) value = withContext(Dispatchers.IO) { QuranRepo.get(ctx) } }
    val d = data ?: return
    val page = d.index.pageOf(vm.prefs.quranBookmark)
    val left = Fasting.ramadanDaysLeft(h)
    val perDay = Fasting.khatmPagesPerDay(page, d.index.pageCount, left)
    Spacer(Modifier.height(10.dp))
    Text("রমজানে খতম", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold)
    Text("পৃষ্ঠা ${Bn.num(page)} থেকে বাকি ${Bn.num(d.index.pageCount - page + 1)} পৃষ্ঠা, ${Bn.num(left)} দিনে — দিনে ${Bn.num(perDay)} পৃষ্ঠা পড়লে ঈদের আগে খতম।",
        fontSize = 15.sp, color = Ink)
    val goal = QuranGoal(QuranUnit.PAGE, perDay.coerceAtLeast(1))
    if (vm.prefs.quranGoal != goal) TextButton(onClick = {
        vm.prefs.quranGoal = goal
        scope.launch { withContext(Dispatchers.IO) { Engine.recheckQuranToday(ctx, d) }; vm.settingsChanged() }
        Toast.makeText(ctx, "কুরআনের দৈনিক লক্ষ্য: ${goal.bn}", Toast.LENGTH_SHORT).show()
    }) { Text("এটাই আমার দৈনিক লক্ষ্য করুন ›", color = Emerald700) }
}

/** সামনে রমজান কত দিন পরে (৩৬০ দিনের মধ্যে) */
private fun daysToRamadan(today: LocalDate, hijri: (LocalDate) -> HijriDay?): Int? =
    (1..360).firstOrNull { hijri(today.plusDays(it.toLong()))?.isRamadan == true }

/** হোমে রমজানের কার্ড: সাহরি/ইফতারের সময় আর এক ট্যাপে "রোজা রেখেছি" */
@Composable
fun RamadanTodayCard(vm: AppVm, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val ver = vm.settingsVersion
    val today = Engine.today(ctx)
    val h = p.hijri(today)
    if (h?.isRamadan != true || p.pausedFrom != null) return
    val w = Engine.windows(ctx, today)?.associateBy { it.prayer } ?: return
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val st by produceState<FastStatus?>(null, ver, refresh) { value = withContext(Dispatchers.IO) { LogStore.get(ctx).fast(today) } }
    SectionCard(Modifier.clickable { open("fasting") }) {
        Text("🌙 রমজানের ${Bn.num(h.day)}তম দিন", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text(
            "সাহরি শেষ ${Bn.time(w.getValue(Prayer.FAJR).start.toLocalTime())} · ইফতার ${Bn.time(w.getValue(Prayer.MAGHRIB).start.toLocalTime())}",
            fontSize = 15.sp, color = Ink,
        )
        if (st == null) TextButton(onClick = {
            scope.launch { withContext(Dispatchers.IO) { Engine.logFast(ctx, today, FastStatus.FASTED) }; refresh++; vm.settingsChanged() }
        }) { Text("আজ রোজা রেখেছি ✓", color = Emerald700) } else Text("আজ: ${st!!.bn}", fontSize = 14.sp, color = Emerald700)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun FTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun FToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Ink)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
    }
}
