package com.istiqamah.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import java.time.DayOfWeek
import java.time.LocalDate

private val weekHeader = listOf("শনি", "রবি", "সোম", "মঙ্গল", "বুধ", "বৃহঃ", "শুক্র")

@Composable
fun CalendarScreen(vm: AppVm) {
    val today = vm.todayState?.today ?: return
    var dayOpen by remember { mutableStateOf<LocalDate?>(null) }
    var sheetFor by remember { mutableStateOf<Pair<LocalDate, Prayer>?>(null) }
    val month = vm.month

    Column(Modifier.fillMaxSize().background(Cream).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "আগের মাস") }
            Text(Bn.monthYear(month.atDay(1)), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Emerald800, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(
                onClick = { vm.showMonth(month.plusMonths(1)) },
                enabled = !month.plusMonths(1).atDay(1).isAfter(today),
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "পরের মাস") }
        }
        Spacer(Modifier.height(8.dp))

        SectionCard {
            Row { weekHeader.forEach { Text(it, Modifier.weight(1f), fontSize = 12.sp, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            Spacer(Modifier.height(6.dp))
            // শনিবার থেকে সপ্তাহ শুরু
            val first = month.atDay(1)
            val lead = (first.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
            val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row {
                    week.forEach { d -> DayCell(d, today, vm.monthLogs[d]?.statuses ?: emptyMap(), Modifier.weight(1f)) { dayOpen = it } }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Legend()
        }

        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("এই মাসে সময়মতো", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            val days = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isAfter(today) }
            Prayer.entries.forEach { p ->
                val counted = days.count { vm.monthLogs[it]?.status(p) != PrayerStatus.EXCUSED }
                val onTime = days.count { vm.monthLogs[it]?.status(p)?.onTime == true }
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.bn, Modifier.width(70.dp), fontSize = 16.sp, color = Ink)
                    Box(Modifier.weight(1f).height(10.dp).background(Emerald50, RoundedCornerShape(5.dp))) {
                        val frac = if (counted == 0) 0f else onTime.toFloat() / counted
                        Box(Modifier.fillMaxWidth(frac).height(10.dp).background(StatusJamaah, RoundedCornerShape(5.dp)))
                    }
                    Text("${Bn.num(onTime)}/${Bn.num(counted)}", Modifier.width(64.dp).padding(start = 8.dp), fontSize = 14.sp, color = Muted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard {
            Text("কাযার হিসাব", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Text("\"পড়া হয়নি\" দিলে এখানে যোগ হয়। আগের বছরগুলোর কাযা থাকলে নিজে সংখ্যা ঠিক করে নিন; প্রতিদিন একটা করে আদায় করলেও যথেষ্ট।", fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
            Prayer.entries.forEach { p ->
                val n = vm.qaza[p] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.bn, Modifier.weight(1f), fontSize = 16.sp)
                    TextButton(onClick = { vm.setQaza(p, n - 1) }, enabled = n > 0) { Text("−", fontSize = 20.sp) }
                    Text(Bn.num(n), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    TextButton(onClick = { vm.setQaza(p, n + 1) }) { Text("+", fontSize = 20.sp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    dayOpen?.let { d ->
        DayDialog(d, vm, onPick = { p -> sheetFor = d to p }, onDismiss = { dayOpen = null })
    }
    sheetFor?.let { (d, p) ->
        StatusSheet(
            date = d, prayer = p, current = vm.monthLogs[d]?.status(p),
            isPast = d.isBefore(today) || (vm.todayState?.windows?.firstOrNull { it.prayer == p }?.let { !vm.now.isBefore(it.end) } == true),
            onPick = { s -> vm.checkIn(d, p, s); sheetFor = null },
            onDismiss = { sheetFor = null },
        )
    }
}

@Composable
private fun DayCell(d: LocalDate?, today: LocalDate, statuses: Map<Prayer, PrayerStatus>, modifier: Modifier, onClick: (LocalDate) -> Unit) {
    Column(
        modifier
            .aspectRatio(0.8f)
            .padding(2.dp)
            .background(if (d == today) Emerald50 else Color.Transparent, RoundedCornerShape(8.dp))
            .then(if (d != null && !d.isAfter(today)) Modifier.clickable { onClick(d) } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (d == null) return@Column
        Text(Bn.num(d.dayOfMonth), fontSize = 14.sp, color = if (d.isAfter(today)) Muted else Ink, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(3.dp))
        if (!d.isAfter(today)) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) { Prayer.entries.forEach { StatusDot(statuses[it], 5) } }
        }
    }
}

@Composable
private fun Legend() {
    val items = listOf(PrayerStatus.JAMAAH, PrayerStatus.ALONE, PrayerStatus.QAZA, PrayerStatus.MISSED, null)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(s, 8)
                Text(" " + (s?.bn?.substringBefore(",") ?: "হিসাব নেই"), fontSize = 11.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun DayDialog(d: LocalDate, vm: AppVm, onPick: (Prayer) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("ঠিক আছে") } },
        title = { Text(Bn.date(d)) },
        text = {
            Column {
                Prayer.entries.forEach { p ->
                    val s = vm.monthLogs[d]?.status(p)
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(s, 12)
                        Text("  ${p.bn}", Modifier.weight(1f), fontSize = 17.sp)
                        Text(s?.bn ?: "হিসাব দিন", color = if (s == null) Emerald700 else Muted)
                    }
                    HorizontalDivider(color = Divider)
                }
            }
        },
        containerColor = Cream,
    )
}
