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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.DawahPerson
import com.istiqamah.app.core.DawahStage
import com.istiqamah.app.core.HifzItem
import com.istiqamah.app.core.HifzState
import com.istiqamah.app.core.Ilm
import com.istiqamah.app.core.IlmSource
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.QuranData
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.notify.Notifier
import java.time.LocalDate
import java.time.LocalTime

// =====================================================================
// v0.12 — ইলম ও দাওয়াহ
// =====================================================================

/** মুখস্থ অংশের নাম — কুরআন লোড হলে সূরার বাংলা নাম */
private fun hifzTitle(h: HifzItem, data: QuranData?): String =
    if (h.key.startsWith("s") && data != null) "সূরা ${data.surahs[h.surah - 1].nameBn}" else h.title

private fun hifzPage(h: HifzItem, data: QuranData?): Int? = data?.index?.pageOf(data.index.idOf(h.surah, h.from))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IlmScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val today = Engine.today(ctx)
    val data = rememberQuran()
    val indoPak = p.quranScript == 0
    var log by remember { mutableStateOf(p.ilmLog) }
    var hifz by remember { mutableStateOf(p.hifz) }
    var showAll by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }

    fun saveHifz(m: Map<String, HifzState>) { hifz = m; p.hifz = m; vm.refresh() }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("ইলম ও দাওয়াহ", "শেখা, মনে রাখা, আর অন্যকে আল্লাহর দিকে ডাকা", onBack)
        SectionCard {
            listOf("h_ibnmajah_224", "h_muslim_2699").mapNotNull { ContentRepo.byId(ctx, it) }.forEach {
                Text("\"${it.shortBn}\" — ${it.refBn}", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(vertical = 2.dp))
            }
            TextButton(onClick = { showSources = !showSources }) { Text(if (showSources) "লুকান" else "ইলম নিয়ে কুরআন-হাদিস ›", color = Emerald700) }
        }
        if (showSources) {
            Spacer(Modifier.height(8.dp))
            Ilm.ILM_AYAT.forEach { AyahBlock(data, it, open, indoPak); Spacer(Modifier.height(8.dp)) }
            Ilm.ILM_HADITH.drop(2).forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- আজকের শেখা ----------
        SectionCard {
            Text("আজ কী শিখলেন? (১০ মিনিট হলেও)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(6.dp))
            val todaySet = log[today].orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IlmSource.entries.forEach { s ->
                    FilterChip(
                        selected = s in todaySet,
                        onClick = {
                            val ns = if (s in todaySet) todaySet - s else todaySet + s
                            val next = if (ns.isEmpty()) log - today else log + (today to ns)
                            log = next; p.ilmLog = next; vm.refresh()
                        },
                        label = { Text((if (s in todaySet) "✓ " else "") + s.bn) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            val streak = Ilm.streak(log.keys, today)
            Text(
                "গত ৩০ দিনে ${Bn.num(log.keys.count { !it.isBefore(today.minusDays(29)) })} দিন" + if (streak > 0) " · টানা ${Bn.num(streak)} দিন" else "",
                fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().clickable { timeDialog = true }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("প্রতিদিন মনে করানো", fontSize = 15.sp, color = Ink)
                    Text(p.ilmAt?.let { Bn.time(it) + " — আজ শেখা হয়ে গেলে আসবে না" } ?: "বন্ধ", fontSize = 13.sp, color = Muted)
                }
                Text("›", color = Muted)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- মুখস্থ ও রিভিশন ----------
        val due = Ilm.dueToday(hifz, today)
        SectionCard {
            Text("মুখস্থ ও রিভিশন", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("যা মুখস্থ আছে তা চিহ্ন দিন — ১, ৩, ৭, ১৪, ৩০, ৬০ দিন পরপর রিভিশন আসবে। ভুলে গেলে আবার শুরু থেকে।", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            if (due.isEmpty()) Text(
                if (hifz.isEmpty()) "নিচের তালিকা থেকে মুখস্থ সূরাগুলো চিহ্ন দিন।" else "আজ কোনো রিভিশন বাকি নেই ✓",
                fontSize = 14.sp, color = Emerald700, modifier = Modifier.padding(top = 6.dp),
            )
            due.mapNotNull { Ilm.byKey(it) }.forEach { h ->
                HorizontalDivider(color = Emerald50, modifier = Modifier.padding(vertical = 6.dp))
                Text(hifzTitle(h, data), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    hifzPage(h, data)?.let { pg -> TextButton(onClick = { open("reader:$pg") }) { Text("পড়ুন/শুনুন", color = Emerald700, fontSize = 13.sp) } }
                    Button(onClick = {
                        hifz[h.key]?.let { s -> saveHifz(hifz + (h.key to Ilm.review(s, true, today))) }
                        if (IlmSource.HIFZ !in log[today].orEmpty()) { val n = log + (today to (log[today].orEmpty() + IlmSource.HIFZ)); log = n; p.ilmLog = n }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("মনে আছে ✓", fontSize = 13.sp) }
                    OutlinedButton(onClick = { hifz[h.key]?.let { s -> saveHifz(hifz + (h.key to Ilm.review(s, false, today))) } }) {
                        Text("ভুলে গেছি", fontSize = 13.sp, color = Emerald800)
                    }
                }
            }
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "তালিকা লুকান" else "কোনগুলো মুখস্থ আছে? (${Bn.num(hifz.size)}/${Bn.num(Ilm.HIFZ.size)}) ›", color = Emerald700)
            }
            if (showAll) Ilm.HIFZ.forEach { h ->
                val s = hifz[h.key]
                Row(
                    Modifier.fillMaxWidth().clickable { saveHifz(if (s != null) hifz - h.key else hifz + (h.key to HifzState(0, today))) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = s != null, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text(hifzTitle(h, data), fontSize = 15.sp, color = Ink)
                        s?.let { Text("পরের রিভিশন: ${Bn.date(Ilm.nextDue(it))}", fontSize = 12.sp, color = Muted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- দাওয়াহ ----------
        DawahCard(vm, today, open, indoPak, data)
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("আজ ট্যাবে \"ইলম আজ\" কার্ড", fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                Switch(checked = p.ilmToday, onCheckedChange = { p.ilmToday = it; vm.settingsChanged() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("দ্বীনের মাসআলা শিখুন নির্ভরযোগ্য আলেম ও বই থেকে। অ্যাপের ব্যাখ্যা ও হাদিস আলেমের রিভিউয়ের অপেক্ষায়।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }

    if (timeDialog) {
        val opts = listOf<LocalTime?>(null) + (5..23).map { LocalTime.of(it, 0) } + LocalTime.of(21, 30)
        val sorted = listOf<LocalTime?>(null) + opts.filterNotNull().distinct().sorted()
        QChoice("কখন মনে করাব?", sorted.map { it?.let { t -> Bn.time(t) } ?: "বন্ধ" }, sorted.indexOf(p.ilmAt).coerceAtLeast(0), { timeDialog = false }) {
            p.ilmAt = sorted[it]; vm.settingsChanged()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DawahCard(vm: AppVm, today: LocalDate, open: (String) -> Unit, indoPak: Boolean, data: QuranData?) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var people by remember { mutableStateOf(p.dawah) }
    var edit by remember { mutableStateOf<DawahPerson?>(null) }
    var showSources by remember { mutableStateOf(false) }
    fun save(v: List<DawahPerson>) { people = v; p.dawah = v }

    SectionCard {
        Text("নামাজের দাওয়াত", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        ContentRepo.byId(ctx, "h_bukhari_2942")?.let { Text("\"${it.shortBn}\" — ${it.refBn}", fontSize = 14.sp, lineHeight = 21.sp, color = Ink) }
        Text("যাদের জন্য দোয়া করছেন ও নরম করে বলতে চান — নাম এনক্রিপ্ট করে শুধু ফোনে থাকে।", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
        people.forEach { d ->
            HorizontalDivider(color = Emerald50, modifier = Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = { edit = d }) { Text("বদলান", color = Muted, fontSize = 12.sp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DawahStage.entries.forEach { st ->
                    FilterChip(
                        selected = d.stage == st,
                        onClick = { save(people.map { if (it.id == d.id) it.copy(stage = st, last = today) else it }) },
                        label = { Text(st.bn, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            if (d.note.isNotBlank()) Text(d.note, fontSize = 12.sp, color = Muted)
        }
        Row(Modifier.padding(top = 6.dp)) {
            Button(onClick = { edit = DawahPerson(0, "", DawahStage.DUA, "", null) }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("+ যোগ করুন") }
        }
        Spacer(Modifier.height(8.dp))
        Text("দাওয়াতের আদব", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Gold)
        Ilm.DAWAH_TIPS.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
        vm.todayState?.daily?.let { item ->
            OutlinedButton(onClick = {
                ctx.startActivity(Notifier.shareIntent(Ilm.shareText(item.shortBn, item.refBn)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }, modifier = Modifier.padding(top = 8.dp)) { Text("আজকের ${if (item.type == "hadith") "হাদিস" else "আয়াত"} শেয়ার করুন", color = Emerald800) }
        }
        TextButton(onClick = { showSources = !showSources }) { Text(if (showSources) "লুকান" else "দাওয়াহ নিয়ে কুরআন-হাদিস ›", color = Emerald700) }
    }
    if (showSources) {
        Spacer(Modifier.height(8.dp))
        Ilm.DAWAH_AYAT.forEach { AyahBlock(data, it, open, indoPak); Spacer(Modifier.height(8.dp)) }
        Ilm.DAWAH_HADITH.forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
    }

    edit?.let { e ->
        var name by remember(e.id) { mutableStateOf(e.name) }
        var note by remember(e.id) { mutableStateOf(e.note) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (e.id == 0L) "কার জন্য?" else "বদলান") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(30) }, label = { Text("নাম বা ডাকনাম") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(note, { note = it.take(80) }, label = { Text("নোট (ঐচ্ছিক) — যেমন কোন ওয়াক্তে সুবিধা") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (e.id != 0L) TextButton(onClick = { save(people.filter { it.id != e.id }); edit = null }) { Text("মুছে ফেলুন", color = StatusQaza) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    save(if (e.id == 0L) people + e.copy(id = (people.maxOfOrNull { it.id } ?: 0) + 1, name = name.trim(), note = note.trim(), last = today)
                    else people.map { if (it.id == e.id) it.copy(name = name.trim(), note = note.trim()) else it })
                    edit = null
                }) { Text("রাখুন") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("বাতিল") } },
        )
    }
}

// =====================================================================
// আজ ট্যাবের কার্ড — "ইলম আজ"
// =====================================================================

@Composable
fun IlmTodayCard(due: List<String>, learned: Boolean, open: (String) -> Unit) {
    val data = rememberQuranIfLoaded()
    Card16(Modifier.clickable { open("ilm") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (learned) "📚 ইলম আজ ✓" else "📚 ইলম আজ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text(
                    when {
                        due.isNotEmpty() -> "রিভিশন বাকি: " + due.mapNotNull { Ilm.byKey(it) }.take(3).joinToString(", ") { hifzTitle(it, data) } +
                            if (due.size > 3) " +${Bn.num(due.size - 3)}" else ""
                        learned -> "আজ শেখা হয়েছে — আলহামদুলিল্লাহ"
                        else -> "আজ ১০ মিনিট দ্বীন শিখুন"
                    },
                    fontSize = 14.sp, color = Muted,
                )
            }
            Text("›", color = Muted)
        }
    }
}

/** কুরআন আগে থেকে লোড থাকলে নাম দেখাই, না থাকলে আজ ট্যাবের জন্য ৩.৪ MB লোড করি না */
@Composable
private fun rememberQuranIfLoaded(): QuranData? = remember { com.istiqamah.app.data.QuranRepo.loaded() }
