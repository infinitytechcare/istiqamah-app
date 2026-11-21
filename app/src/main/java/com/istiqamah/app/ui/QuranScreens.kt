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
internal fun rememberQuran(): QuranData? {
    val ctx = LocalContext.current
    val s by produceState(QuranRepo.loaded(), Unit) { if (value == null) value = withContext(Dispatchers.IO) { QuranRepo.get(ctx) } }
    return s
}

@Composable
internal fun Loading() {
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
internal fun QChoice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
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
