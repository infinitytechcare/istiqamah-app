package com.istiqamah.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material3.RadioButton
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
import com.istiqamah.app.core.DayOverride
import com.istiqamah.app.core.EaseKeys
import com.istiqamah.app.core.HomeSetup
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.Report
import com.istiqamah.app.core.Sadaqah
import com.istiqamah.app.core.Situation
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.notify.Notifier
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.concurrent.thread

private val WEEK = listOf(
    DayOfWeek.SATURDAY to "শনি", DayOfWeek.SUNDAY to "রবি", DayOfWeek.MONDAY to "সোম", DayOfWeek.TUESDAY to "মঙ্গল",
    DayOfWeek.WEDNESDAY to "বুধ", DayOfWeek.THURSDAY to "বৃহঃ", DayOfWeek.FRIDAY to "শুক্র",
)

// =====================================================================
// ঘরে-একা নামাজ — মোড, রুটিন, ওয়াদা, জবাবদিহি, সাদাকার বাক্স, ঘরের পরিবেশ
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeAloneScreen(vm: AppVm, open: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val p = vm.prefs
    @Suppress("UNUSED_VARIABLE") val v = vm.settingsVersion // সেটিং বদলালে আবার আঁকা
    val today = Engine.today(ctx)
    var dialog by remember { mutableStateOf<String?>(null) }
    val changed = { vm.settingsChanged() }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("ঘরে-একা নামাজ", "অফিসে পড়া হয়, ঘরে অলসতা — তার সমাধান", onBack)

        SectionCard {
            Text(
                "অফিসে চারপাশের মানুষ, আযান, রুটিন — সবই মনে করিয়ে দেয়। ঘরে একা বা ছুটির দিনে সেই ইশারা থাকে না, " +
                    "তাই অলসতা জেতে। এই মোডে ঘরের ওয়াক্তগুলোতে অ্যাপ নিজেই সেই কাজটা করে:",
                fontSize = 15.sp, color = Ink, lineHeight = 22.sp,
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "ওয়াক্ত শুরু হলে অ্যালার্ম-ঘড়ির মতো পুরো পর্দার ডাক",
                "পুরো নামাজ নয় — শুধু প্রথম ছোট ধাপ: \"ওযু করতে যাচ্ছি\"",
                "না পড়া পর্যন্ত প্রতি ১৫ মিনিটে নাছোড় ডাক (সর্বোচ্চ ৩টি)",
                "নোটিফিকেশনে আপনার নিজের লেখা ওয়াদা আর বাকি সময়ের কাউন্টডাউন",
            ).forEach { Text("• $it", fontSize = 14.sp, color = Muted, lineHeight = 21.sp) }
            HToggle("ঘরে মোড চালু", Situation.todayLabel(today, p.homeMode), p.homeModeOn) { p.homeModeOn = it; changed() }
        }
        Spacer(Modifier.height(12.dp))

        if (p.homeModeOn) {
            SectionCard {
                HTitle("আজ কোথায় আছেন?")
                val cur = p.dayOverride(today)
                DayOverride.entries.forEach { o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { p.setDayOverride(today, o); changed() }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = cur == o, onClick = { p.setDayOverride(today, o); changed() })
                        Text(o.bn, fontSize = 16.sp, color = Ink)
                    }
                }
                Text("শুধু আজকের জন্য — কাল আবার রুটিন অনুযায়ী।", fontSize = 13.sp, color = Muted)
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                HTitle("অফিস/কাজের রুটিন")
                HToggle("আমার নির্দিষ্ট অফিস সময় আছে", "এর বাইরের সব ওয়াক্ত = ঘরে", p.workEnabled) { p.workEnabled = it; changed() }
                if (p.workEnabled) {
                    Text("অফিসের দিন", fontSize = 14.sp, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEK.forEach { (d, label) ->
                            val on = d in p.workDays
                            FilterChip(
                                selected = on,
                                onClick = { p.workDays = if (on) p.workDays - d else p.workDays + d; changed() },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald50, selectedLabelColor = Emerald800),
                            )
                        }
                    }
                    HItem("অফিস শুরু", Bn.time(p.workStart)) { pickTime(ctx, p.workStart) { p.workStart = it; changed() } }
                    HItem("অফিস শেষ (বাসায় ফেরা)", Bn.time(p.workEnd)) { pickTime(ctx, p.workEnd) { p.workEnd = it; changed() } }
                    // v0.18: সময়মতো কাজে যাওয়া — হালাল রুজির অংশ
                    HToggle("অফিসের রিমাইন্ডার (হালাল রুজি)", "সময়মতো রওনা, কাজ শুরুর নিয়ত — ছুটির দিন বা \"আজ ঘরে\" বাছলে আসে না", p.workRemindOn) {
                        p.workRemindOn = it; changed()
                    }
                    if (p.workRemindOn) {
                        val r = p.workReminders
                        HItem("যাতায়াতে কত সময়", "${Bn.num(r.commuteMin)} মিনিট — রওনার ডাক ${Bn.time(p.workStart.minusMinutes(r.commuteMin + com.istiqamah.app.core.Rizq.READY_MIN))}-এ") { dialog = "commute" }
                        HToggle("রওনা দেওয়ার ডাক", "বের হওয়ার দোয়াসহ", r.leave) { p.workReminders = r.copy(leave = it); changed() }
                        HToggle("কাজ শুরুর নিয়ত", "অফিস শুরুর সময়ে", r.start) { p.workReminders = r.copy(start = it); changed() }
                        HToggle("কাজ শেষে শুকরিয়া", "অফিস শেষের সময়ে — সময়মতো বাড়ি ফেরা", r.end) { p.workReminders = r.copy(end = it); changed() }
                    }
                }
                val homeToday = Engine.windows(ctx, today)?.filter { Situation.isHome(it.start, p.homeMode) }?.map { it.prayer.bn }
                if (homeToday != null) {
                    Text(
                        if (homeToday.isEmpty()) "আজ কোনো ওয়াক্ত ঘরে নয়" else "আজ ঘরের ওয়াক্ত: ${homeToday.joinToString(", ")}",
                        fontSize = 14.sp, color = Emerald700, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                HTitle("পুরো পর্দার ডাক")
                HToggle("অ্যালার্মের মতো পুরো পর্দা", "ওয়াক্ত শুরু ও শেষ সতর্কতায়, লক স্ক্রিনের ওপরেও", p.fullScreenCall) { p.fullScreenCall = it; changed() }
                if (p.fullScreenCall && !Notifier.canFullScreen(ctx) && Build.VERSION.SDK_INT >= 34) {
                    Text("এই ফোনে পুরো পর্দার জন্য আলাদা অনুমতি লাগবে।", fontSize = 14.sp, color = StatusQaza)
                    OutlinedButton(onClick = {
                        val i = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}"))
                        try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) {
                            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                        }
                    }) { Text("অনুমতি দিন", color = Emerald800) }
                }
                TextButton(onClick = { open("prime") }) { Text("৬০ সেকেন্ডের প্রস্তুতি দেখুন ›", color = Emerald700) }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- ওয়াদা ----------
        SectionCard {
            HTitle("আমার ওয়াদা")
            Text("নিজের হাতে লেখা কথা অন্যের উপদেশের চেয়ে বেশি কাজ করে। ঘরের প্রতিটা ডাকে এটা আপনাকে দেখানো হবে।",
                fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
            var pledge by remember { mutableStateOf(p.pledge) }
            var why by remember { mutableStateOf(p.pledgeWhy) }
            OutlinedTextField(
                pledge, { pledge = it.take(200) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("ওয়াদা") },
                placeholder = { Text("ঘরে একা থাকলেও কোনো ওয়াক্ত ছাড়ব না, ইনশাআল্লাহ") },
            )
            OutlinedTextField(
                why, { why = it.take(200) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("কেন? (নিজের কারণ)") },
                placeholder = { Text("যেন আমার সন্তান আমাকে দেখে নামাজি হয় / কবরে প্রথম প্রশ্ন নামাজ") },
            )
            Button(
                onClick = { p.pledge = pledge; p.pledgeWhy = why; changed(); Toast.makeText(ctx, "ওয়াদা রাখা হলো — আল্লাহ সহজ করুন", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
            ) { Text("রাখুন") }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- জবাবদিহি বন্ধু ----------
        SectionCard {
            HTitle("জবাবদিহি বন্ধু")
            Text("একজন ভাই/বন্ধু/স্ত্রী — যাকে প্রতিদিন রাতে নিজের নামাজের হিসাব পাঠাবেন। কেউ জানবে, এটুকুই অনেক বড় ধাক্কা। " +
                "অ্যাপ নিজে কিছু পাঠায় না — আপনি নিজে হোয়াটসঅ্যাপ/মেসেঞ্জারে পাঠান; শুধু নামাজের হিসাব যায়, আর কিছু নয়।",
                fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
            var name by remember { mutableStateOf(p.partnerName) }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("নাম") }, singleLine = true)
            if (name != p.partnerName) {
                TextButton(onClick = { p.partnerName = name; changed() }) { Text("নাম রাখুন", color = Emerald700) }
            }
            if (p.partnerName.isNotBlank()) {
                HToggle("ইশার পর মনে করিয়ে দেবে", "\"আজকের হিসাব ${p.partnerName}-কে পাঠান\"", p.dailyReport) { p.dailyReport = it; changed() }
                OutlinedButton(onClick = {
                    thread {
                        val text = Report.daily(today, Engine.logs(ctx, today, today)[today], p.partnerName)
                        ctx.startActivity(Notifier.shareIntent(text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("আজকের হিসাব এখনই পাঠান", color = Emerald800) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- সাদাকার বাক্স ----------
        SectionCard {
            HTitle("সাদাকার বাক্স")
            Text("নিজের জন্য নিজে ঠিক করা নিয়ম: যে ওয়াক্ত কাযা হলো বা ছুটে গেল, তার জন্য নির্দিষ্ট টাকা দান। " +
                "ছোট একটা \"দাম\" থাকলে অলসতা হিসাব করে চলে।", fontSize = 14.sp, color = Muted, lineHeight = 20.sp)
            ContentRepo.byId(ctx, "h_tirmidhi_1987")?.let {
                Text("${it.shortBn} — ${it.refBn}", fontSize = 14.sp, color = Emerald800, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            }
            Text("⚠️ এটা কোনো শরয়ী কাফফারা নয় — ছুটে যাওয়া নামাজের কাযা আদায় ফরজই থাকে।",
                fontSize = 13.sp, color = StatusQaza, modifier = Modifier.padding(top = 6.dp))
            if (p.sadaqahPerMiss <= 0) {
                OutlinedButton(onClick = { dialog = "sadaqah" }, modifier = Modifier.padding(top = 6.dp)) { Text("চালু করুন", color = Emerald800) }
            } else {
                val from = p.sadaqahFrom ?: today
                val st = remember(vm.settingsVersion, vm.todayState) {
                    Sadaqah.status(Engine.logs(ctx, from, today), from, today, p.sadaqahPerMiss, p.sadaqahPaid)
                }
                Text("প্রতি ওয়াক্তে ৳${Bn.num(p.sadaqahPerMiss)} · ${Bn.date(from)} থেকে", fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(vertical = 8.dp)) {
                    Stat("কাযা/ছুটে গেছে", Bn.num(st.misses))
                    Stat("নিয়ত", "৳${Bn.num(st.pledged)}")
                    Stat("দিয়েছি", "৳${Bn.num(st.paid)}")
                    Stat("বাকি", "৳${Bn.num(st.due)}")
                }
                Row {
                    Button(onClick = { dialog = "paid" }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700)) { Text("দান করেছি") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { dialog = "sadaqah" }) { Text("পরিমাণ বদলান", color = Muted) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- ঘরের পরিবেশ ----------
        SectionCard {
            HTitle("ঘরের পরিবেশ — ইচ্ছাশক্তির চেয়ে বড়")
            Text("যে কাজ চোখের সামনে আর সহজ, সেটাই হয়। একটা একটা করে টিক দিন:", fontSize = 14.sp, color = Muted)
            val done = p.homeSetupDone
            HomeSetup.ITEMS.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().clickable { p.homeSetupDone = if (i in done) done - i else done + i; changed() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = i in done, onCheckedChange = { p.homeSetupDone = if (it) done + i else done - i; changed() },
                        colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                    Text(s, fontSize = 15.sp, color = Ink, lineHeight = 21.sp)
                }
            }
            Text("${Bn.num(done.size)}/${Bn.num(HomeSetup.ITEMS.size)} হয়েছে", fontSize = 13.sp, color = Emerald700)
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(Modifier.clickable { open("keys") }) {
            Text("নামাজ কঠিন লাগে? — কুরআন-হাদিসের ৮টি চাবি ›", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("\"নিশ্চয়ই তা কঠিন, কিন্তু বিনয়ীদের জন্য নয়\" (২:৪৫) — বিনয়ী কারা, আর কীভাবে হওয়া যায়", fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.height(24.dp))
    }

    val close = { dialog = null }
    when (dialog) {
        "commute" -> {
            val opts = listOf(5, 10, 15, 20, 30, 40, 45, 60, 75, 90, 120)
            HChoice("বাসা থেকে অফিসে যেতে কত সময়?", opts.map { "${Bn.num(it)} মিনিট" }, opts.indexOf(p.workReminders.commuteMin), close) {
                p.workReminders = p.workReminders.copy(commuteMin = opts[it]); changed()
            }
        }
        "start", "end" -> {
            val isStart = dialog == "start"
            val hours = if (isStart) (5..13).toList() else (12..23).toList()
            val cur = (if (isStart) p.workStart else p.workEnd).hour
            HChoice(if (isStart) "অফিস শুরু" else "অফিস শেষ", hours.map { Bn.time(LocalTime.of(it, 0)) }, hours.indexOf(cur), close) {
                if (isStart) p.workStart = LocalTime.of(hours[it], 0) else p.workEnd = LocalTime.of(hours[it], 0)
                changed()
            }
        }
        "sadaqah" -> AmountDialog("প্রতি কাযা/ছুটে যাওয়া ওয়াক্তে কত টাকা?", p.sadaqahPerMiss.takeIf { it > 0 } ?: 10, close) { n ->
            if (p.sadaqahFrom == null || p.sadaqahPerMiss <= 0) p.sadaqahFrom = today
            p.sadaqahPerMiss = n; changed()
        }
        "paid" -> AmountDialog("কত টাকা দান করেছেন?", 0, close) { n ->
            p.sadaqahPaid = p.sadaqahPaid + n; changed()
            Toast.makeText(ctx, "আল্লাহ কবুল করুন", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text(label, fontSize = 11.sp, color = Muted)
    }
}

@Composable
private fun AmountDialog(title: String, initial: Int, onDismiss: () -> Unit, onOk: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (initial > 0) initial.toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { t -> text = t.filter { it.isDigit() }.take(6) },
                prefix = { Text("৳") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.takeIf { it > 0 }?.let(onOk); onDismiss() }) { Text("ঠিক আছে") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } },
    )
}

@Composable
private fun HToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Ink)
            Text(subtitle, fontSize = 14.sp, color = Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = Emerald700))
    }
}

@Composable
private fun HTitle(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun HItem(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Text(title, fontSize = 17.sp, color = Ink)
        Text(subtitle, fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun HChoice(title: String, options: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i); onDismiss() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = { onPick(i); onDismiss() })
                        Text(o)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বন্ধ") } },
    )
}

// =====================================================================
// নামাজ সহজ করার ৮টি চাবি (২:৪৫–৪৬ থেকে শুরু)
// =====================================================================

@Composable
fun EaseKeysScreen(onOpenContent: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("নামাজ কঠিন — কিন্তু কাদের জন্য নয়?", "কুরআন-হাদিস থেকে ৮টি চাবি", onBack)
        ContentRepo.byId(ctx, "q_2_45_46")?.let { head ->
            SectionCard(Modifier.clickable { onOpenContent(head.id) }) {
                Text(head.arabic, fontSize = 20.sp, lineHeight = 36.sp, color = Ink, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(head.bn, fontSize = 16.sp, color = Ink, lineHeight = 24.sp)
                Text("— ${head.refBn}", fontSize = 13.sp, color = Muted)
                Spacer(Modifier.height(8.dp))
                Text(
                    "আয়াতটা নিজেই সমাধান বলে দেয়: নামাজ কঠিন তাদের জন্য, যাদের অন্তরে রবের সামনে দাঁড়ানোর অনুভূতি নেই। " +
                        "নিচের চাবিগুলো সেই অনুভূতি গড়ার বাস্তব উপায়।",
                    fontSize = 14.sp, color = Emerald800, lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        EaseKeys.ALL.forEach { k ->
            var expanded by remember { mutableStateOf(false) }
            SectionCard(Modifier.clickable { expanded = !expanded }) {
                Text(k.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Spacer(Modifier.height(4.dp))
                Text(k.practice, fontSize = 15.sp, color = Ink, lineHeight = 22.sp)
                val items = k.contentIds.mapNotNull { ContentRepo.byId(ctx, it) }.filter { it.id != "q_2_45_46" }
                if (expanded) {
                    items.forEach { c ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenContent(c.id) }.padding(top = 10.dp)) {
                            Text(c.shortBn, fontSize = 15.sp, color = Emerald800, lineHeight = 22.sp)
                            Text("— ${c.refBn}${c.grading?.let { g -> " · $g" } ?: ""}", fontSize = 12.sp, color = Muted)
                        }
                    }
                } else if (items.isNotEmpty()) {
                    Text("${Bn.num(items.size)}টি আয়াত/হাদিস দেখুন ›", fontSize = 13.sp, color = Emerald700, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("আয়াতের অনুবাদ: মুহিউদ্দীন খান। হাদিসের বাংলা ভাবার্থ আলেমের রিভিউ বাকি।", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
    }
}

/** অ্যাপ থেকে খোলা ৬০ সেকেন্ডের প্রস্তুতি — এখন যে ওয়াক্ত চলছে তার জন্য */
@Composable
fun PrimeScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val w = vm.currentWindow()
    val date = Engine.today(ctx)
    PrimingFlow(
        onWudu = {
            if (w != null) thread { Engine.wuduGoing(ctx, date, w.prayer) }
            Toast.makeText(ctx, "১২ মিনিট পরে খোঁজ নেব ইনশাআল্লাহ", Toast.LENGTH_SHORT).show()
            onBack()
        },
        onPrayed = {
            if (w != null) vm.checkIn(date, w.prayer, PrayerStatus.ALONE)
            onBack()
        },
        onClose = onBack,
    )
}

/** v0.18: অফিসের সময় মিনিট ধরে বাছাই (Android-এর নিজস্ব ঘড়ি) */
private fun pickTime(ctx: android.content.Context, cur: LocalTime, onPick: (LocalTime) -> Unit) {
    android.app.TimePickerDialog(ctx, { _, h, m -> onPick(LocalTime.of(h, m)) }, cur.hour, cur.minute, false).show()
}
