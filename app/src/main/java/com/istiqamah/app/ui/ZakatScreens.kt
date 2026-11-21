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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.HajjPlan
import com.istiqamah.app.core.NisabBasis
import com.istiqamah.app.core.Zakat
import com.istiqamah.app.core.ZakatInput
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.engine.Engine
import java.time.DayOfWeek

/** ৳১২,৩৪,৫৬৭ — বাংলা অঙ্ক, দক্ষিণ এশীয় কমা */
fun taka(n: Long): String {
    val s = Math.abs(n).toString()
    val grouped = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$rest,$last3"
    }
    return (if (n < 0) "−" else "") + "৳" + grouped.map { if (it.isDigit()) '০' + (it - '0') else it }.joinToString("")
}

private fun digits(t: String) = t.filter { it.isDigit() }.take(12)
private fun decimal(t: String): String {
    val f = t.filter { it.isDigit() || it == '.' }
    val i = f.indexOf('.')
    return (if (i < 0) f else f.substring(0, i + 1) + f.substring(i + 1).replace(".", "")).take(8)
}

/**
 * v0.8 — যাকাত ও সদকা: হিসাব (এনক্রিপ্ট করে রাখা), বার্ষিক হিজরি তারিখ, দেওয়ার খাতা, সাপ্তাহিক গোপন সদকা।
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZakatScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val saved = remember { p.zakatInput }
    fun sL(x: Long) = if (x > 0) x.toString() else ""
    fun sD(x: Double) = if (x > 0) x.toString().removeSuffix(".0") else ""
    var cash by remember { mutableStateOf(sL(saved.cash)) }
    var savings by remember { mutableStateOf(sL(saved.savings)) }
    var goldB by remember { mutableStateOf(sD(saved.goldBhori)) }
    var goldP by remember { mutableStateOf(sL(saved.goldPricePerBhori)) }
    var silverB by remember { mutableStateOf(sD(saved.silverBhori)) }
    var silverP by remember { mutableStateOf(sL(saved.silverPricePerBhori)) }
    var business by remember { mutableStateOf(sL(saved.business)) }
    var receivable by remember { mutableStateOf(sL(saved.receivable)) }
    var investments by remember { mutableStateOf(sL(saved.investments)) }
    var debts by remember { mutableStateOf(sL(saved.debts)) }
    var basis by remember { mutableStateOf(saved.basis) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val input = ZakatInput(
        cash.toLongOrNull() ?: 0, savings.toLongOrNull() ?: 0, goldB.toDoubleOrNull() ?: 0.0, goldP.toLongOrNull() ?: 0,
        silverB.toDoubleOrNull() ?: 0.0, silverP.toLongOrNull() ?: 0, business.toLongOrNull() ?: 0,
        receivable.toLongOrNull() ?: 0, investments.toLongOrNull() ?: 0, debts.toLongOrNull() ?: 0, basis,
    )
    val r = Zakat.calculate(input)
    val today = Engine.today(ctx)
    val h = p.hijri(today)

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("যাকাত ও সদকা", "হিসাব শুধু আপনার ফোনে, এনক্রিপ্ট করা", onBack)

        ContentRepo.byId(ctx, "q_9_103")?.let {
            SectionCard { Text("${it.shortBn} — ${it.refBn}", fontSize = 14.sp, color = Emerald800, lineHeight = 20.sp) }
            Spacer(Modifier.height(12.dp))
        }

        SectionCard {
            ZTitle("যাকাতযোগ্য সম্পদ (৳)")
            Money("হাতে ও ব্যাংকে নগদ", cash) { cash = digits(it) }
            Money("ডিপিএস/এফডিআর/সঞ্চয়পত্র/বিকাশ", savings) { savings = digits(it) }
            Row {
                Field("সোনা (ভরি)", goldB, KeyboardType.Decimal, Modifier.weight(1f)) { goldB = decimal(it) }
                Spacer(Modifier.width(8.dp))
                Field("প্রতি ভরির দাম", goldP, KeyboardType.Number, Modifier.weight(1f)) { goldP = digits(it) }
            }
            Row {
                Field("রুপা (ভরি)", silverB, KeyboardType.Decimal, Modifier.weight(1f)) { silverB = decimal(it) }
                Spacer(Modifier.width(8.dp))
                Field("রুপার প্রতি ভরির দাম", silverP, KeyboardType.Number, Modifier.weight(1f)) { silverP = digits(it) }
            }
            Text("রুপা না থাকলেও রুপার দাম দিন — নিসাব বের করতে লাগে। আজকের দাম বাজুস থেকে দেখে বসান।", fontSize = 12.sp, color = Muted)
            Money("ব্যবসার মাল (বিক্রয়মূল্যে)", business) { business = digits(it) }
            Money("পাওনা টাকা (ফেরত পাওয়ার আশা আছে)", receivable) { receivable = digits(it) }
            Money("শেয়ার/বিক্রির জন্য কেনা জমি-ফ্ল্যাট", investments) { investments = digits(it) }
            ZTitle("বাদ যাবে")
            Money("এখন পরিশোধযোগ্য ঋণ/বকেয়া", debts) { debts = digits(it) }
            Text("নিজের থাকার বাড়ি, ব্যবহারের গাড়ি-আসবাব-জামাকাপড় যাকাতযোগ্য নয়।", fontSize = 12.sp, color = Muted)
            ZTitle("নিসাব")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NisabBasis.entries.forEach { b ->
                    FilterChip(
                        selected = basis == b, onClick = { basis = b }, label = { Text(b.bn, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            ZTitle("ফল")
            Line("মোট সম্পদ", taka(r.totalAssets))
            Line("ঋণ বাদে নিট", taka(r.net))
            Line("নিসাব (${if (r.basisUsed == NisabBasis.GOLD) "সোনা ৭.৫ ভরি" else "রুপা ৫২.৫ ভরি"})", r.nisab?.let { taka(it) } ?: "দাম দিন")
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    r.due > 0 -> "যাকাত: ${taka(r.due)}"
                    r.nisab == null && r.basisUsed != NisabBasis.GOLD -> "রুপার দাম দিলে হিসাব হবে"
                    else -> "নিসাব পূর্ণ হয়নি — যাকাত ফরজ নয়"
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Emerald800,
            )
            if (r.due > 0) Text("নিসাব পূর্ণ হওয়ার পর এক চন্দ্রবছর পার হলে ফরজ।", fontSize = 13.sp, color = Muted)
            Button(
                onClick = { p.zakatInput = input; Toast.makeText(ctx, "হিসাব রাখা হলো (এনক্রিপ্ট করা)", Toast.LENGTH_SHORT).show() },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700), modifier = Modifier.padding(top = 8.dp),
            ) { Text("হিসাব রাখুন") }
            Text("এটা হিসাবের সাহায্য মাত্র — ব্যবসা, ঋণ বা জটিল সম্পদে নিজের আলেমকে জিজ্ঞেস করুন।", fontSize = 12.sp, color = StatusQaza, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            ZTitle("যাকাতের বার্ষিক দিন")
            val zh = p.zakatHijri
            Text(zh?.let { (m, d) -> "প্রতি বছর ${Bn.num(d)} ${com.istiqamah.app.core.HijriDay.MONTHS[m - 1]} — ৭ দিন আগে আর সেদিন মনে করাব" } ?: "এখনো ঠিক করা হয়নি",
                fontSize = 15.sp, color = Ink)
            h?.let { hd ->
                TextButton(onClick = { p.zakatHijri = hd.month to hd.day; vm.settingsChanged() }) {
                    Text("আজকের হিজরি তারিখ (${hd.day.let { Bn.num(it) }} ${hd.monthBn}) রাখুন", color = Emerald700)
                }
            }
            if (zh != null) TextButton(onClick = { p.zakatHijri = null; vm.settingsChanged() }) { Text("রিমাইন্ডার বন্ধ", color = Muted) }
            Text("যেদিন প্রথম নিসাবের মালিক হয়েছিলেন, সেই হিজরি তারিখই যাকাতের বছর।", fontSize = 12.sp, color = Muted)

            // এ বছরের দেওয়া
            val year = h?.year ?: 0
            if (p.zakatPaidYear != year) { p.zakatPaidYear = year; p.zakatPaid = 0 }
            Spacer(Modifier.height(8.dp))
            Line("এ হিজরি বছরে দিয়েছি", taka(p.zakatPaid))
            if (r.due > 0) Line("বাকি", taka((r.due - p.zakatPaid).coerceAtLeast(0)))
            OutlinedButton(onClick = { dialog = "zakatPaid" }) { Text("যাকাত দিয়েছি — লিখুন", color = Emerald800) }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            ZTitle("গোপন সদকা")
            ContentRepo.byId(ctx, "h_muslim_2588")?.let { Text("${it.shortBn} — ${it.refBn}", fontSize = 14.sp, color = Emerald800) }
            Text("প্রতি সপ্তাহে মনে করাব:", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
            val days = listOf<DayOfWeek?>(null, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
            val names = listOf("বন্ধ", "শুক্র", "শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহস্পতি")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { i, d ->
                    FilterChip(
                        selected = p.sadaqahDay == d, onClick = { p.sadaqahDay = d; vm.settingsChanged() }, label = { Text(names[i]) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                    )
                }
            }
            val log = p.sadaqahLog
            val month = log.filter { it.first.year == today.year && it.first.month == today.month }.sumOf { it.second }
            Line("এ মাসে দিয়েছি", taka(month))
            Line("মোট (খাতায়)", taka(log.sumOf { it.second }))
            OutlinedButton(onClick = { dialog = "sadaqah" }) { Text("সদকা দিয়েছি — লিখুন", color = Emerald800) }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(Modifier.clickable { open("hajj") }) {
            Text("হজ ও উমরাহর প্রস্তুতি ›", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("নিয়ত, সঞ্চয়ের হিসাব, শেখার তালিকা, জিলহজের রিমাইন্ডার", fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        "zakatPaid" -> AmountAsk("কত টাকা যাকাত দিলেন?", { dialog = null }) { n -> p.zakatPaid = p.zakatPaid + n; vm.settingsChanged() }
        "sadaqah" -> AmountAsk("কত টাকা সদকা দিলেন?", { dialog = null }) { n ->
            p.sadaqahLog = p.sadaqahLog + (today to n); vm.settingsChanged()
            Toast.makeText(ctx, "আল্লাহ কবুল করুন", Toast.LENGTH_SHORT).show()
        }
    }
}

/** v0.8 — হজ ও উমরাহর প্রস্তুতি */
@Composable
fun HajjScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion
    val money = p.hajjMoney
    var target by remember { mutableStateOf(if (money.first > 0) money.first.toString() else "") }
    var perMonth by remember { mutableStateOf(if (money.third > 0) money.third.toString() else "") }
    var dialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("হজ ও উমরাহ", "নিয়ত থেকে প্রস্তুতি", onBack)
        SectionCard {
            listOf("q_3_97", "h_bukhari_1521", "h_bukhari_1773").mapNotNull { ContentRepo.byId(ctx, it) }.forEach {
                Text("${it.shortBn}\n— ${it.refBn}", fontSize = 14.sp, color = Emerald800, lineHeight = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("আমি হজ/উমরাহর নিয়ত করেছি", fontSize = 17.sp, color = Ink)
                    Text("নিয়ত থেকেই পথ শুরু — প্রতিদিন দোয়া করুন", fontSize = 13.sp, color = Muted)
                }
                Switch(checked = p.hajjIntent, onCheckedChange = { p.hajjIntent = it; vm.settingsChanged() }, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            ZTitle("সঞ্চয়ের হিসাব")
            Money("লক্ষ্য (মোট খরচ)", target) { target = digits(it) }
            Money("মাসে কত জমাবেন", perMonth) { perMonth = digits(it) }
            Line("এখন পর্যন্ত জমা", taka(money.second))
            val months = HajjPlan.monthsToGoal(target.toLongOrNull() ?: 0, money.second, perMonth.toLongOrNull() ?: 0)
            Text(
                when (months) {
                    null -> "মাসে কত জমাবেন দিলে সময় হিসাব হবে"
                    0 -> "আলহামদুলিল্লাহ — লক্ষ্য পূর্ণ"
                    else -> "এই হারে প্রায় ${Bn.num(months)} মাস (${Bn.num(months / 12)} বছর ${Bn.num(months % 12)} মাস)"
                },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Emerald800, modifier = Modifier.padding(vertical = 6.dp),
            )
            Row {
                Button(onClick = {
                    p.hajjMoney = Triple(target.toLongOrNull() ?: 0, money.second, perMonth.toLongOrNull() ?: 0); vm.settingsChanged()
                    Toast.makeText(ctx, "রাখা হলো", Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("রাখুন") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { dialog = true }) { Text("জমা যোগ করুন", color = Emerald800) }
            }
            Text("হালাল টাকায় হজ — সুদ বা হারাম আয় মেশাবেন না।", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            ZTitle("প্রস্তুতির তালিকা")
            val done = p.hajjChecklist
            HajjPlan.CHECKLIST.forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().clickable { p.hajjChecklist = if (i in done) done - i else done + i; vm.settingsChanged() }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = i in done, onCheckedChange = { p.hajjChecklist = if (it) done + i else done - i; vm.settingsChanged() },
                        colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                    Text(item, fontSize = 15.sp, color = Ink)
                }
            }
            Text("${Bn.num(done.size)}/${Bn.num(HajjPlan.CHECKLIST.size)}", fontSize = 13.sp, color = Emerald700)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("জিলহজের প্রথম দশকের রিমাইন্ডার", fontSize = 17.sp, color = Ink)
                    Text("১ জিলহজের আগের সন্ধ্যায় — আমল, আরাফার রোজা, কুরবানিদাতার চুল-নখ", fontSize = 13.sp, color = Muted)
                }
                Switch(checked = p.dhulHijjahOn, onCheckedChange = { p.dhulHijjahOn = it; vm.settingsChanged() }, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (dialog) AmountAsk("কত টাকা জমা হলো?", { dialog = false }) { n ->
        val m = p.hajjMoney
        p.hajjMoney = Triple(m.first, m.second + n, m.third); vm.settingsChanged()
    }
}

@Composable
private fun ZTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Money(label: String, value: String, onChange: (String) -> Unit) =
    Field(label, value, KeyboardType.Number, Modifier.fillMaxWidth(), onChange)

@Composable
private fun Field(label: String, value: String, type: KeyboardType, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, modifier.padding(vertical = 3.dp),
        label = { Text(label, fontSize = 13.sp) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}

@Composable
private fun AmountAsk(title: String, onDismiss: () -> Unit, onOk: (Long) -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(t, { t = digits(it) }, prefix = { Text("৳") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        },
        confirmButton = { TextButton(onClick = { t.toLongOrNull()?.takeIf { it > 0 }?.let(onOk); onDismiss() }) { Text("ঠিক আছে") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}
