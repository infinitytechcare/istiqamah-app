package com.istiqamah.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.audio.QuranAudio
import com.istiqamah.app.audio.Reciter
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.QuranGoal
import com.istiqamah.app.core.QuranProgress
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.QuranData
import com.istiqamah.app.data.QuranRepo
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** পুরো কুরআন একবার IO-তে লোড; লোড হওয়া পর্যন্ত null */
@Composable
private fun rememberQuran(): QuranData? {
    val ctx = LocalContext.current
    val s by produceState(QuranRepo.loaded(), Unit) { if (value == null) value = withContext(Dispatchers.IO) { QuranRepo.get(ctx) } }
    return s
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Emerald700)
    }
}

private data class QuranStats(val todayUnits: Int, val streak: Int, val perDay: Double, val eta: Int?)

// =====================================================================
// কুরআন ট্যাব — অগ্রগতি, লক্ষ্য, সূরা/পারা তালিকা
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuranHomeScreen(vm: AppVm, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val data = rememberQuran() ?: return Loading()
    val p = vm.prefs
    var refresh by remember { mutableIntStateOf(0) }
    val ver = vm.settingsVersion
    val today = Engine.today(ctx)
    val goal = p.quranGoal
    val stats by produceState<QuranStats?>(null, ver, refresh) {
        value = withContext(Dispatchers.IO) {
            val ev = LogStore.get(ctx).reads(today.minusDays(400), today)
            val rate = QuranProgress.pagesPerDay(ev, data.index, today)
            QuranStats(
                QuranProgress.unitsOn(today, ev, data.index, goal.unit),
                QuranProgress.streak(ev, data.index, goal, today),
                rate, QuranProgress.daysToKhatm(p.quranBookmark, data.index, rate),
            )
        }
    }
    var dialog by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val bm = p.quranBookmark

    LazyColumn(Modifier.fillMaxSize().background(Cream).statusBarsPadding().padding(horizontal = 16.dp)) {
        item {
            Text("কুরআন", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Emerald800, modifier = Modifier.padding(top = 16.dp))
            Text("প্রতিদিন একটু — ধীরে ধীরে খতম", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
        }
        item {
            SectionCard {
                Text("এখান থেকে পড়বেন", fontSize = 13.sp, color = Muted)
                Text(data.label(bm), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (data.index.pageOf(bm) - 1) / data.index.pageCount.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp), color = Gold, trackColor = Emerald50,
                )
                Text(
                    "খতমের ${Bn.num((data.index.pageOf(bm) - 1) * 100 / data.index.pageCount)}% · পূর্ণ খতম ${Bn.num(p.quranKhatm)} বার",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
                )
                stats?.let { s ->
                    val done = s.todayUnits >= goal.amount
                    Spacer(Modifier.height(10.dp))
                    Text(
                        (if (done) "✓ " else "") + "আজ ${Bn.num(s.todayUnits)}/${Bn.num(goal.amount)} ${goal.unit.bn}" +
                            if (s.streak > 0) " · টানা ${Bn.num(s.streak)} দিন" else "",
                        fontSize = 16.sp, color = if (done) Emerald700 else Ink, fontWeight = FontWeight.SemiBold,
                    )
                    s.eta?.let { Text("এই গতিতে (দিনে ${"%.1f".format(s.perDay)} পৃষ্ঠা) খতম হবে প্রায় ${Bn.num(it)} দিনে", fontSize = 13.sp, color = Muted) }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Button(onClick = { open("reader") }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("পড়া চালিয়ে যান") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { dialog = "offline" }) { Text("মুসহাফে পড়েছি", color = Emerald800) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            SectionCard {
                QTitle("লক্ষ্য ও রিমাইন্ডার")
                QItem("দৈনিক লক্ষ্য", goal.bn) { dialog = "goal" }
                QToggle("রিমাইন্ডার", p.quranAt?.let { Bn.time(it) } ?: "ফজরের পরে, সূর্য ওঠার আগে · না পড়লে ইশার পরে আরেকবার", p.quranOn) {
                    p.quranOn = it; vm.settingsChanged()
                }
                if (p.quranOn) QItem("রিমাইন্ডারের সময়", p.quranAt?.let { Bn.time(it) } ?: "ফজরের পরে") { dialog = "time" }
                QItem("তিলাওয়াত", Reciter.fromCode(p.reciter).bn) { dialog = "reciter" }
            }
            Spacer(Modifier.height(12.dp))
        }
        item {
            QTitle("পারা")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..30).forEach { j ->
                    FilterChip(
                        selected = data.index.juzOf(bm) == j, onClick = { open("reader:${data.index.pageOf(data.index.juzStart(j))}") },
                        label = { Text(Bn.num(j)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            QTitle("সূরা")
        }
        items(data.surahs, key = { it.no }) { s ->
            Row(
                Modifier.fillMaxWidth().clickable { open("reader:${data.index.pageOf(s.start)}") }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Bn.num(s.no), fontSize = 14.sp, color = Gold, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.nameBn, fontSize = 17.sp, color = Ink)
                    Text("${s.meaning} · ${Bn.num(s.count)} আয়াত · ${if (s.meccan) "মাক্কী" else "মাদানী"}", fontSize = 13.sp, color = Muted)
                }
                Text(s.nameAr, fontSize = 20.sp, color = Emerald800)
            }
            HorizontalDivider(color = Emerald50)
        }
        item {
            Text(data.attribution, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 16.dp))
        }
    }

    val close = { dialog = null }
    when (dialog) {
        "goal" -> QChoice("দৈনিক লক্ষ্য", QuranGoal.PRESETS.map { it.bn + if (it == QuranGoal(com.istiqamah.app.core.QuranUnit.PAGE, 20)) " (মাসে এক খতম)" else "" },
            QuranGoal.PRESETS.indexOf(goal), close) { i ->
            p.quranGoal = QuranGoal.PRESETS[i]
            scope.launch { withContext(Dispatchers.IO) { Engine.recheckQuranToday(ctx, data) }; vm.settingsChanged() }
        }
        "time" -> {
            val opts = listOf<LocalTime?>(null) + (5..23).map { LocalTime.of(it, 0) }
            QChoice("কখন মনে করাব?", opts.map { it?.let { t -> Bn.time(t) } ?: "ফজরের পরে (সূর্য ওঠার আগে)" }, opts.indexOf(p.quranAt).coerceAtLeast(0), close) {
                p.quranAt = opts[it]; vm.settingsChanged()
            }
        }
        "reciter" -> QChoice("কারি", Reciter.entries.map { it.bn }, p.reciter, close) { p.reciter = it }
        "offline" -> {
            val counts = listOf(1, 2, 3, 4, 5, 10, 20)
            QChoice("মুসহাফে আজ কত পৃষ্ঠা পড়েছেন?", counts.map { "${Bn.num(it)} পৃষ্ঠা (${data.label(bm)} থেকে)" }, -1, close) { i ->
                val toPage = data.index.pageOf(bm) + counts[i]
                val to = if (toPage > data.index.pageCount) com.istiqamah.app.core.QuranIndex.TOTAL + 1 else data.index.pageStart(toPage)
                scope.launch {
                    val khatm = withContext(Dispatchers.IO) { Engine.logQuranRead(ctx, data, bm, to, moveBookmark = true) }
                    Toast.makeText(ctx, if (khatm) "আলহামদুলিল্লাহ! খতম সম্পূর্ণ 🌙" else "লেখা হলো — আল্লাহ কবুল করুন", Toast.LENGTH_SHORT).show()
                    refresh++; vm.settingsChanged()
                }
            }
        }
    }
}

// =====================================================================
// পাঠক — পৃষ্ঠা ধরে আরবি + বাংলা, তিলাওয়াত, মুখস্থের রিপিট
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuranReaderScreen(vm: AppVm, startPage: Int?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data = rememberQuran() ?: return Loading()
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion // বুকমার্ক বদলালে আবার আঁকা
    var page by remember { mutableIntStateOf(startPage ?: data.index.pageOf(p.quranBookmark)) }
    var showBn by remember { mutableStateOf(p.quranShowBn) }
    var repeat by remember { mutableIntStateOf(p.quranRepeat) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val playing by QuranAudio.playing.collectAsState()
    val error by QuranAudio.error.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val range = data.index.pageRange(page)
    val ids = range.toList()

    BackHandler { QuranAudio.stop(); onBack() }
    DisposableEffect(Unit) { onDispose { QuranAudio.stop() } }
    LaunchedEffect(page) { listState.scrollToItem(0) }
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); QuranAudio.clearError() } }
    // বাজতে থাকা আয়াত চোখের সামনে রাখি; পৃষ্ঠা শেষ হলে পরের পৃষ্ঠায় যায় না (নিজে এগোবেন)
    LaunchedEffect(playing) { playing?.let { id -> ids.indexOf(id).takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) } } }

    fun play(from: Int, only: Boolean = false) {
        val list = if (only) listOf(from) else ids.filter { it >= from }
        QuranAudio.play(ctx, data.index, list, repeat, Reciter.fromCode(p.reciter))
    }

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        // ওপরের বার
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { QuranAudio.stop(); onBack() }) { Text("‹ ফিরে", color = Emerald800) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(data.surah(range.first).nameBn, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text("পৃষ্ঠা ${Bn.num(page)} · পারা ${Bn.num(data.index.juzOf(range.first))}", fontSize = 12.sp, color = Muted)
            }
            TextButton(onClick = { showBn = !showBn; p.quranShowBn = showBn }) { Text(if (showBn) "অর্থ লুকান" else "অর্থ দেখান", color = Emerald700, fontSize = 13.sp) }
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 14.dp), state = listState) {
            items(ids, key = { it }) { id ->
                val s = data.surah(id)
                val ayah = data.index.ayahNo(id)
                if (ayah == 1) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp).background(Emerald50, RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(s.nameAr, fontSize = 24.sp, color = Emerald800)
                        Text("সূরা ${s.nameBn} (${s.meaning}) · ${Bn.num(s.count)} আয়াত", fontSize = 13.sp, color = Muted)
                        if (s.no != 1 && s.no != 9) Text(data.ar(1), fontSize = 22.sp, color = Ink, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                val active = playing == id
                Column(
                    Modifier.fillMaxWidth()
                        .background(if (active) Emerald50 else Cream, RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = { menuFor = id }, onLongClick = { menuFor = id })
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(
                        "${data.ar(id)} ﴿${arabicNum(ayah)}﴾",
                        fontSize = 25.sp, lineHeight = 46.sp, color = Ink, modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Start),
                    )
                    if (id in data.index.sajda) Text("۩ সিজদার আয়াত", fontSize = 12.sp, color = Gold)
                    if (showBn) {
                        Text("${Bn.num(s.no)}:${Bn.num(ayah)} — ${data.bn(id)}", fontSize = 15.sp, lineHeight = 23.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                        if (data.hasBnIssue(id)) Text("এই আয়াতের ডিজিটাল অনুবাদে অংশ বাদ পড়েছে — মুদ্রিত তাফসির দেখুন।", fontSize = 11.sp, color = StatusQaza)
                    }
                    if (id == p.quranBookmark) Text("🔖 এখান থেকে পড়ার কথা", fontSize = 12.sp, color = Gold)
                }
                HorizontalDivider(color = Emerald50)
            }
            item {
                val bmPage = data.index.pageOf(p.quranBookmark)
                Button(
                    onClick = {
                        val to = range.last + 1
                        scope.launch {
                            val khatm = withContext(Dispatchers.IO) { Engine.logQuranRead(ctx, data, range.first, to, moveBookmark = page == bmPage) }
                            Toast.makeText(ctx, if (khatm) "আলহামদুলিল্লাহ! খতম সম্পূর্ণ 🌙" else "পৃষ্ঠা ${Bn.num(page)} পড়া হলো ✓", Toast.LENGTH_SHORT).show()
                            vm.settingsChanged()
                            if (page < data.index.pageCount) page++
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                ) { Text("এই পৃষ্ঠা পড়া শেষ ✓", fontSize = 17.sp) }
                if (page != bmPage) Text(
                    "বুকমার্কের পৃষ্ঠা নয় — আজকের লক্ষ্যে গোনা হবে, বুকমার্ক নড়বে না।", fontSize = 12.sp, color = Muted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        // নিচের বার: পৃষ্ঠা বদল, তিলাওয়াত, রিপিট
        Row(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = page > 1, onClick = { QuranAudio.stop(); page-- }) { Text("‹ আগের", color = Emerald800) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (playing != null) QuranAudio.stop() else play(range.first) }) {
                Text(if (playing != null) "■ থামান" else "▶ শুনুন", color = Gold, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {
                val opts = listOf(1, 3, 5, 10)
                repeat = opts[(opts.indexOf(repeat) + 1).mod(opts.size)]
                p.quranRepeat = repeat
            }) { Text("রিপিট ${Bn.num(repeat)}×", color = Emerald700, fontSize = 13.sp) }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = page < data.index.pageCount, onClick = { QuranAudio.stop(); page++ }) { Text("পরের ›", color = Emerald800) }
        }
    }

    menuFor?.let { id ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("${data.surah(id).nameBn} ${Bn.num(data.surah(id).no)}:${Bn.num(data.index.ayahNo(id))}") },
            text = {
                Column {
                    TextButton(onClick = { play(id); menuFor = null }) { Text("▶ এখান থেকে শুনুন") }
                    TextButton(onClick = { play(id, only = true); menuFor = null }) { Text("🔁 শুধু এই আয়াত (${Bn.num(repeat)} বার) — মুখস্থের জন্য") }
                    TextButton(onClick = {
                        p.quranBookmark = id; p.quranBookmarkLabel = data.label(id); menuFor = null
                        Toast.makeText(ctx, "বুকমার্ক রাখা হলো", Toast.LENGTH_SHORT).show(); vm.settingsChanged()
                    }) { Text("🔖 এখানে বুকমার্ক রাখুন") }
                }
            },
            confirmButton = { TextButton(onClick = { menuFor = null }) { Text("বন্ধ") } },
        )
    }
}

/** আরবি সংখ্যা ০–৯ → ٠–٩ */
private fun arabicNum(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

/** হোম স্ক্রিনের ছোট কার্ড — পুরো কুরআন লোড না করে, শুধু Prefs থেকে */
@Composable
fun QuranTodayCard(vm: AppVm, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val done = Engine.today(ctx) in p.quranDoneDates
    SectionCard(Modifier.clickable { open("reader") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (done) "📖 আজকের কুরআন ✓" else "📖 আজকের কুরআন — ${p.quranGoal.bn}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text(if (done) "আলহামদুলিল্লাহ — চাইলে আরও পড়ুন" else "এখান থেকে: ${p.quranBookmarkLabel}", fontSize = 14.sp, color = Muted)
            }
            Text("পড়ুন ›", fontSize = 14.sp, color = Emerald700)
        }
    }
}

@Composable
private fun QTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun QItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontSize = 17.sp, color = Ink)
        Text(subtitle, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun QToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Ink)
            Text(subtitle, fontSize = 14.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
    }
}

@Composable
private fun QChoice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options.size) { i ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(options[i])
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } },
    )
}
