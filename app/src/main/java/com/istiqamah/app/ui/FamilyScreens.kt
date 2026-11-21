package com.istiqamah.app.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Child
import com.istiqamah.app.core.Family
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Ihsan
import com.istiqamah.app.core.Marital
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.engine.Engine
import java.time.DayOfWeek
import java.time.YearMonth

// =====================================================================
// v0.11 — পরিবার
// =====================================================================

private val HALAQA_DAYS = listOf(
    DayOfWeek.FRIDAY to "শুক্র", DayOfWeek.SATURDAY to "শনি", DayOfWeek.SUNDAY to "রবি", DayOfWeek.MONDAY to "সোম",
    DayOfWeek.TUESDAY to "মঙ্গল", DayOfWeek.WEDNESDAY to "বুধ", DayOfWeek.THURSDAY to "বৃহস্পতি",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val today = Engine.today(ctx)
    val data = rememberQuran()
    val indoPak = p.quranScript == 0
    var marital by remember { mutableStateOf(p.marital) }
    var kids by remember { mutableStateOf(p.children) }
    var editKid by remember { mutableStateOf<Child?>(null) }
    var showSources by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("পরিবার", "প্রত্যেকে দায়িত্বশীল — পরিবার সম্পর্কে জিজ্ঞেস করা হবে (বুখারি ৮৯৩)", onBack)

        // ---------- অবস্থা ----------
        SectionCard {
            Text("আপনি কি বিবাহিত?", fontSize = 15.sp, color = Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Marital.MARRIED, Marital.SINGLE).forEach { m ->
                    FilterChip(
                        selected = marital == m, onClick = { marital = m; p.marital = m; vm.refresh() }, label = { Text(m.bn) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            Text("শুধু আপনার ফোনে থাকে — কোন অংশ দেখাব তা ঠিক করতে।", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(12.dp))

        // ---------- স্বামী-স্ত্রী ----------
        if (marital == Marital.MARRIED) {
            val g = p.gender
            SectionCard {
                Text(when (g) { Gender.FEMALE -> "স্বামীর হক ও ভালোবাসা"; Gender.MALE -> "স্ত্রীর হক ও ভালোবাসা"; else -> "স্বামী-স্ত্রীর হক" },
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text("আজ: ${Family.spouseActFor(g, today)}", fontSize = 16.sp, lineHeight = 24.sp, color = Emerald700, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(6.dp))
                Family.spouseActs(g).forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
                TextButton(onClick = { showSources = !showSources }) { Text(if (showSources) "কুরআন-হাদিস লুকান" else "কুরআন-হাদিস দেখুন ›", color = Emerald700) }
            }
            if (showSources) {
                Spacer(Modifier.height(8.dp))
                Family.SPOUSE_AYAT.forEach { AyahBlock(data, it, open, indoPak); Spacer(Modifier.height(8.dp)) }
                Family.SPOUSE_HADITH.forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
            }
            Spacer(Modifier.height(12.dp))
        } else if (marital == Marital.SINGLE) {
            SectionCard {
                Text("বিয়ের প্রস্তুতি", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                listOf(
                    "জীবনসঙ্গী বাছাইয়ে দ্বীনদারিকে প্রাধান্য দিন (বুখারি ৫০৯০)",
                    "বাবা-মা ও অভিভাবকের সাথে পরামর্শ, সিদ্ধান্তের আগে ইস্তিখারা",
                    "এখন থেকেই নামাজ, হালাল উপার্জন আর চরিত্র গড়া — ভবিষ্যৎ পরিবারের ভিত",
                    "দৃষ্টির হেফাজত; সামর্থ্য না হলে রোজা (বুখারি ৫০৬৫)",
                ).forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- সন্তান ----------
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("সন্তানের তারবিয়াহ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800, modifier = Modifier.weight(1f))
                TextButton(onClick = { editKid = Child(0, "", YearMonth.from(today).minusYears(5)) }) { Text("+ যোগ", color = Emerald700) }
            }
            Text("বয়স অনুযায়ী কী শেখাবেন — নাম ও জন্মমাস এনক্রিপ্ট করে শুধু ফোনে থাকে।", fontSize = 13.sp, color = Muted)
            if (kids.isEmpty()) Text("সন্তান যোগ করলে তার বয়সের ধাপ আর কাজের তালিকা দেখাবে।", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        kids.sortedBy { it.born }.forEach { c ->
            Spacer(Modifier.height(8.dp))
            ChildCard(vm, c, today) { editKid = c }
        }
        Spacer(Modifier.height(8.dp))
        if (kids.isNotEmpty()) {
            Family.FAMILY_HADITH.drop(1).forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
        }
        Spacer(Modifier.height(4.dp))

        // ---------- পারিবারিক হালাকা ----------
        HalaqaCard(vm, today, open, indoPak)
        Spacer(Modifier.height(12.dp))

        SectionCard {
            HadithBlock("h_bukhari_893")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("আজ ট্যাবে \"পরিবার আজ\" কার্ড", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = p.familyToday, onCheckedChange = { p.familyToday = it; vm.settingsChanged() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("ব্যাখ্যা ও হাদিস আলেমের রিভিউয়ের অপেক্ষায়। পারিবারিক বিরোধ বা জটিল মাসআলায় আলেম বা পারিবারিক কাউন্সেলরের সাহায্য নিন।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }

    editKid?.let { c ->
        ChildDialog(
            c, onDismiss = { editKid = null },
            onSave = { n ->
                val next = if (c.id == 0L) kids + n.copy(id = (kids.maxOfOrNull { it.id } ?: 0) + 1) else kids.map { if (it.id == c.id) n else it }
                kids = next; p.children = next; editKid = null; vm.refresh()
            },
            onDelete = if (c.id != 0L) ({ val next = kids.filter { it.id != c.id }; kids = next; p.children = next; editKid = null; vm.refresh() }) else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildCard(vm: AppVm, c: Child, today: java.time.LocalDate, onEdit: () -> Unit) {
    val p = vm.prefs
    val age = Family.age(c, today)
    val stage = Family.stageFor(age)
    var done by remember { mutableStateOf(p.childDone) }
    var salah by remember(c.id) { mutableStateOf(p.childSalah(c.id)) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
                Text("${Bn.num(age)} বছর · ${stage.title}", fontSize = 13.sp, color = Muted)
            }
            TextButton(onClick = onEdit) { Text("বদলান", color = Muted, fontSize = 13.sp) }
        }
        stage.items.forEachIndexed { i, item ->
            val key = "${c.id}|${stage.fromAge}|$i"
            Row(
                Modifier.fillMaxWidth().clickable {
                    done = if (key in done) done - key else done + key
                    p.childDone = done
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = key in done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                Text(item, fontSize = 14.sp, lineHeight = 20.sp, color = Ink, modifier = Modifier.padding(start = 6.dp))
            }
        }
        if (Family.prays(c, today)) {
            HorizontalDivider(color = Emerald50, modifier = Modifier.padding(vertical = 6.dp))
            Text("আজ কয় ওয়াক্ত পড়েছে?", fontSize = 14.sp, color = Ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..5).forEach { n ->
                    FilterChip(
                        selected = salah[today] == n,
                        onClick = { p.setChildSalah(c.id, today, n); salah = p.childSalah(c.id); vm.refresh() },
                        label = { Text(Bn.num(n)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            Family.salahAverage(salah, today, 7)?.let {
                Text("গত ৭ দিনে গড়ে দিনে ${"%.1f".format(it)} ওয়াক্ত — পড়লে প্রশংসা করুন, ছুটলে পরের ওয়াক্তে সাথে নিন।", fontSize = 12.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun ChildDialog(c: Child, onDismiss: () -> Unit, onSave: (Child) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(c.name) }
    var year by remember { mutableStateOf(c.born.year.toString()) }
    var month by remember { mutableStateOf(c.born.monthValue.toString()) }
    fun ascii(s: String) = s.map { ch -> if (ch in '০'..'৯') '0' + (ch - '০') else ch }.filter { it.isDigit() }.joinToString("")
    val y = year.toIntOrNull()
    val m = month.toIntOrNull()
    val ok = name.isNotBlank() && y != null && y in 1950..2100 && m != null && m in 1..12
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (c.id == 0L) "সন্তান যোগ" else "বদলান") },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("নাম বা ডাকনাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    OutlinedTextField(year, { year = ascii(it).take(4) }, label = { Text("জন্মসাল") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(month, { month = ascii(it).take(2) }, label = { Text("মাস (১–১২)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                onDelete?.let { d -> TextButton(onClick = d) { Text("মুছে ফেলুন", color = StatusQaza) } }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onSave(c.copy(name = name.trim(), born = YearMonth.of(y!!, m!!))) }) { Text("রাখুন") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HalaqaCard(vm: AppVm, today: java.time.LocalDate, open: (String) -> Unit, indoPak: Boolean) {
    val p = vm.prefs
    val data = rememberQuran()
    val week = Ihsan.weekKey(today)
    val h = Family.halaqaFor(today)
    var day by remember { mutableStateOf(p.halaqaDay) }
    var doneWeeks by remember { mutableStateOf(p.halaqaDone) }
    SectionCard {
        Text("পারিবারিক হালাকা — সপ্তাহে ১৫ মিনিট", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("সবাই মিলে বসে: একটা আয়াত, একটা হাদিস, একটা প্রশ্ন, একটা দোয়া। প্রতি সোমবার নতুন বিষয়।", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
        Text("এ সপ্তাহ: ${h.title}", fontSize = 17.sp, color = Gold, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text("১. আয়াত", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald800)
    AyahBlock(data, h.ayah, open, indoPak)
    Spacer(Modifier.height(8.dp))
    Text("২. হাদিস", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald800)
    HadithBlock(h.hadith)
    Spacer(Modifier.height(8.dp))
    SectionCard {
        Text("৩. আলোচনার প্রশ্ন", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text(h.question, fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
        Text("সবার কথা শুনুন — ছোটদেরটা আগে।", fontSize = 12.sp, color = Muted)
    }
    Spacer(Modifier.height(8.dp))
    Text("৪. একসাথে মুখস্থের দোয়া", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald800)
    AyahBlock(data, h.dua, open, indoPak)
    Spacer(Modifier.height(8.dp))
    SectionCard {
        val thisDone = week in doneWeeks
        Button(
            onClick = { doneWeeks = if (thisDone) doneWeeks - week else doneWeeks + week; p.halaqaDone = doneWeeks; vm.refresh() },
            colors = ButtonDefaults.buttonColors(containerColor = if (thisDone) Emerald50 else Emerald700),
        ) { Text(if (thisDone) "✓ এ সপ্তাহের হালাকা হয়েছে" else "এ সপ্তাহের হালাকা হয়েছে", color = if (thisDone) Emerald800 else androidx.compose.ui.graphics.Color.White) }
        Text("গত ৪ সপ্তাহে ${Bn.num(Family.halaqaPercent(doneWeeks, today) / 25)} বার", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        Text("কোন দিন মনে করাব? (ইশার ২০ মিনিট পরে)", fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(selected = day == null, onClick = { day = null; p.halaqaDay = null; vm.settingsChanged() }, label = { Text("বন্ধ") })
            HALAQA_DAYS.forEach { (d, n) ->
                FilterChip(
                    selected = day == d, onClick = { day = d; p.halaqaDay = d; vm.settingsChanged() }, label = { Text(n) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                )
            }
        }
    }
}

// =====================================================================
// আজ ট্যাবের কার্ড — "পরিবার আজ"
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FamilyTodayCard(vm: AppVm, spouseAct: String?, kids: List<Pair<Child, Int?>>, open: (String) -> Unit) {
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("পরিবার আজ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800, modifier = Modifier.weight(1f))
            TextButton(onClick = { open("family") }) { Text("খুলুন ›", color = Emerald700, fontSize = 13.sp) }
        }
        spouseAct?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp, color = Ink) }
        kids.forEach { (c, n) ->
            Text("${c.name} আজ কয় ওয়াক্ত পড়েছে?", fontSize = 14.sp, color = Ink, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0..5).forEach { k ->
                    FilterChip(
                        selected = n == k, onClick = { vm.setKidSalah(c, k) }, label = { Text(Bn.num(k)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
        }
    }
}
