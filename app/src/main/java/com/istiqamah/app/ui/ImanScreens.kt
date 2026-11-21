package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.AkhirahCard
import com.istiqamah.app.core.Akhirah
import com.istiqamah.app.core.AyahRef
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Iman
import com.istiqamah.app.core.ImanLesson
import com.istiqamah.app.core.Wholeness
import com.istiqamah.app.core.WholenessResult
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.QuranData
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.launch

// =====================================================================
// v0.9 — ঈমানের ৬ রুকন, আখিরাত কার্ড, আমলের পূর্ণতা
// =====================================================================

private val HopeBg = Color(0xFFE6F4EC)
private val WarnBg = Color(0xFFFBF1E0)

@Composable
fun ImanHomeScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val done = p.imanDone
    val count = Iman.LESSONS.count { it.key in done }
    val todayCard = Akhirah.CARDS[Akhirah.forDay(Engine.today(ctx))]
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("ঈমানের ৬ রুকন", "হাদিসে জিবরাইল (মুসলিম ৮) ধরে — পাঠ, আয়াত-হাদিস আর ছোট কুইজ", onBack)
        SectionCard {
            Text("${Bn.num(count)}/${Bn.num(Iman.LESSONS.size)} পাঠ সম্পূর্ণ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { count / Iman.LESSONS.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = Gold, trackColor = Emerald50,
            )
            Spacer(Modifier.height(8.dp))
            ContentRepo.byId(ctx, "h_muslim_8")?.let { Text("\"${it.shortBn}\"", fontSize = 14.sp, color = Muted, lineHeight = 21.sp) }
            Iman.next(done)?.let { n ->
                Spacer(Modifier.height(8.dp))
                Button(onClick = { open("lesson:${n.key}") }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) {
                    Text(if (count == 0) "প্রথম পাঠ শুরু করুন" else "পরের পাঠ: ${n.title}")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Iman.LESSONS.forEach { l ->
            val ok = l.key in done
            SectionCard(Modifier.clickable { open("lesson:${l.key}") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(if (ok) Emerald700 else Emerald50, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (ok) "✓" else Bn.num(l.no), color = if (ok) Color.White else Emerald800, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
                        Text(if (ok) "কুইজ পার হয়েছে" else "পাঠ ও ৩ প্রশ্নের কুইজ", fontSize = 13.sp, color = Muted)
                    }
                    Text(l.arabic, fontFamily = AmiriQuran, fontSize = 18.sp, color = Emerald800)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(4.dp))
        SectionCard(Modifier.clickable { open("akhirah") }) {
            Text("আখিরাত কার্ড", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("মৃত্যু থেকে জান্নাত পর্যন্ত ${Bn.num(Akhirah.CARDS.size)}টি কার্ড — প্রতিটিতে সতর্কবাণীর সাথে আশার কথা, আর আজকের একটি কাজ", fontSize = 14.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text("আজকের কার্ড: ${todayCard.title} ›", fontSize = 15.sp, color = Emerald700, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text("সব ব্যাখ্যা ও কুইজ আলেমের রিভিউয়ের অপেক্ষায়। আকিদার গভীর প্রশ্নে নির্ভরযোগ্য আলেমের কাছে যান।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ImanLessonScreen(vm: AppVm, key: String, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val lesson = Iman.byKey(key) ?: run {
        androidx.compose.runtime.LaunchedEffect(key) { onBack() }
        return
    }
    val ctx = LocalContext.current
    val data = rememberQuran()
    val p = vm.prefs
    val picks = remember(key) { mutableStateListOf<Int?>().apply { repeat(lesson.quiz.size) { add(null) } } }
    var checked by remember(key) { mutableStateOf(false) }
    var passedNow by remember(key) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(scroll).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("পাঠ ${Bn.num(lesson.no)}: ${lesson.title}", null, onBack)
        SectionCard {
            Text(lesson.arabic, fontFamily = AmiriQuran, fontSize = 26.sp, color = Emerald800, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(lesson.summary, fontSize = 17.sp, lineHeight = 26.sp, color = Ink)
            Spacer(Modifier.height(8.dp))
            lesson.points.forEach { Text("• $it", fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(vertical = 3.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        LTitle("কুরআন থেকে")
        lesson.ayat.forEach { r -> AyahBlock(data, r, open, p.quranScript == 0); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(4.dp))
        LTitle("হাদিস থেকে")
        lesson.hadith.forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
        Spacer(Modifier.height(4.dp))
        SectionCard {
            Text("আজকের অনুশীলন", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text(lesson.practice, fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
        }
        Spacer(Modifier.height(16.dp))

        // ---------- কুইজ ----------
        LTitle("ছোট কুইজ — সব ঠিক হলে পাঠ সম্পূর্ণ")
        lesson.quiz.forEachIndexed { qi, q ->
            SectionCard {
                Text("${Bn.num(qi + 1)}. ${q.q}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                q.options.forEachIndexed { oi, o ->
                    val right = checked && oi == q.answer
                    val wrong = checked && picks[qi] == oi && oi != q.answer
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !checked) { picks[qi] = oi }.padding(vertical = 2.dp)
                            .background(if (right) HopeBg else if (wrong) WarnBg else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picks[qi] == oi, onClick = { if (!checked) picks[qi] = oi },
                            colors = RadioButtonDefaults.colors(selectedColor = Emerald700))
                        Text(o + if (right) "  ✓" else if (wrong) "  ✗" else "", fontSize = 15.sp, color = Ink)
                    }
                }
                if (checked) Text(q.why, fontSize = 14.sp, lineHeight = 21.sp, color = Emerald700, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!checked) {
            Button(
                onClick = {
                    checked = true
                    if (Iman.passed(lesson, picks.toList())) {
                        passedNow = true
                        p.imanDone = p.imanDone + lesson.key
                        vm.settingsChanged()
                    }
                },
                enabled = picks.all { it != null },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
            ) { Text("উত্তর মিলিয়ে দেখুন", fontSize = 16.sp) }
        } else if (passedNow || lesson.key in p.imanDone && Iman.passed(lesson, picks.toList())) {
            SectionCard {
                Text("আলহামদুলিল্লাহ — পাঠ সম্পূর্ণ ✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald700)
                Text("আল্লাহ ঈমান মজবুত করুন। এখন অনুশীলনটা আজই করে ফেলুন।", fontSize = 14.sp, color = Muted)
                Iman.next(p.imanDone)?.let { n ->
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { open("lesson:${n.key}") }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("পরের পাঠ: ${n.title}") }
                } ?: TextButton(onClick = { open("akhirah") }) { Text("সব পাঠ শেষ — এবার আখিরাত কার্ড ›", color = Emerald700) }
            }
        } else {
            SectionCard {
                Text("কিছু উত্তর মেলেনি — ব্যাখ্যাগুলো পড়ে আবার চেষ্টা করুন।", fontSize = 15.sp, color = Ink)
                OutlinedButton(onClick = {
                    for (i in picks.indices) picks[i] = null
                    checked = false
                    scope.launch { scroll.animateScrollTo(0) }
                }, modifier = Modifier.padding(top = 6.dp)) { Text("আবার চেষ্টা", color = Emerald800) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
}

/** কুরআনের আয়াত — আরবি, বাংলা অর্থ, কুরআনে খোলার বোতাম */
@Composable
private fun AyahBlock(data: QuranData?, r: AyahRef, open: (String) -> Unit, indoPak: Boolean, bg: Color = Color.White) {
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(14.dp)) {
        if (data == null) {
            Text("সূরা ${r.bn} — লোড হচ্ছে…", fontSize = 14.sp, color = Muted)
            return@Column
        }
        val ids = (r.from..r.to).map { data.index.idOf(r.surah, it) }
        Text(
            if (indoPak) ids.joinToString(" ") { data.ip(it) }
            else ids.joinToString(" ") { data.ar(it) + " ۝" + com.istiqamah.app.core.QuranScript.digits(data.index.ayahNo(it)) },
            fontFamily = if (indoPak) IndoPak else AmiriQuran, fontSize = 23.sp, lineHeight = 46.sp, color = Ink,
            textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(ids.joinToString(" ") { data.bn(it) }, fontSize = 15.sp, lineHeight = 23.sp, color = Color(0xFF2B6C70))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("সূরা ${data.surahs[r.surah - 1].nameBn} ${r.bn}", fontSize = 13.sp, color = Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { open("reader:${data.index.pageOf(ids.first())}") }) { Text("কুরআনে খুলুন ›", fontSize = 13.sp, color = Emerald700) }
        }
    }
}

@Composable
private fun HadithBlock(id: String, bg: Color = Color.White) {
    val ctx = LocalContext.current
    val h = remember(id) { ContentRepo.byId(ctx, id) } ?: return
    Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text(h.bn, fontSize = 15.sp, lineHeight = 23.sp, color = Ink)
        Text(h.refBn, fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

// =====================================================================
// আখিরাত কার্ড — ডানে-বামে সরিয়ে পড়া
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AkhirahScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val data = rememberQuran()
    val start = remember { Akhirah.forDay(Engine.today(ctx)) }
    val pager = rememberPagerState(initialPage = start) { Akhirah.CARDS.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenHeader("আখিরাত কার্ড", "${Bn.num(pager.currentPage + 1)}/${Bn.num(Akhirah.CARDS.size)} · ভয় আর আশা — দুই ডানায়", onBack)
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            AkhirahPage(Akhirah.CARDS[i], data, open, isToday = i == start, indoPak = vm.prefs.quranScript == 0)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("‹ আগের", color = Emerald800) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { open("iman") }) { Text("ঈমানের পাঠ", color = Emerald700) }
            Spacer(Modifier.weight(1f))
            TextButton(enabled = pager.currentPage < Akhirah.CARDS.lastIndex, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) { Text("পরের ›", color = Emerald800) }
        }
    }
}

@Composable
private fun AkhirahPage(c: AkhirahCard, data: QuranData?, open: (String) -> Unit, isToday: Boolean, indoPak: Boolean) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        if (isToday) Text("আজকের কার্ড", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        Text(c.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Spacer(Modifier.height(8.dp))
        c.ayah?.let { AyahBlock(data, it, open, indoPak, WarnBg); Spacer(Modifier.height(8.dp)) }
        Text(c.reflection, fontSize = 17.sp, lineHeight = 26.sp, color = Ink)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().background(HopeBg, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text("আশার কথা", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Emerald700)
            Text(c.hope, fontSize = 17.sp, lineHeight = 26.sp, color = Ink)
        }
        Spacer(Modifier.height(8.dp))
        c.hopeAyah?.let { AyahBlock(data, it, open, indoPak, HopeBg); Spacer(Modifier.height(8.dp)) }
        c.hadith?.let { id ->
            val h = remember(id) { ContentRepo.byId(ctx, id) }
            h?.let { HadithBlock(id); Spacer(Modifier.height(8.dp)) }
        }
        SectionCard {
            Text("আজকের কাজ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text(c.action, fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// =====================================================================
// আজ ট্যাবের কার্ড — আমলের পূর্ণতা
// =====================================================================

@Composable
fun WholenessCard(w: WholenessResult, open: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card16 {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("আমলের পূর্ণতা", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text("গত ৩০ দিন · সব আমল মিলিয়ে", fontSize = 13.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${Bn.num(w.overall)}%", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
                Text(Wholeness.label(w.overall), fontSize = 12.sp, color = Muted)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            w.areas.forEach { a ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.area.bn, fontSize = 14.sp, color = Ink, modifier = Modifier.weight(1f))
                    Text(a.detail, fontSize = 12.sp, color = Muted)
                }
                LinearProgressIndicator(
                    progress = { a.percent / 100f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).padding(top = 2.dp),
                    color = if (a.percent >= 70) Emerald700 else Gold, trackColor = Emerald50,
                )
            }
            Text(
                "ধাপ ওঠে নামাজ দিয়ে (কিয়ামতে প্রথম হিসাব নামাজের) — এটা তার পাশে পুরো ছবি।",
                fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp),
            )
        }
        w.weakest?.let { a ->
            HorizontalDivider(color = Emerald50, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "পরের ছোট পদক্ষেপ (${a.area.bn}): ${a.area.nextStep}" + if (a.area.route != null) " ›" else "",
                fontSize = 14.sp, color = Emerald700, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable(enabled = a.area.route != null) { a.area.route?.let(open) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "কম দেখান" else "প্রতিটি আমল দেখুন", color = Emerald700, fontSize = 13.sp)
            }
        }
    }
}
