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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Ihsan
import com.istiqamah.app.core.Khushu
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.SalahText
import com.istiqamah.app.core.SunnahSlot
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// =====================================================================
// v0.9.5 — ইহসান: আল্লাহর সাথে
// =====================================================================

private data class IhsanData(
    val sunnah: Map<LocalDate, Set<SunnahSlot>>,
    val khushu: Map<LocalDate, Map<Prayer, Int>>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IhsanScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    val ver = vm.settingsVersion
    val today = Engine.today(ctx)
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val data by produceState<IhsanData?>(null, ver, refresh) {
        value = withContext(Dispatchers.IO) {
            val s = LogStore.get(ctx)
            IhsanData(s.sunnah(today.minusDays(29), today), s.khushu(today.minusDays(6), today))
        }
    }
    fun toggle(slot: SunnahSlot, done: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { LogStore.get(ctx).setSunnah(today, slot, done) }
            refresh++
            vm.refresh()
        }
    }
    val todaySlots = data?.sunnah?.get(today).orEmpty()

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("ইহসান — আল্লাহর সাথে", "যেন তাঁকে দেখছেন; না দেখলেও তিনি তো দেখছেন", onBack)
        ContentRepo.byId(ctx, "h_bukhari_50")?.let { h ->
            SectionCard {
                Text(h.bn, fontSize = 15.sp, lineHeight = 23.sp, color = Ink)
                Text(h.refBn, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- ১২ রাকাত ----------
        SectionCard {
            Text("১২ রাকাত সুন্নাতে মুয়াক্কাদা", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("আজ ${Bn.num(Ihsan.rakat(todaySlots))}/১২ রাকাত", fontSize = 15.sp, color = Gold, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SunnahSlot.TWELVE.forEach { s ->
                    SlotChip(s, s in todaySlots) { toggle(s, it) }
                }
            }
            data?.let { d ->
                Spacer(Modifier.height(10.dp))
                WeekRakatBars(d.sunnah, today)
                val streak = Ihsan.streak(d.sunnah, today)
                Text(
                    "গত ৭ দিনে পূর্ণ ১২ রাকাত: ${Bn.num(Ihsan.fullDays(d.sunnah, today, 7))} দিন · ৩০ দিনে ${Bn.num(Ihsan.percent(d.sunnah, today, 30))}%" +
                        if (streak > 0) " · টানা ${Bn.num(streak)} দিন" else "",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            ContentRepo.byId(ctx, "h_muslim_728")?.let { Text("\"${it.shortBn}\" — ${it.refBn}", fontSize = 13.sp, color = Emerald700, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp)) }
            ContentRepo.byId(ctx, "h_muslim_725")?.let { Text("\"${it.shortBn}\" — ${it.refBn}", fontSize = 13.sp, color = Emerald700, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)) }
            val cardOn = p.sunnahCardOn ?: (vm.todayState?.showSunnah == true)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("আজ ট্যাবে এই কার্ড", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = cardOn, onCheckedChange = { p.sunnahCardOn = it; vm.refresh(); refresh++ },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- বিতর, তাহাজ্জুদ, দোহা ----------
        SectionCard {
            Text("বিতর, তাহাজ্জুদ ও দোহা", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(SunnahSlot.WITR, SunnahSlot.TAHAJJUD, SunnahSlot.DUHA).forEach { s -> SlotChip(s, s in todaySlots) { toggle(s, it) } }
            }
            data?.let { d ->
                Text(
                    "গত ৩০ দিনে — বিতর ${Bn.num(Ihsan.countOf(SunnahSlot.WITR, d.sunnah, today, 30))}, তাহাজ্জুদ ${Bn.num(Ihsan.countOf(SunnahSlot.TAHAJJUD, d.sunnah, today, 30))}, দোহা ${Bn.num(Ihsan.countOf(SunnahSlot.DUHA, d.sunnah, today, 30))} দিন",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
                )
            }
            listOf(NotifKind.TAHAJJUD to "তাহাজ্জুদের রিমাইন্ডার (রাতের শেষ তৃতীয়াংশে)", NotifKind.DUHA to "ইশরাক/দোহার রিমাইন্ডার").forEach { (k, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                    Switch(checked = k in p.routine, onCheckedChange = { on ->
                        p.routine = if (on) p.routine + k else p.routine - k
                        vm.settingsChanged()
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- খুশু ----------
        SectionCard {
            Text("নামাজে মন — খুশু", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            ContentRepo.byId(ctx, "q_23_1_2")?.let { Text("\"${it.shortBn}\" (${it.refBn})", fontSize = 13.sp, color = Emerald700, lineHeight = 19.sp) }
            data?.let { d ->
                val w = Ihsan.khushuWeek(d.khushu, today)
                Spacer(Modifier.height(8.dp))
                KhushuBars(w.days)
                Text(
                    w.average?.let { "এ সপ্তাহে গড়: ${"%.1f".format(it)}/৩ (${Bn.num(w.rated)}টি নামাজের মাপ)" } ?: "এখনো মাপ নেই — নামাজ পড়েছি চাপলে আজ ট্যাবে জিজ্ঞেস করা হবে।",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("মন ফেরানোর উপায়", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold)
            Ihsan.KHUSHU_TIPS.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
            TextButton(onClick = { open("prime") }) { Text("৬০ সেকেন্ডের প্রস্তুতি ›", color = Emerald700) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("নামাজের পর \"মন কতটা ছিল?\" জিজ্ঞেস করুন", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = p.khushuAsk, onCheckedChange = { p.khushuAsk = it; vm.refresh(); refresh++ },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- নামাজে যা পড়ি ----------
        Text("নামাজে যা পড়ি — অর্থসহ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
        SectionCard(Modifier.clickable { open("reader:1") }) {
            Text("সূরা ফাতিহা — প্রতি রাকাতে", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
            Text("অর্থসহ পড়ুন কুরআন পাঠকে ›", fontSize = 14.sp, color = Emerald700)
        }
        Spacer(Modifier.height(8.dp))
        Ihsan.SALAH_TEXTS.forEach { SalahTextCard(it); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(4.dp))

        // ---------- গোপন আমল ----------
        SecretDeedsCard(vm, today) { refresh++ }
        Spacer(Modifier.height(10.dp))
        Text("ব্যাখ্যা ও অর্থ আলেমের রিভিউয়ের অপেক্ষায়। নামাজের মাসআলায় নিজের মাযহাবের আলেমকে জিজ্ঞেস করুন।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SlotChip(s: SunnahSlot, done: Boolean, onToggle: (Boolean) -> Unit) {
    FilterChip(
        selected = done, onClick = { onToggle(!done) },
        label = { Text((if (done) "✓ " else "") + s.bn + if (s.twelve) " · ${Bn.num(s.rakat)}" else "") },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
    )
}

/** গত ৭ দিনের রাকাত — ১২-এর মধ্যে কতটা */
@Composable
private fun WeekRakatBars(log: Map<LocalDate, Set<SunnahSlot>>, today: LocalDate) {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { d ->
            val r = Ihsan.rakat(log[d].orEmpty())
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                Canvas(Modifier.width(18.dp).height(44.dp)) {
                    drawRoundRect(Emerald50, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                    val h = size.height * r / 12f
                    if (h > 0) drawRoundRect(if (r == 12) Emerald700 else Gold, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(4.dp.toPx()))
                }
                Text(Bn.num(r), fontSize = 11.sp, color = Muted)
                Text(Bn.weekdayShort(d), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

/** গত ৭ দিনের গড় খুশু (১–৩) */
@Composable
private fun KhushuBars(days: List<Pair<LocalDate, Double?>>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { (d, avg) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                Canvas(Modifier.width(18.dp).height(40.dp)) {
                    drawRoundRect(Emerald50, Offset.Zero, size, CornerRadius(4.dp.toPx()))
                    avg?.let { a ->
                        val h = size.height * (a / 3.0).toFloat()
                        drawRoundRect(if (a >= 2.5) Emerald700 else if (a >= 1.75) Gold else Color(0xFFD08A5B), Offset(0f, size.height - h), Size(size.width, h), CornerRadius(4.dp.toPx()))
                    }
                }
                Text(Bn.weekdayShort(d), fontSize = 10.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun SalahTextCard(t: SalahText) {
    var open by remember { mutableStateOf(false) }
    SectionCard(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.whenBn, fontSize = 13.sp, color = Muted)
                Text(t.pron.substringBefore("،").substringBefore(",").take(40) + if (t.pron.length > 40) "…" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
            }
            Text(if (open) "▲" else "▼", color = Muted)
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(t.arabic, fontFamily = Amiri, fontSize = 22.sp, lineHeight = 42.sp, color = Emerald900, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
            Text("উচ্চারণ: ${t.pron}", fontSize = 14.sp, lineHeight = 21.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
            Text("অর্থ: ${t.bn}", fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
            Text(t.ref, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SecretDeedsCard(vm: AppVm, today: LocalDate, onChange: () -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val week = Ihsan.weekKey(today)
    var done by remember(week) { mutableStateOf(p.secretDone.filter { it.startsWith("$week:") }.map { it.removePrefix("$week:") }.toSet()) }
    SectionCard {
        Text("গোপন আমল — এ সপ্তাহ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("যা শুধু আল্লাহ জানেন। এই তালিকা শুধু আপনার ফোনে থাকে, কোথাও দেখানো হয় না; প্রতি সোমবার নতুন।", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
        Ihsan.SECRET_DEEDS.forEach { d ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    done = if (d.key in done) done - d.key else done + d.key
                    p.secretDone = done.map { "$week:$it" }.toSet() // আগের সপ্তাহগুলো ফেলে দিই
                    onChange()
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = d.key in done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                Column(Modifier.weight(1f)) {
                    Text(d.bn, fontSize = 15.sp, color = Ink)
                    Text(d.ref, fontSize = 12.sp, color = Muted)
                }
            }
        }
        ContentRepo.byId(ctx, "h_muslim_2985")?.let {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFFBF1E0), RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text("রিয়া থেকে সাবধান", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(it.bn, fontSize = 14.sp, lineHeight = 21.sp, color = Ink)
                Text(it.refBn, fontSize = 12.sp, color = Muted)
            }
        }
    }
}

// =====================================================================
// আজ ট্যাবের কার্ডগুলো
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SunnahTodayCard(vm: AppVm, done: Set<SunnahSlot>, open: (String) -> Unit) {
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("১২ রাকাত সুন্নাত", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text("জান্নাতে একটি ঘর (মুসলিম ৭২৮)", fontSize = 13.sp, color = Muted)
            }
            Text("${Bn.num(Ihsan.rakat(done))}/১২", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (Ihsan.rakat(done) == 12) Emerald700 else Gold)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SunnahSlot.TWELVE.forEach { s -> SlotChip(s, s in done) { vm.toggleSunnah(s) } }
            listOf(SunnahSlot.WITR, SunnahSlot.TAHAJJUD, SunnahSlot.DUHA).forEach { s -> SlotChip(s, s in done) { vm.toggleSunnah(s) } }
        }
        TextButton(onClick = { open("ihsan") }) { Text("ইহসান — খুশু, গোপন আমল, নামাজের অর্থ ›", color = Emerald700, fontSize = 13.sp) }
    }
}

@Composable
fun KhushuPromptCard(vm: AppVm, prayer: Prayer) {
    Card16 {
        Text("${Bn.of(prayer.bn)} নামাজে মন কতটা ছিল?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
        Text("শুধু নিজের জন্য — সপ্তাহ শেষে নিজেকে দেখার জন্য", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Khushu.entries.forEach { k ->
                OutlinedButton(onClick = { vm.rateKhushu(prayer, k.score) }, modifier = Modifier.weight(1f)) {
                    Text(k.bn, fontSize = 12.sp, color = Emerald800, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun TahajjudTipCard(vm: AppVm) {
    Card16 {
        Text("ইহসান ধাপে স্বাগতম 🌙", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text(
            "পাঁচ ওয়াক্ত এখন অভ্যাস — এবার রাতের নামাজ। রাতের শেষ তৃতীয়াংশে আল্লাহ বান্দাকে ডাকেন: কে আছ আমাকে ডাকবে, আমি সাড়া দেব (বুখারি ১১৪৫)।",
            fontSize = 14.sp, color = Ink, lineHeight = 21.sp,
        )
        Row(Modifier.padding(top = 6.dp)) {
            Button(onClick = {
                vm.prefs.routine = vm.prefs.routine + NotifKind.TAHAJJUD
                vm.settingsChanged()
            }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("তাহাজ্জুদের রিমাইন্ডার চালু") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { vm.prefs.tahajjudTipOff = true; vm.refresh() }) { Text("পরে", color = Muted) }
        }
    }
}
