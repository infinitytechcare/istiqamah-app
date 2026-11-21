package com.istiqamah.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.istiqamah.app.audio.QuranAudio
import com.istiqamah.app.audio.Reciter
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.MushafLayout
import com.istiqamah.app.core.QuranScript
import com.istiqamah.app.data.QuranData
import com.istiqamah.app.data.SurahInfo
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// =====================================================================
// কুরআন পাঠক (v0.9) — দুই ধরন:
//   ০) আয়াত-আয়াত: নকশা করা নম্বর, ডানে আরবি, নিচে বাংলা অর্থ, সূরার নকশা-ব্যানার, সূরার ট্যাব
//   ১) শুধু আরবি: মুসহাফের মতো লাইন ভরাট করে, লাইনের নিচে দাগ, সবুজ সূরা-শিরোনাম
// পাতা ডান থেকে বামে উল্টায় (আসল মুসহাফের মতো) — পরের পাতা বাম দিকে।
// =====================================================================

private val ReaderBar = Color(0xFF234E4F)
private val ReaderBarSub = Color(0xFFC9DCD8)
private val Emblem = Color(0xFF2E8B7A)
private val TabGold = Color(0xFFE6BC72)
private val TabIdle = Color(0xFFB7C8C5)
private val CardCream = Color(0xFFF8F6EE)
private val CardActive = Color(0xFFE2F1EB)
private val Tan = Color(0xFFCBB089)
private val TanDark = Color(0xFFA88D62)
private val BannerBg = Color(0xFFEADFC6)
private val BannerLine = Color(0xFFDCCBA8)
private val PillBg = Color(0xFFF3EBD8)
private val ArInk = Color(0xFF1B1B1B)
private val BnTeal = Color(0xFF2B6C70)
private val StripCream = Color(0xFFF7F1E2)
private val MushafGreenBg = Color(0xFFDCEFE1)
private val MushafGreen = Color(0xFF7FD39A)
private val RuleGray = Color(0xFFD3D3D3)
private val PlayingInk = Color(0xFF117A65)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuranReaderScreen(vm: AppVm, startPage: Int?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val data = rememberQuran() ?: return Loading()
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion // বুকমার্ক বদলালে আবার আঁকা
    var mode by remember { mutableIntStateOf(p.quranMode) }
    var showBn by remember { mutableStateOf(p.quranShowBn) }
    var arSize by remember { mutableIntStateOf(p.quranArSize) }
    var bnSize by remember { mutableIntStateOf(p.quranBnSize) }
    var bold by remember { mutableStateOf(p.quranBold) }
    var script by remember { mutableIntStateOf(p.quranScript) }
    val indoPak = script == 0
    var repeat by remember { mutableIntStateOf(p.quranRepeat) }
    var settings by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val pageCount = data.index.pageCount
    val pager = rememberPagerState(initialPage = ((startPage ?: data.index.pageOf(p.quranBookmark)).coerceIn(1, pageCount)) - 1) { pageCount }
    val page = pager.currentPage + 1
    val range = data.index.pageRange(page)
    val playing by QuranAudio.playing.collectAsState()
    val error by QuranAudio.error.collectAsState()
    val scope = rememberCoroutineScope()

    BackHandler { QuranAudio.stop(); onBack() }
    DisposableEffect(Unit) { onDispose { QuranAudio.stop() } }
    LaunchedEffect(error) { error?.let { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show(); QuranAudio.clearError() } }
    // অন্য পাতায় চলে গেলে আগের পাতার তিলাওয়াত থামাই
    LaunchedEffect(pager.settledPage) {
        val r = data.index.pageRange(pager.settledPage + 1)
        if (QuranAudio.playing.value?.let { it !in r } == true) QuranAudio.stop()
    }

    fun play(from: Int, only: Boolean = false) {
        val list = if (only) listOf(from) else range.filter { it >= from }
        QuranAudio.play(ctx, data.index, list, repeat, Reciter.fromCode(p.reciter))
    }

    fun markPageRead(pg: Int) {
        val r = data.index.pageRange(pg)
        val bmPage = data.index.pageOf(p.quranBookmark)
        scope.launch {
            val khatm = withContext(Dispatchers.IO) { Engine.logQuranRead(ctx, data, r.first, r.last + 1, moveBookmark = pg == bmPage) }
            Toast.makeText(ctx, if (khatm) "আলহামদুলিল্লাহ! খতম সম্পূর্ণ 🌙" else "পৃষ্ঠা ${Bn.num(pg)} পড়া হলো ✓", Toast.LENGTH_SHORT).show()
            vm.settingsChanged()
            if (pg < pageCount) pager.animateScrollToPage(pg) // সূচি pg মানে পরের পাতা
        }
    }

    val arStyle = TextStyle(
        fontFamily = if (indoPak) IndoPak else AmiriQuran, fontSize = arSize.sp, color = ArInk,
        // একটিই ওজন — SemiBold চাইলে অ্যান্ড্রয়েড মোটা করে আঁকে; ইন্দোপাক ফন্ট এমনিতেই ভরাট
        fontWeight = if (bold && !indoPak) FontWeight.SemiBold else FontWeight.Normal,
        textDirection = TextDirection.Rtl,
    )

    Column(Modifier.fillMaxSize().background(Color.White).navigationBarsPadding()) {
        // ---------- ওপরের গাঢ় বার ----------
        Column(Modifier.fillMaxWidth().background(ReaderBar).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { QuranAudio.stop(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "ফিরে যান", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(Modifier.size(42.dp).background(Emblem, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color(0xFFE3F2EC), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f).clickable { goTo = true }.padding(start = 14.dp)) {
                    Text("পৃষ্ঠা ${Bn.num(page)}", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    if (mode == 0) Text("পারা ${Bn.num(data.index.juzOf(range.first))}", fontSize = 14.sp, color = ReaderBarSub)
                }
                IconButton(onClick = { if (playing != null) QuranAudio.stop() else play(range.first) }) {
                    Icon(if (playing != null) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing != null) "থামান" else "তিলাওয়াত শুনুন", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { mode = 1 - mode; p.quranMode = mode }) {
                    Icon(
                        if (mode == 0) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Filled.FormatListBulleted,
                        if (mode == 0) "শুধু আরবি (মুসহাফ)" else "অর্থসহ", tint = Color.White, modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = { settings = true }) { Icon(Icons.Filled.Settings, "সেটিংস", tint = Color.White, modifier = Modifier.size(26.dp)) }
            }
            if (mode == 0) SurahTabs(data, data.index.surahOf(range.first)) { s ->
                scope.launch { pager.scrollToPage(data.index.pageOf(s.start) - 1) }
            }
        }
        if (mode == 1) {
            val s = data.surah(range.first)
            Row(Modifier.fillMaxWidth().background(StripCream).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("সূরা ${Bn.num(s.no)}: ${s.nameBn}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TanDark)
                Spacer(Modifier.weight(1f))
                if (data.index.pageOf(p.quranBookmark) == page) Text("🔖  ", fontSize = 14.sp)
                Text("পারা ${Bn.num(data.index.juzOf(range.first))}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TanDark)
            }
        }

        // ---------- পাতা: ডান থেকে বামে ----------
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), key = { it }) { idx ->
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val pg = idx + 1
                    val footer: @Composable () -> Unit = {
                        PageFooter(pg, pageCount, isBookmarkPage = data.index.pageOf(p.quranBookmark) == pg,
                            onDone = { markPageRead(pg) },
                            onPrev = { scope.launch { pager.animateScrollToPage(idx - 1) } },
                            onNext = { scope.launch { pager.animateScrollToPage(idx + 1) } })
                    }
                    if (mode == 0) TranslationPage(data, pg, indoPak, arStyle, bnSize, showBn, playing, p.quranBookmark, { menuFor = it }, footer)
                    else MushafPage(data, pg, indoPak, arStyle, playing, { menuFor = it }, footer)
                }
            }
        }
    }

    // ---------- আয়াতের মেনু ----------
    menuFor?.let { id ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text("${data.surah(id).nameBn} ${Bn.num(data.surah(id).no)}:${Bn.num(data.index.ayahNo(id))}") },
            text = {
                Column {
                    if (mode == 1) Text(data.bn(id), fontSize = 15.sp, color = BnTeal, modifier = Modifier.padding(bottom = 8.dp))
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

    // ---------- পৃষ্ঠায় যান ----------
    if (goTo) {
        var target by remember { mutableFloatStateOf(page.toFloat()) }
        val t = target.roundToInt().coerceIn(1, pageCount)
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text("কোন পৃষ্ঠায় যাবেন?") },
            text = {
                Column {
                    Text("পৃষ্ঠা ${Bn.num(t)} · ${data.surah(data.index.pageStart(t)).nameBn} · পারা ${Bn.num(data.index.juzOf(data.index.pageStart(t)))}", fontSize = 16.sp, color = Ink)
                    Slider(value = target, onValueChange = { target = it }, valueRange = 1f..pageCount.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Emerald700, activeTrackColor = Emerald700))
                    TextButton(onClick = { target = data.index.pageOf(p.quranBookmark).toFloat() }) { Text("🔖 বুকমার্কের পৃষ্ঠা") }
                }
            },
            confirmButton = { TextButton(onClick = { goTo = false; scope.launch { pager.scrollToPage(t - 1) } }) { Text("যান") } },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text("বাতিল") } },
        )
    }

    // ---------- সেটিংস ----------
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("পড়ার সেটিংস") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf("আয়াত-আয়াত, বাংলা অর্থসহ", "শুধু আরবি — মুসহাফের মতো").forEachIndexed { i, label ->
                        Row(Modifier.fillMaxWidth().clickable { mode = i; p.quranMode = i }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == i, onClick = { mode = i; p.quranMode = i })
                            Text(label, fontSize = 15.sp)
                        }
                    }
                    Text("লিপি", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    listOf("পাক-ভারত (ইন্দোপাক) — বাংলাদেশে প্রচলিত", "মাদানি (উসমানি) — সৌদি মুসহাফ").forEachIndexed { i, label ->
                        Row(Modifier.fillMaxWidth().clickable { script = i; p.quranScript = i }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = script == i, onClick = { script = i; p.quranScript = i })
                            Text(label, fontSize = 15.sp)
                        }
                    }
                    if (mode == 0) SettingSwitch("বাংলা অর্থ দেখান", showBn) { showBn = it; p.quranShowBn = it }
                    if (!indoPak) SettingSwitch("আরবি হরফ মোটা", bold) { bold = it; p.quranBold = it }
                    Text("আরবির আকার: ${Bn.num(arSize)}", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    Slider(value = arSize.toFloat(), onValueChange = { arSize = it.roundToInt(); p.quranArSize = arSize }, valueRange = 20f..44f,
                        colors = SliderDefaults.colors(thumbColor = Emerald700, activeTrackColor = Emerald700))
                    Text(data.bismillah(indoPak), style = arStyle, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    if (mode == 0) {
                        Text("বাংলার আকার: ${Bn.num(bnSize)}", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                        Slider(value = bnSize.toFloat(), onValueChange = { bnSize = it.roundToInt(); p.quranBnSize = bnSize }, valueRange = 13f..26f,
                            colors = SliderDefaults.colors(thumbColor = Emerald700, activeTrackColor = Emerald700))
                    }
                    Text("মুখস্থের রিপিট (প্রতি আয়াত)", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 5, 10).forEach { n ->
                            FilterChip(selected = repeat == n, onClick = { repeat = n; p.quranRepeat = n }, label = { Text("${Bn.num(n)}×") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800))
                        }
                    }
                    Text("কারি: ${Reciter.fromCode(p.reciter).bn} (কুরআন ট্যাব থেকে বদলাবেন)", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { settings = false }) { Text("ঠিক আছে") } },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
    }
}

// =====================================================================
// সূরার ট্যাব — ডান থেকে বামে ১, ২, ৩ …
// =====================================================================

@Composable
private fun SurahTabs(data: QuranData, current: Int, onPick: (SurahInfo) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
    LaunchedEffect(current) { state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyRow(state = state, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            itemsIndexed(data.surahs, key = { _, s -> s.no }) { _, s ->
                val sel = s.no == current
                Column(
                    Modifier.width(IntrinsicSize.Max).clickable { onPick(s) }.padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "${Bn.num(s.no)}. ${s.nameBn}", fontSize = 16.sp, maxLines = 1,
                        color = if (sel) TabGold else TabIdle, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 10.dp, bottom = 10.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(3.dp).background(if (sel) TabGold else Color.Transparent))
                }
            }
        }
    }
}

// =====================================================================
// ধরন ০: আয়াত-আয়াত, বাংলা অর্থসহ
// =====================================================================

private sealed interface Row0 {
    data class Banner(val s: SurahInfo) : Row0
    data class Bismillah(val s: SurahInfo) : Row0
    data class Ayah(val id: Int) : Row0
    data object Footer : Row0
}

@Composable
private fun TranslationPage(
    data: QuranData, pg: Int, indoPak: Boolean, arStyle: TextStyle, bnSize: Int, showBn: Boolean, playing: Int?, bookmark: Int,
    onTap: (Int) -> Unit, footer: @Composable () -> Unit,
) {
    val rows = remember(pg) {
        buildList {
            for (id in data.index.pageRange(pg)) {
                val s = data.surah(id)
                if (data.index.ayahNo(id) == 1) {
                    add(Row0.Banner(s))
                    if (s.no != 1 && s.no != 9) add(Row0.Bismillah(s))
                }
                add(Row0.Ayah(id))
            }
            add(Row0.Footer)
        }
    }
    val listState = rememberLazyListState()
    // বাজতে থাকা আয়াত চোখের সামনে
    LaunchedEffect(playing) {
        playing?.let { id -> rows.indexOfFirst { it is Row0.Ayah && it.id == id }.takeIf { it >= 0 }?.let { listState.animateScrollToItem(it) } }
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.White), state = listState) {
        itemsIndexed(rows, key = { _, r -> r.toString() }) { _, r ->
            when (r) {
                is Row0.Banner -> SurahBanner(r.s)
                is Row0.Bismillah -> Text(
                    data.bismillah(indoPak), style = arStyle.copy(lineHeight = arStyle.fontSize * 2f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 14.dp, horizontal = 12.dp),
                )
                is Row0.Ayah -> AyahCard(data, r.id, indoPak, arStyle, bnSize, showBn, playing == r.id, r.id == bookmark) { onTap(r.id) }
                Row0.Footer -> footer()
            }
        }
    }
}

@Composable
private fun AyahCard(
    data: QuranData, id: Int, indoPak: Boolean, arStyle: TextStyle, bnSize: Int, showBn: Boolean, active: Boolean, isBookmark: Boolean, onTap: () -> Unit,
) {
    val ayah = data.index.ayahNo(id)
    val bg = when {
        active -> CardActive
        ayah % 2 == 1 -> CardCream
        else -> Color.White
    }
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onTap).padding(start = 8.dp, end = 16.dp, top = 18.dp, bottom = 22.dp),
    ) {
        AyahBadge(ayah, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val ink = if (active) PlayingInk else ArInk
            // ইন্দোপাক পাঠে আয়াত-নম্বরের গোল চিহ্ন ফন্টেই আছে
            if (indoPak) Text(
                data.ip(id), style = arStyle.copy(color = ink, lineHeight = arStyle.fontSize * 2.0f),
                textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
            ) else ArabicWithEnd(data.ar(id), ayah, arStyle, ink)
            if (id in data.index.sajda) Text("۩ সিজদার আয়াত", fontSize = 13.sp, color = TanDark)
            if (showBn) {
                Spacer(Modifier.height(10.dp))
                Text(data.bn(id), fontSize = bnSize.sp, lineHeight = (bnSize * 1.6f).sp, color = BnTeal)
                if (data.hasBnIssue(id)) Text("এই আয়াতের ডিজিটাল অনুবাদে অংশ বাদ পড়েছে — মুদ্রিত তাফসির দেখুন।", fontSize = 12.sp, color = StatusQaza)
            }
            if (isBookmark) Text("🔖 এখান থেকে পড়ার কথা", fontSize = 13.sp, color = TanDark, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** আরবি আয়াত, শেষে গোল দাগের ভেতরে আরবি নম্বর */
@Composable
private fun ArabicWithEnd(ar: String, ayah: Int, style: TextStyle, ink: Color) {
    val wide = ayah >= 100
    val text = remember(ar, ayah) {
        buildAnnotatedString {
            append(ar)
            append(" ")
            appendInlineContent("end", "(${ayah})")
        }
    }
    val inline = remember(ayah, style.fontSize, ink) {
        mapOf(
            "end" to InlineTextContent(Placeholder(if (wide) 1.55.em else 1.2.em, 1.2.em, PlaceholderVerticalAlign.Center)) {
                AyahEndMark(ayah, style, ink)
            },
        )
    }
    Text(
        text, inlineContent = inline, style = style.copy(color = ink, lineHeight = style.fontSize * 2.0f),
        textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AyahEndMark(ayah: Int, style: TextStyle, ink: Color) {
    Box(
        Modifier.fillMaxSize().border(BorderStroke(1.5.dp, ink), RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            QuranScript.digits(ayah), fontFamily = AmiriQuran, color = ink, maxLines = 1,
            fontSize = style.fontSize * 0.55f, lineHeight = style.fontSize * 0.65f,
        )
    }
}

/** বামের নকশা-করা আয়াত নম্বর — ১৬ কোনার তারকা-ফুল */
@Composable
private fun AyahBadge(n: Int, modifier: Modifier = Modifier) {
    Box(modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val rOut = size.minDimension / 2 - 2.dp.toPx()
            val rIn = rOut * 0.84f
            val path = Path()
            val pts = 16
            for (k in 0..pts) {
                val a = -PI / 2 + k * PI / (pts / 2)
                val r = if (k % 2 == 0) rOut else rIn
                val x = c.x + (r * cos(a)).toFloat()
                val y = c.y + (r * sin(a)).toFloat()
                if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Tan, style = Stroke(1.6.dp.toPx(), join = StrokeJoin.Round))
            // ওপরে-নিচে ছোট কুঁড়ি
            drawCircle(Tan, 1.8.dp.toPx(), Offset(c.x, c.y - rOut - 0.5.dp.toPx()))
            drawCircle(Tan, 1.8.dp.toPx(), Offset(c.x, c.y + rOut + 0.5.dp.toPx()))
        }
        Text(Bn.num(n), fontSize = if (n >= 100) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3C6E6A))
    }
}

/** সূরার শুরুতে নকশা-ব্যানার: [মাক্কী/মাদানী চিহ্ন] [সূরার আরবি নাম] [আয়াত সংখ্যা] */
@Composable
private fun SurahBanner(s: SurahInfo) {
    Box(
        Modifier.fillMaxWidth().height(104.dp).background(BannerBg).drawBehind {
            // হালকা জালি নকশা
            val step = 18.dp.toPx()
            val stroke = 1.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(BannerLine, Offset(x, 0f), Offset(x + size.height, size.height), stroke)
                drawLine(BannerLine, Offset(x + size.height, 0f), Offset(x, size.height), stroke)
                x += step
            }
            drawLine(TanDark, Offset(0f, 2.dp.toPx()), Offset(size.width, 2.dp.toPx()), 2.dp.toPx())
            drawLine(TanDark, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 2.dp.toPx())
        },
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BannerPill(Modifier.weight(0.85f).height(54.dp)) {
                if (s.meccan) KaabaMark(Modifier.size(28.dp)) else DomeMark(Modifier.size(28.dp))
            }
            Spacer(Modifier.width(6.dp))
            BannerPill(Modifier.weight(1.6f).height(70.dp)) {
                Text(s.nameAr, fontFamily = AmiriQuran, fontSize = 30.sp, color = Color(0xFF3A3024), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            BannerPill(Modifier.weight(0.85f).height(54.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Bn.num(s.count), fontSize = 16.sp, color = TanDark, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                    Text("আয়াত", fontSize = 13.sp, color = TanDark, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun BannerPill(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(PillBg, RoundedCornerShape(50)).border(1.5.dp, Tan, RoundedCornerShape(50)).padding(3.dp)
            .border(0.8.dp, BannerLine, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** মাক্কী সূরার চিহ্ন — কাবার সরল রূপ */
@Composable
private fun KaabaMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Color(0xFF9C8763), Offset(w * 0.12f, h * 0.18f), Size(w * 0.76f, h * 0.7f), CornerRadius(2.dp.toPx()))
        drawRect(Color(0xFFE2CC94), Offset(w * 0.12f, h * 0.32f), Size(w * 0.76f, h * 0.1f))
        drawRect(Color(0xFFE2CC94), Offset(w * 0.56f, h * 0.55f), Size(w * 0.14f, h * 0.25f))
    }
}

/** মাদানী সূরার চিহ্ন — গম্বুজ */
@Composable
private fun DomeMark(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val col = Color(0xFF9C8763)
        drawArc(col, 180f, 180f, true, Offset(w * 0.2f, h * 0.22f), Size(w * 0.6f, h * 0.6f))
        drawRect(col, Offset(w * 0.2f, h * 0.52f), Size(w * 0.6f, h * 0.36f))
        drawLine(col, Offset(w / 2, h * 0.04f), Offset(w / 2, h * 0.22f), 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

// =====================================================================
// ধরন ১: শুধু আরবি — মুসহাফের মতো
// =====================================================================

private sealed interface MBlock {
    data class Header(val s: SurahInfo) : MBlock
    data class Bismillah(val s: SurahInfo) : MBlock
    /** একটানা আয়াতের অংশ: শব্দ ও আয়াত-শেষের চিহ্ন */
    data class Para(val tokens: List<Token>) : MBlock
}

private data class Token(val text: String?, val id: Int, val ayah: Int) // text == null হলে আয়াত-শেষের গোল চিহ্ন

/** অক্ষর ছাড়া শুধু চিহ্ন দিয়ে শুরু (ওয়াকফ, আয়াতের গোল চিহ্ন, ইন্দোপাক ফন্টের নম্বর-গ্লিফ) */
private fun startsWithMark(w: String): Boolean {
    val c = w.first()
    return Character.getType(c) == Character.NON_SPACING_MARK.toInt() || c == '—' || c.code in 0xE000..0xF8FF
}

@Composable
private fun MushafPage(
    data: QuranData, pg: Int, indoPak: Boolean, arStyle: TextStyle, playing: Int?, onTap: (Int) -> Unit, footer: @Composable () -> Unit,
) {
    val blocks = remember(pg, indoPak) {
        val out = ArrayList<MBlock>()
        var cur = ArrayList<Token>()
        fun flush() { if (cur.isNotEmpty()) { out += MBlock.Para(cur); cur = ArrayList() } }
        for (id in data.index.pageRange(pg)) {
            val s = data.surah(id)
            val ayah = data.index.ayahNo(id)
            if (ayah == 1) {
                flush()
                out += MBlock.Header(s)
                if (s.no != 1 && s.no != 9) out += MBlock.Bismillah(s)
            }
            data.arabic(id, indoPak).split(' ').filter { it.isNotBlank() }.forEach { w ->
                // ওয়াকফ চিহ্ন বা আয়াতের গোল চিহ্ন আগের শব্দের সাথে থাকে — লাইনের শুরুতে একা যেন না পড়ে
                val last = cur.lastOrNull()
                if (last != null && last.id == id && last.text != null && startsWithMark(w)) cur[cur.lastIndex] = last.copy(text = last.text + " " + w)
                else cur += Token(w, id, ayah)
            }
            if (!indoPak) cur += Token(null, id, ayah) // ইন্দোপাকে নম্বরের চিহ্ন পাঠের ভেতরেই
        }
        flush()
        out
    }
    // মুসহাফে একটু ছোট হরফ — এক লাইনে বেশি শব্দ, ফাঁক কম (আসল মুসহাফের মতো)
    val mStyle = arStyle.copy(fontSize = arStyle.fontSize * 0.9f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val minH = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = if (pg <= 2) minH * 0.8f else 0.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = if (pg <= 2) Arrangement.Center else Arrangement.Top,
            ) {
                blocks.forEach { b ->
                    when (b) {
                        is MBlock.Header -> MushafHeader(b.s)
                        is MBlock.Bismillah -> Text(
                            data.bismillah(indoPak), style = mStyle, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        is MBlock.Para -> MushafParagraph(b.tokens, mStyle, playing, onTap)
                    }
                }
            }
            footer()
        }
    }
}

@Composable
private fun MushafHeader(s: SurahInfo) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).background(MushafGreenBg).border(1.5.dp, MushafGreen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (s.meccan) "মাক্কী" else "মাদানী", fontSize = 17.sp, color = Ink, modifier = Modifier.weight(1f))
        Box(
            Modifier.background(Color.White, RoundedCornerShape(18.dp)).border(2.dp, MushafGreen, RoundedCornerShape(18.dp))
                .padding(horizontal = 34.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(s.nameAr, fontFamily = AmiriQuran, fontSize = 26.sp, color = ArInk, maxLines = 1)
        }
        Text("${Bn.num(s.count)} আয়াত", fontSize = 17.sp, color = Ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** শব্দগুলো মেপে লাইনে ভরি: শেষ লাইন ছাড়া সব দুই প্রান্ত ছোঁয়া, শেষ লাইন মাঝখানে; লাইনের নিচে দাগ */
@Composable
private fun MushafParagraph(tokens: List<Token>, style: TextStyle, playing: Int?, onTap: (Int) -> Unit) {
    val measurer = rememberTextMeasurer(cacheSize = 512)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val maxW = constraints.maxWidth.toFloat()
        val endW: Dp = with(density) { (style.fontSize * 1.25f).toDp() }
        val gapW: Dp = with(density) { (style.fontSize * 0.28f).toDp() }
        val lineH: Dp = with(density) { (style.fontSize * 2.15f).toDp() }
        val lines = remember(tokens, maxW, style) {
            val widths = tokens.map { t ->
                if (t.text == null) with(density) { endW.toPx() }
                else measurer.measure(AnnotatedString(t.text), style, softWrap = false, maxLines = 1).size.width.toFloat()
            }
            MushafLayout.breakLines(widths, maxW, with(density) { gapW.toPx() })
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxWidth()) {
                lines.forEachIndexed { li, r ->
                    val last = li == lines.lastIndex
                    Row(
                        Modifier.fillMaxWidth().height(lineH).drawBehind {
                            if (!last) drawLine(RuleGray, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                        },
                        horizontalArrangement = if (last || r.count() == 1) Arrangement.spacedBy(gapW, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in r) {
                            val t = tokens[i]
                            val ink = if (t.id == playing) PlayingInk else ArInk
                            val tap = Modifier.clickable(interactionSource = null, indication = null) { onTap(t.id) }
                            if (t.text == null) {
                                Box(tap.size(endW), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(endW * 0.95f)) { AyahEndMark(t.ayah, style, ink) }
                                }
                            } else {
                                Text(t.text, style = style.copy(color = ink), softWrap = false, maxLines = 1, modifier = tap)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// পাতার নিচে: পড়া শেষ, আগের/পরের পাতা
// =====================================================================

@Composable
private fun PageFooter(pg: Int, pageCount: Int, isBookmarkPage: Boolean, onDone: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Button(
            onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ReaderBar),
        ) { Text("পৃষ্ঠা ${Bn.num(pg)} পড়া শেষ ✓", fontSize = 17.sp) }
        if (!isBookmarkPage) Text(
            "বুকমার্কের পৃষ্ঠা নয় — আজকের লক্ষ্যে গোনা হবে, বুকমার্ক নড়বে না।", fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // মুসহাফের মতো: পরের পাতা বাম দিকে
            TextButton(enabled = pg < pageCount, onClick = onNext) { Text("‹ পরের পৃষ্ঠা", color = Emerald800) }
            Spacer(Modifier.weight(1f))
            Text("ডানে টানলে পরের পাতা", fontSize = 12.sp, color = Muted)
            Spacer(Modifier.weight(1f))
            TextButton(enabled = pg > 1, onClick = onPrev) { Text("আগের ›", color = Emerald800) }
        }
    }
}
