package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Akhlaq
import com.istiqamah.app.core.AlimQuestion
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.DebtKind
import com.istiqamah.app.core.Ihsan
import com.istiqamah.app.core.Masail
import com.istiqamah.app.core.MasailGroup
import com.istiqamah.app.core.MasailLesson
import com.istiqamah.app.core.WasiyyahDraft
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.notify.Notifier
import java.time.LocalDate

// =====================================================================
// v0.13 — দ্বীনের মৌলিক মাসআলা ও পথের তিন সঙ্গী
// =====================================================================

private val NoteBg = Color(0xFFFBF1E0)

@Composable
private fun MadhhabNote() {
    Text(
        Masail.MADHHAB_NOTE, fontSize = 13.sp, lineHeight = 19.sp, color = Ink,
        modifier = Modifier.fillMaxWidth().background(NoteBg, RoundedCornerShape(12.dp)).padding(10.dp),
    )
}

@Composable
fun DeenHubScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val done = p.masailDone
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("দ্বীনের মৌলিক শিক্ষা", "পবিত্রতা থেকে মৃত্যুর প্রস্তুতি — যা না জানলেই নয়", onBack)
        SectionCard(Modifier.clickable { open("companions") }) {
            Text("আগে পড়ুন: পথের তিন সঙ্গী", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("অ্যাপ শুধু অভ্যাস গড়ার সাহায্যকারী। আসল তিনটি জিনিস অ্যাপের বাইরে — একজন আলেম, ভালো মানুষের সঙ্গ, আর মসজিদ।", fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
            Text("আপনার আছে: ${Bn.num(p.companionsDone.size)}/৩ ›", fontSize = 14.sp, color = Emerald700, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        MadhhabNote()
        Spacer(Modifier.height(12.dp))
        Masail.groupsFor(p.gender).forEach { g ->
            Text(g.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold)
            Text(g.subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
            Masail.lessons(g).forEach { l ->
                SectionCard(Modifier.clickable { open("masail:${l.key}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(l.title, fontSize = 16.sp, color = Emerald800, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (l.key in done) "✓ শিখেছি" else "›", fontSize = 13.sp, color = if (l.key in done) Emerald700 else Muted)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (p.gender == com.istiqamah.app.core.Gender.MALE) TextButton(onClick = { open("masail:haydh") }) {
            Text("পরিবারের জন্য জানতে: মহিলাদের মাসআলা ›", color = Muted, fontSize = 13.sp)
        }
        SectionCard(Modifier.clickable { open("questions") }) {
            Text("প্রশ্নের খাতা", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            val open0 = p.questions.count { !it.done }
            Text(if (open0 > 0) "${Bn.num(open0)}টি প্রশ্ন আলেমকে জিজ্ঞেস করা বাকি ›" else "কোনো মাসআলায় সন্দেহ হলে লিখে রাখুন — আলেমের সাথে দেখা হলে জিজ্ঞেস করবেন ›",
                fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun CompanionsScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = vm.prefs
    val data = rememberQuran()
    var done by remember { mutableStateOf(p.companionsDone) }
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("পথের তিন সঙ্গী", "অ্যাপ যা পারবে না", onBack)
        SectionCard {
            Text("খাঁটি মুসলমান হওয়ার পথে এই অ্যাপ শুধু অভ্যাস গড়ার সাহায্যকারী — মনে করিয়ে দেয়, হিসাব রাখে, উৎসাহ দেয়।", fontSize = 16.sp, lineHeight = 24.sp, color = Ink)
            Text("আসল তিনটি জিনিস অ্যাপের বাইরে:", fontSize = 16.sp, lineHeight = 24.sp, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            listOf("একজন আলেম বা উস্তাদ, যাঁর কাছ থেকে শিখবেন", "ভালো মানুষের সঙ্গ — সুহবত", "মসজিদের সাথে সম্পর্ক").forEach {
                Text("• $it", fontSize = 15.sp, color = Ink, modifier = Modifier.padding(top = 2.dp))
            }
            Text("এই তিনটি ছাড়া অ্যাপের সব ফিচার মিলেও যথেষ্ট নয়। নিচে প্রতিটির জন্য আজ থেকেই কী করবেন।", fontSize = 14.sp, color = Emerald700, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Masail.COMPANIONS.forEach { c ->
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text(c.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text(c.why, fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(top = 4.dp))
                Text("কীভাবে", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.padding(top = 8.dp))
                c.how.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp, color = Ink, modifier = Modifier.padding(top = 2.dp)) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                        done = if (c.key in done) done - c.key else done + c.key
                        p.companionsDone = done; vm.refresh()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = c.key in done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                    Text(c.check, fontSize = 15.sp, color = if (c.key in done) Emerald700 else Ink, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            c.ayah?.let { AyahBlock(data, it, open, p.quranScript == 0); Spacer(Modifier.height(8.dp)) }
            c.hadith?.let { HadithBlock(it) }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { open("questions") }, modifier = Modifier.fillMaxWidth()) { Text("প্রশ্নের খাতা খুলুন", color = Emerald800) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun MasailLessonScreen(vm: AppVm, key: String, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val l = Masail.byKey(key) ?: run {
        androidx.compose.runtime.LaunchedEffect(key) { onBack() }
        return
    }
    val p = vm.prefs
    val data = rememberQuran()
    var learned by remember(key) { mutableStateOf(key in p.masailDone) }
    var ask by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader(l.title, l.group.title, onBack)
        MadhhabNote()
        Spacer(Modifier.height(12.dp))
        l.sections.forEach { s ->
            SectionCard {
                Text(s.heading, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                s.points.forEach { Text("• $it", fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(top = 4.dp)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        when (l.tool) {
            "wudu" -> WuduPractice()
            "shukr" -> ShukrJournal(vm)
            "rights" -> RightsWeek(vm)
            "wasiyyah" -> WasiyyahEditor(vm)
        }
        if (l.tool != null) Spacer(Modifier.height(10.dp))
        if (l.ayat.isNotEmpty()) {
            Text("কুরআন থেকে", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
            l.ayat.forEach { AyahBlock(data, it, open, p.quranScript == 0); Spacer(Modifier.height(8.dp)) }
        }
        if (l.hadith.isNotEmpty()) {
            Text("হাদিস থেকে", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 6.dp))
            l.hadith.forEach { HadithBlock(it); Spacer(Modifier.height(8.dp)) }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { learned = !learned; p.masailDone = if (learned) p.masailDone + key else p.masailDone - key; vm.refresh() },
                colors = ButtonDefaults.buttonColors(containerColor = if (learned) Emerald50 else Emerald700),
            ) { Text(if (learned) "✓ শিখেছি" else "শিখেছি", color = if (learned) Emerald800 else Color.White) }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { ask = true }) { Text("প্রশ্ন লিখে রাখুন", color = Emerald800) }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (ask) QuestionDialog(null, "${l.title}: ", { ask = false }) { q ->
        p.questions = p.questions + q.copy(id = (p.questions.maxOfOrNull { it.id } ?: 0) + 1)
        ask = false
    }
}

// =====================================================================
// সাহায্য-যন্ত্র
// =====================================================================

/** ওযুর অনুশীলন — ধাপে ধাপে চিহ্ন দিয়ে */
@Composable
private fun WuduPractice() {
    val checks = remember { mutableStateListOf(*Array(Masail.WUDU_STEPS.size) { false }) }
    SectionCard {
        Text("ওযুর অনুশীলন — ধাপে ধাপে", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("ওযু করতে করতে প্রতিটি ধাপ চিহ্ন দিন। গাঢ় লেখা = ফরজ।", fontSize = 13.sp, color = Muted)
        Masail.WUDU_STEPS.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clickable { checks[i] = !checks[i] }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checks[i], onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                Text(
                    "${Bn.num(i + 1)}. ${s.text}", fontSize = 15.sp, lineHeight = 22.sp,
                    color = if (s.fard) Emerald800 else Ink, fontWeight = if (s.fard) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (checks.all { it }) Text("ওযু সম্পূর্ণ — এখন কালিমায়ে শাহাদাত, জান্নাতের আট দরজা খোলা (মুসলিম ২৩৪)", fontSize = 14.sp, color = Emerald700, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { for (i in checks.indices) checks[i] = false }) { Text("আবার শুরু", color = Muted) }
    }
}

/** শোকরের খাতা — প্রতিদিন তিনটি নিয়ামত */
@Composable
private fun ShukrJournal(vm: AppVm) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val today = Engine.today(ctx)
    var all by remember { mutableStateOf(p.shukr) }
    val todayList = all[today].orEmpty()
    var a by remember { mutableStateOf(todayList.getOrElse(0) { "" }) }
    var b by remember { mutableStateOf(todayList.getOrElse(1) { "" }) }
    var c by remember { mutableStateOf(todayList.getOrElse(2) { "" }) }
    SectionCard {
        Text("শোকরের খাতা — আজকের তিনটি নিয়ামত", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("ছোট হলেও লিখুন: সুস্থতা, এক কাপ চা, কারও হাসি। শুধু ফোনে, এনক্রিপ্ট করে থাকে।", fontSize = 13.sp, color = Muted)
        listOf(a to { s: String -> a = s }, b to { s: String -> b = s }, c to { s: String -> c = s }).forEachIndexed { i, (v, set) ->
            OutlinedTextField(v, { set(it.take(80)) }, label = { Text("${Bn.num(i + 1)}. আলহামদুলিল্লাহ —") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            val list = listOf(a, b, c).map { it.trim() }.filter { it.isNotEmpty() }
            all = if (list.isEmpty()) all - today else all + (today to list)
            p.shukr = all
        }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700), modifier = Modifier.padding(top = 6.dp)) { Text("রাখুন") }
        val days = all.keys.count { !it.isBefore(today.minusDays(29)) }
        if (days > 0) Text("গত ৩০ দিনে ${Bn.num(days)} দিন শোকর লিখেছেন — \"কৃতজ্ঞ হলে আরও দেব\" (১৪:৭)", fontSize = 13.sp, color = Emerald700, modifier = Modifier.padding(top = 4.dp))
    }
}

/** এ সপ্তাহে মুসলিমের ছয় হক */
@Composable
private fun RightsWeek(vm: AppVm) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val week = Ihsan.weekKey(Engine.today(ctx))
    var done by remember(week) { mutableStateOf(p.rightsDone(week)) }
    val items = listOf(
        "salam" to "আগে সালাম দিয়েছি, জবাব সুন্দর করে দিয়েছি",
        "dawat" to "কারও দাওয়াত কবুল করেছি",
        "nasiha" to "কেউ পরামর্শ চাইলে আন্তরিক পরামর্শ দিয়েছি",
        "sneeze" to "হাঁচির জবাব দিয়েছি",
        "sick" to "অসুস্থ কাউকে দেখতে গিয়েছি বা খোঁজ নিয়েছি",
        "janazah" to "জানাযায় শরিক হয়েছি (সুযোগ হলে)",
    )
    SectionCard {
        Text("এ সপ্তাহে কোন হকগুলো আদায় করলেন?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        items.forEach { (k, t) ->
            Row(
                Modifier.fillMaxWidth().clickable { done = if (k in done) done - k else done + k; p.setRightsDone(week, done) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = k in done, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                Text(t, fontSize = 14.sp, color = Ink, modifier = Modifier.padding(start = 6.dp))
            }
        }
        Text("প্রতি সোমবার নতুন সপ্তাহ।", fontSize = 12.sp, color = Muted)
    }
}

/** ওসিয়তের খসড়া — এনক্রিপ্ট করে, শেয়ার/প্রিন্ট করা যায় */
@Composable
private fun WasiyyahEditor(vm: AppVm) {
    val ctx = LocalContext.current
    val p = vm.prefs
    var w by remember { mutableStateOf(p.wasiyyah) }
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    fun upd(n: WasiyyahDraft) { w = n; saved = false }
    SectionCard {
        Text("আমার ওসিয়তের খসড়া", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("শুধু ফোনে, এনক্রিপ্ট করে থাকে। লিখে পরিবারের বিশ্বস্ত কাউকে দিন বা প্রিন্ট করে সই-সাক্ষীসহ রাখুন। আইনি দলিলের জন্য আইনজীবীর পরামর্শ নিন।",
            fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
        TextButton(onClick = {
            val list = p.debts.filter { !it.done }
            fun line(e: com.istiqamah.app.core.DebtEntry) = "• ${e.who}" + (if (e.amount > 0) " — ৳${taka(e.amount)}" else "") + (if (e.note.isNotBlank()) " (${e.note})" else "")
            upd(w.copy(
                debts = list.filter { it.kind == DebtKind.I_OWE }.joinToString("\n") { line(it) }.ifBlank { w.debts },
                amanah = list.filter { it.kind == DebtKind.AMANAH }.joinToString("\n") { line(it) }.ifBlank { w.amanah },
            ))
        }) { Text("দেনা-পাওনার খাতা থেকে বসিয়ে দিন", color = Emerald700) }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("আপনার নাম (শুধু লেখায় বসবে, রাখা হয় না)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        listOf(
            Triple("আমার দেনা", w.debts) { s: String -> upd(w.copy(debts = s)) },
            Triple("আমার কাছে আমানত", w.amanah) { s: String -> upd(w.copy(amanah = s)) },
            Triple("কাযা নামাজ-রোজা ও ফিদিয়া", w.qaza) { s: String -> upd(w.copy(qaza = s)) },
            Triple("দান-সদকার ওসিয়ত (১/৩-এর মধ্যে)", w.charity) { s: String -> upd(w.copy(charity = s)) },
            Triple("দাফন-কাফন", w.burial) { s: String -> upd(w.copy(burial = s)) },
            Triple("পরিবারের প্রতি উপদেশ", w.advice) { s: String -> upd(w.copy(advice = s)) },
            Triple("যাদের জানাতে হবে", w.contacts) { s: String -> upd(w.copy(contacts = s)) },
        ).forEach { (label, value, set) ->
            OutlinedTextField(value, { set(it.take(1000)) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), minLines = 2)
        }
        Row(Modifier.padding(top = 8.dp)) {
            Button(onClick = { p.wasiyyah = w; saved = true }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text(if (saved) "✓ রাখা হয়েছে" else "রাখুন") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                p.wasiyyah = w; saved = true
                ctx.startActivity(Notifier.shareIntent(w.text(name, Engine.today(ctx))).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("শেয়ার/প্রিন্ট", color = Emerald800) }
        }
        if (Akhlaq.overdue(p.debts, Engine.today(ctx)).isNotEmpty()) Text("দেনার খাতায় তারিখ পেরোনো দেনা আছে — আগে সেগুলো শোধের চেষ্টা করুন।", fontSize = 13.sp, color = StatusQaza, modifier = Modifier.padding(top = 4.dp))
    }
}

// =====================================================================
// প্রশ্নের খাতা
// =====================================================================

@Composable
fun QuestionsScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val p = vm.prefs
    var list by remember { mutableStateOf(p.questions) }
    var edit by remember { mutableStateOf<AlimQuestion?>(null) }
    var add by remember { mutableStateOf(false) }
    fun save(v: List<AlimQuestion>) { list = v; p.questions = v }
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("প্রশ্নের খাতা", "আলেমকে জিজ্ঞেস করার জন্য — শুধু ফোনে, এনক্রিপ্ট করে", onBack)
        Button(onClick = { add = true }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("+ নতুন প্রশ্ন") }
        Spacer(Modifier.height(10.dp))
        val (pending, answered) = list.sortedByDescending { it.asked }.partition { !it.done }
        if (pending.isNotEmpty()) Text("জিজ্ঞেস করা বাকি", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold)
        pending.forEach { q -> QuestionRow(q) { edit = q }; Spacer(Modifier.height(6.dp)) }
        if (answered.isNotEmpty()) Text("উত্তর পেয়েছি", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(top = 8.dp))
        answered.forEach { q -> QuestionRow(q) { edit = q }; Spacer(Modifier.height(6.dp)) }
        if (list.isEmpty()) Text("কোনো মাসআলায় সন্দেহ হলে এখানে লিখুন। মসজিদের ইমাম বা পরিচিত আলেমের সাথে দেখা হলে জিজ্ঞেস করুন, উত্তরটাও লিখে রাখুন।", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
    if (add) QuestionDialog(null, "", { add = false }) { q -> save(list + q.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1)); add = false }
    edit?.let { e ->
        QuestionDialog(e, e.text, { edit = null }, onDelete = { save(list.filter { it.id != e.id }); edit = null }) { q -> save(list.map { if (it.id == e.id) q else it }); edit = null }
    }
}

@Composable
private fun QuestionRow(q: AlimQuestion, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Text(q.text, fontSize = 15.sp, color = Ink, lineHeight = 22.sp)
        if (q.answer.isNotBlank()) Text("উত্তর: ${q.answer}", fontSize = 14.sp, color = Emerald700, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
        Text(Bn.date(q.asked), fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun QuestionDialog(e: AlimQuestion?, initial: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onSave: (AlimQuestion) -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    var answer by remember { mutableStateOf(e?.answer ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (e == null) "আলেমকে কী জিজ্ঞেস করবেন?" else "প্রশ্ন ও উত্তর") },
        text = {
            Column {
                OutlinedTextField(text, { text = it.take(500) }, label = { Text("প্রশ্ন") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (e != null) OutlinedTextField(answer, { answer = it.take(1000) }, label = { Text("আলেমের উত্তর") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 2)
                onDelete?.let { d -> TextButton(onClick = d) { Text("মুছে ফেলুন", color = StatusQaza) } }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                val today = Engine.today(ctx)
                onSave((e ?: AlimQuestion(0, "", "", today, false)).copy(text = text.trim(), answer = answer.trim(), done = answer.isNotBlank()))
            }) { Text("রাখুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}
