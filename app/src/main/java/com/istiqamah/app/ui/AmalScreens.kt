package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.AzkarItem
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.SIN_PRESETS
import com.istiqamah.app.core.TriggerTag
import com.istiqamah.app.core.Weekly
import com.istiqamah.app.data.AzkarRepo
import kotlinx.coroutines.launch

// =====================================================================
// "আমল" ট্যাব — সব নতুন ফিচারের প্রবেশপথ
// =====================================================================

@Composable
fun AmalScreen(vm: AppVm, open: (String) -> Unit, requestUnlock: (() -> Unit) -> Unit) {
    val t = vm.todayState ?: return
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("আমল", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("নামাজের পরের ধাপ — যিকির, নিজের হিসাব আর গুনাহ ছাড়ার লড়াই", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text("আযকার", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            AzkarSet.entries.forEach { s ->
                val done = s in t.azkarDone
                val frac = vm.azkarCompletion(s)
                Row(
                    Modifier.fillMaxWidth().clickable { open("azkar:${s.name}") }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.bn, fontSize = 17.sp, color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(s.whenBn, fontSize = 12.sp, color = Muted)
                        if (s == AzkarSet.MORNING || s == AzkarSet.EVENING) {
                            LinearProgressIndicator(
                                progress = { if (done) 1f else frac },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp),
                                color = StatusJamaah, trackColor = Emerald50,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (done) "✓" else "›", fontSize = 22.sp, color = if (done) StatusJamaah else Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(Modifier.clickable { open("muhasaba") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("রাতের মুহাসাবা", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Text(
                        if (t.muhasabaDone) "আজকের হিসাব হয়ে গেছে ✓" else "৯০ সেকেন্ডে আজকের হিসাব",
                        fontSize = 14.sp, color = if (t.muhasabaDone) StatusJamaah else Muted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Bn.num(t.muhasabaStreak), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
                    Text("দিনের ধারা", fontSize = 11.sp, color = Muted)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(Modifier.clickable { requestUnlock { open("sins") } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("গুনাহ ছাড়ার লড়াই", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Text("শুধু আপনার জন্য — লেখা এনক্রিপ্ট করা, ঢুকতে ফোনের লক", fontSize = 13.sp, color = Muted)
                }
                Icon(Icons.Outlined.Lock, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard(Modifier.clickable { open("weekly") }) {
            Text("সাপ্তাহিক পর্যালোচনা", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text(
                t.weeklyGoal?.let { "এ সপ্তাহের লক্ষ্য: $it" } ?: "গত ৭ দিনের হিসাব আর পরের সপ্তাহের একটা লক্ষ্য",
                fontSize = 14.sp, color = Muted,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "ফিরে যান", tint = Emerald800) }
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            subtitle?.let { Text(it, fontSize = 13.sp, color = Muted) }
        }
    }
}

// =====================================================================
// আযকার — একটা একটা করে, ট্যাপে গণনা
// =====================================================================

@Composable
fun AzkarScreen(vm: AppVm, set: AzkarSet, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val items = remember(set) { vm.azkarItems(set) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var finished by remember { mutableStateOf(false) }
    val counts = vm.azkarCounts
    val frac = vm.azkarCompletion(set)

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 8.dp)) {
            ScreenHeader(set.bn, set.whenBn, onBack)
            LinearProgressIndicator(
                progress = { frac },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(6.dp),
                color = StatusJamaah, trackColor = Emerald50,
            )
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                val c = counts[item.id] ?: 0
                AzkarCard(item, c) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val setDone = vm.azkarTap(item)
                    if (c + 1 >= item.count) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (index + 1 < items.size) scope.launch { listState.animateScrollToItem(index + 1) }
                    }
                    if (setDone) finished = true
                }
            }
            item {
                Text(
                    AzkarRepo.attribution + "\nবাংলা উচ্চারণ, অর্থ ও সংখ্যা: আলেমের রিভিউ বাকি।",
                    fontSize = 11.sp, color = Muted, modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
    }

    if (finished) {
        AlertDialog(
            onDismissRequest = { finished = false; onBack() },
            confirmButton = { TextButton(onClick = { finished = false; onBack() }) { Text("আলহামদুলিল্লাহ") } },
            title = { Text("${set.bn} সম্পূর্ণ") },
            text = { Text("আল্লাহ কবুল করুন। “আমাকে স্মরণ কর, আমিও তোমাদের স্মরণ রাখবো।” (২:১৫২)") },
            containerColor = Cream,
        )
    }
}

@Composable
private fun AzkarCard(item: AzkarItem, count: Int, onTap: () -> Unit) {
    val done = count >= item.count
    var showMeaning by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(if (done) Emerald50 else Color.White, RoundedCornerShape(18.dp))
            .clickable(enabled = !done, onClick = onTap)
            .padding(16.dp)
            .animateContentSize()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.titleBn, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Emerald800)
            Box(
                Modifier.size(52.dp).background(if (done) StatusJamaah else Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (done) "✓" else "${Bn.num(count)}/${Bn.num(item.count)}",
                    color = if (done) Color.White else Emerald900, fontWeight = FontWeight.Bold,
                    fontSize = if (done) 22.sp else 13.sp,
                )
            }
        }
        if (!done) {
            Text(
                item.arabic, fontFamily = Amiri, fontSize = 22.sp, lineHeight = 42.sp, color = Emerald900,
                textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            if (item.pronunciationBn.isNotBlank()) {
                Text("উচ্চারণ: ${item.pronunciationBn}", fontSize = 15.sp, lineHeight = 23.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
            }
            if (showMeaning) {
                Text("অর্থ: ${item.meaningBn}", fontSize = 15.sp, lineHeight = 23.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
            if (item.noteBn.isNotBlank()) {
                Text("ফজিলত: ${item.noteBn}", fontSize = 13.sp, lineHeight = 20.sp, color = Emerald700, modifier = Modifier.padding(top = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${item.refBn} · ${item.grading}", Modifier.weight(1f), fontSize = 11.sp, color = Muted)
                TextButton(onClick = { showMeaning = !showMeaning }) { Text(if (showMeaning) "অর্থ লুকান" else "অর্থ দেখুন") }
            }
            Text("কার্ডে ট্যাপ করে গুনুন", fontSize = 11.sp, color = Muted)
        }
    }
}

// =====================================================================
// রাতের মুহাসাবা
// =====================================================================

private val GOOD_PRESETS = listOf("নামাজ সময়মতো", "কুরআন পড়েছি", "যিকির করেছি", "দান করেছি", "বাবা-মাকে সাহায্য", "কারো উপকার", "রাগ সামলেছি", "হালাল উপার্জন")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuhasabaScreen(vm: AppVm, requestUnlock: (() -> Unit) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val t = vm.todayState ?: return
    val priv = vm.privateState
    var goodChips by remember { mutableStateOf(setOf<String>()) }
    var goodText by remember { mutableStateOf("") }
    var slipText by remember { mutableStateOf("") }
    var slipGoals by remember { mutableStateOf(setOf<Long>()) }
    var tomorrow by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    // আগে লেখা থাকলে (আনলক করা অবস্থায়) ফর্মে দেখাই
    LaunchedEffect(priv?.muhasabaToday) {
        priv?.muhasabaToday?.let { e ->
            val parts = e.good.split(" · ")
            goodChips = parts.filter { it in GOOD_PRESETS }.toSet()
            goodText = parts.filterNot { it in GOOD_PRESETS }.joinToString(" · ")
            slipText = e.slip
            tomorrow = e.tomorrow
        }
    }

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("রাতের মুহাসাবা", Bn.date(t.today), onBack)
        Text(
            "“হে মুমিনগণ, আল্লাহকে ভয় কর। প্রত্যেক ব্যক্তির উচিত, আগামী কালের জন্যে সে কি প্রেরণ করে, তা চিন্তা করা।” — সূরা আল-হাশর ৫৯:১৮",
            fontSize = 14.sp, color = Emerald700, lineHeight = 21.sp,
        )
        if (t.muhasabaDone && priv == null) {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("আজকের মুহাসাবা হয়ে গেছে ✓", fontWeight = FontWeight.Bold, color = StatusJamaah)
                Text("লেখাগুলো দেখতে বা বদলাতে আনলক করুন।", fontSize = 14.sp, color = Muted)
                TextButton(onClick = { requestUnlock { } }) { Text("আনলক করুন") }
            }
        }
        Spacer(Modifier.height(12.dp))

        SectionCard {
            Text("১. আজ আল্লাহকে খুশি করার মতো কী করেছি?", fontWeight = FontWeight.Bold, color = Emerald800)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                GOOD_PRESETS.forEach { g -> Chip(g, g in goodChips) { goodChips = if (g in goodChips) goodChips - g else goodChips + g } }
            }
            OutlinedTextField(goodText, { goodText = it }, label = { Text("নিজে লিখুন (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Text("২. কোথায় ভুল হয়েছে?", fontWeight = FontWeight.Bold, color = Emerald800)
            Text("সৎভাবে লিখুন — শুধু আপনি আর আল্লাহ জানবেন। লেখা এনক্রিপ্ট হয়ে থাকে।", fontSize = 13.sp, color = Muted)
            val goals = priv?.goals.orEmpty()
            if (goals.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    goals.forEach { g -> Chip(g.title, g.id in slipGoals) { slipGoals = if (g.id in slipGoals) slipGoals - g.id else slipGoals + g.id } }
                }
            } else if (priv == null) {
                TextButton(onClick = { requestUnlock { } }) { Text("নিজের লক্ষ্যগুলো দেখতে আনলক করুন") }
            }
            OutlinedTextField(slipText, { slipText = it }, label = { Text("নিজে লিখুন (ঐচ্ছিক)") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        SectionCard {
            Text("৩. কাল একটা জিনিস কী বদলাব?", fontWeight = FontWeight.Bold, color = Emerald800)
            OutlinedTextField(tomorrow, { tomorrow = it }, label = { Text("যেমন: ফজরের অ্যালার্ম বিছানা থেকে দূরে রাখব") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val good = (goodChips.toList() + listOfNotNull(goodText.trim().ifBlank { null })).joinToString(" · ")
                vm.saveMuhasaba(good, slipText.trim(), tomorrow.trim(), slipGoals)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
        ) { Text("সংরক্ষণ করে তাওবা করি", fontSize = 16.sp) }
        Spacer(Modifier.height(24.dp))
    }

    if (saved) {
        val istighfar = remember { vm.azkarItems(AzkarSet.MORNING).firstOrNull { it.id.startsWith("me_sayyidul_istighfar") } }
        AlertDialog(
            onDismissRequest = { saved = false; onBack() },
            confirmButton = { TextButton(onClick = { saved = false; onBack() }) { Text("আমিন") } },
            title = { Text("আল্লাহর কাছে ফিরে আসুন") },
            containerColor = Cream,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("“প্রত্যেক আদম সন্তানই ভুল করে; আর ভুলকারীদের মধ্যে সর্বোত্তম তারা, যারা তাওবা করে।” — তিরমিযি ২৪৯৯", fontSize = 14.sp, color = Emerald700, lineHeight = 21.sp)
                    istighfar?.let {
                        Spacer(Modifier.height(10.dp))
                        Text("সাইয়্যিদুল ইস্তিগফার পড়ে ঘুমান:", fontWeight = FontWeight.Bold)
                        Text(it.arabic, fontFamily = Amiri, fontSize = 19.sp, lineHeight = 36.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        Text(it.pronunciationBn, fontSize = 14.sp, lineHeight = 21.sp)
                        Text(it.meaningBn, fontSize = 13.sp, lineHeight = 20.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(if (selected) Emerald700 else Emerald50, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = if (selected) Color.White else Emerald800,
        fontSize = 14.sp,
    )
}

// =====================================================================
// গুনাহ ছাড়ার লড়াই (লক করা অংশ)
// =====================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SinsScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val priv = vm.privateState
    var adding by remember { mutableStateOf(false) }
    var slipFor by remember { mutableStateOf<Long?>(null) }
    var comfort by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("গুনাহ ছাড়ার লড়াই", "সর্বোচ্চ ৩টা লক্ষ্য — একটা একটা করে জয়", onBack)
        if (priv == null) {
            Text("লোড হচ্ছে…", color = Muted)
            return@Column
        }
        if (priv.goals.isEmpty()) {
            SectionCard {
                Text("কোন অভ্যাসটা ছাড়তে চান?", fontWeight = FontWeight.Bold, color = Emerald800, fontSize = 17.sp)
                Text("ছোট থেকে শুরু করুন। কোনো নোটিফিকেশনে এর নাম কখনো দেখানো হবে না।", fontSize = 14.sp, color = Muted)
            }
        }
        priv.goals.forEach { g ->
            val st = priv.stats[g.id]
            Spacer(Modifier.height(10.dp))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                        Text("শুরু ${Bn.date(g.startedOn)}", fontSize = 12.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Bn.num(st?.cleanDays ?: 0), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Text("দিন পবিত্র", fontSize = 12.sp, color = Muted)
                    }
                }
                Text(
                    "সেরা ধারা ${Bn.num(st?.bestCleanDays ?: 0)} দিন · গত ৩০ দিনে ভুল ${Bn.num(st?.eventsLast30 ?: 0)} বার",
                    fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp),
                )
                st?.insights?.takeIf { it.isNotEmpty() }?.let { ins ->
                    Column(Modifier.padding(top = 8.dp).fillMaxWidth().background(Emerald50, RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Text("প্যাটার্ন যা দেখা যাচ্ছে", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Emerald800)
                        ins.forEach { Text("• $it", fontSize = 14.sp, lineHeight = 21.sp) }
                        Text("এই সময়/পরিস্থিতিগুলো আগে থেকে এড়ানোর পরিকল্পনা করুন।", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { slipFor = g.id }) { Text("আজ ভুল হয়ে গেছে") }
                    TextButton(onClick = { vm.archiveSinGoal(g.id) }) { Text("লক্ষ্য সরান", color = Muted) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (priv.goals.size < 3) {
            Button(onClick = { adding = true }, colors = ButtonDefaults.buttonColors(containerColor = Emerald700), modifier = Modifier.fillMaxWidth()) {
                Text("নতুন লক্ষ্য যোগ করুন")
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "“যারা বলে, আমাদের পালনকর্তা আল্লাহ, অতঃপর তাতেই অবিচল থাকে, তাদের কাছে ফেরেশতা অবতীর্ণ হয়।” — ৪১:৩০",
            fontSize = 13.sp, color = Emerald700, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        var custom by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            confirmButton = {
                TextButton(onClick = { vm.addSinGoal(custom); adding = false }, enabled = custom.isNotBlank()) { Text("যোগ করুন") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("বাতিল") } },
            title = { Text("কী ছাড়তে চান?") },
            containerColor = Cream,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SIN_PRESETS.forEach { p -> Chip(p, custom == p) { custom = p } }
                    }
                    OutlinedTextField(custom, { custom = it }, label = { Text("বা নিজে লিখুন") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                }
            },
        )
    }

    slipFor?.let { gid ->
        var tags by remember(gid) { mutableStateOf(setOf<TriggerTag>()) }
        AlertDialog(
            onDismissRequest = { slipFor = null },
            confirmButton = {
                TextButton(onClick = { vm.recordSlip(gid, tags); slipFor = null; comfort = true }) { Text("লিখে রাখি") }
            },
            dismissButton = { TextButton(onClick = { slipFor = null }) { Text("বাতিল") } },
            title = { Text("কী পরিস্থিতিতে হয়েছিল?") },
            containerColor = Cream,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("ঐচ্ছিক — বারবার কোন পরিস্থিতিতে হয় বুঝতে সাহায্য করবে।", fontSize = 13.sp, color = Muted)
                    TriggerTag.entries.groupBy { it.group }.forEach { (group, list) ->
                        Text(group, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { tg -> Chip(tg.bn, tg in tags) { tags = if (tg in tags) tags - tg else tags + tg } }
                        }
                    }
                }
            },
        )
    }

    if (comfort) {
        AlertDialog(
            onDismissRequest = { comfort = false },
            confirmButton = { TextButton(onClick = { comfort = false }) { Text("আবার শুরু করি") } },
            title = { Text("হতাশ হবেন না") },
            containerColor = Cream,
            text = {
                Text(
                    "“তোমরা আল্লাহর রহমত থেকে নিরাশ হয়ো না। নিশ্চয় আল্লাহ সমস্ত গোনাহ মাফ করেন।” (৩৯:৫৩)\n\n" +
                        "এখনই দুই রাকাত নামাজ পড়ে আন্তরিকভাবে তাওবা করুন। আজ থেকে নতুন ধারা শুরু।",
                    lineHeight = 22.sp,
                )
            },
        )
    }
}

// =====================================================================
// সাপ্তাহিক পর্যালোচনা
// =====================================================================

@Composable
fun WeeklyScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { vm.loadWeekly() }
    val r = vm.weekly
    val t = vm.todayState
    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("সাপ্তাহিক পর্যালোচনা", r?.let { "${Bn.date(it.from)} – ${Bn.date(it.to)}" }, onBack)
        if (r == null) {
            Text("হিসাব হচ্ছে…", color = Muted)
            return@Column
        }
        SectionCard {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Bn.num(r.onTimeTotal), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Text(" / ৩৫ ওয়াক্ত সময়মতো", fontSize = 16.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                when {
                    r.change > 0 -> "গত সপ্তাহের চেয়ে ${Bn.num(r.change)} ওয়াক্ত বেশি ↑ আলহামদুলিল্লাহ"
                    r.change < 0 -> "গত সপ্তাহের চেয়ে ${Bn.num(-r.change)} ওয়াক্ত কম ↓ — নতুন সপ্তাহে আবার শুরু"
                    else -> "গত সপ্তাহের সমান"
                },
                color = if (r.change >= 0) StatusJamaah else StatusQaza, fontWeight = FontWeight.SemiBold,
            )
            Text("জামাতে ${Bn.num(r.jamaahTotal)} ওয়াক্ত · মুহাসাবা ${Bn.num(r.muhasabaDays)} দিন · আযকার ${Bn.num(r.azkarSessions)} বার",
                fontSize = 14.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("ওয়াক্ত অনুযায়ী (৭ দিনে)", fontWeight = FontWeight.Bold, color = Emerald800)
            Prayer.entries.forEach { p ->
                val n = r.onTime.getValue(p)
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t?.prayerName(p) ?: p.bn, Modifier.width(92.dp), fontSize = 15.sp, color = if (p == r.hardest) StatusQaza else Ink,
                        fontWeight = if (p == r.hardest) FontWeight.Bold else FontWeight.Normal)
                    Box(Modifier.weight(1f).height(10.dp).background(Emerald50, RoundedCornerShape(5.dp))) {
                        Box(Modifier.fillMaxWidth(n / 7f).height(10.dp).background(StatusJamaah, RoundedCornerShape(5.dp)))
                    }
                    Text("${Bn.num(n)}/৭", Modifier.width(44.dp).padding(start = 8.dp), fontSize = 14.sp, color = Muted)
                }
            }
            r.hardest?.let { Text("সবচেয়ে কঠিন: ${it.bn}", color = StatusQaza, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("পরের সপ্তাহের একটা লক্ষ্য", fontWeight = FontWeight.Bold, color = Emerald800)
            Text("একটাই — ছোট আর নির্দিষ্ট। হোম স্ক্রিনে সারা সপ্তাহ দেখাবে।", fontSize = 13.sp, color = Muted)
            Weekly.suggestions(r.hardest).forEach { s ->
                val selected = t?.weeklyGoal == s
                Text(
                    (if (selected) "✓ " else "") + s,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(if (selected) Emerald50 else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { vm.setWeeklyGoal(s) }
                        .padding(12.dp),
                    color = if (selected) Emerald800 else Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
