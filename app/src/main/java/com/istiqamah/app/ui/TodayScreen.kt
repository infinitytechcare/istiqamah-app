package com.istiqamah.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.engine.Engine
import com.istiqamah.app.notify.Notifier
import java.time.Duration
import java.time.LocalDate

private data class SheetTarget(val date: LocalDate, val prayer: Prayer, val isPast: Boolean)

@Composable
fun TodayScreen(vm: AppVm, onOpenContent: (String) -> Unit, onOpenBattery: () -> Unit) {
    val t = vm.todayState ?: return
    var sheet by remember { mutableStateOf<SheetTarget?>(null) }

    LazyColumn(Modifier.fillMaxSize().background(Cream)) {
        item { Header(vm) }
        item { Spacer(Modifier.height(12.dp)) }

        t.celebration?.let { msg ->
            item {
                Card16 {
                    Text(msg, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Emerald800, lineHeight = 26.sp)
                    TextButton(onClick = { vm.dismissCelebration() }) { Text("আলহামদুলিল্লাহ") }
                }
            }
        }

        item { PermissionWarnings(t, onOpenBattery) }

        item {
            Card16 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ধাপ ${Bn.num(t.stage.stage.number)} · ${t.stage.stage.bn}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                        Text(t.stage.stage.subtitle, fontSize = 14.sp, color = Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Bn.num(t.streak), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold)
                        Text("দিনের ধারা", fontSize = 12.sp, color = Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "লক্ষ্য: " + t.stage.focus.sortedBy { it.ordinal }.joinToString(" · ") { it.bn },
                    fontSize = 15.sp, color = Ink,
                )
            }
        }

        item {
            Card16 {
                Text("আজকের নামাজ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Spacer(Modifier.height(6.dp))
                t.windows.forEach { w ->
                    PrayerRow(w, t.log.status(w.prayer), w.prayer in t.stage.focus, vm.now.isBefore(w.start)) {
                        sheet = SheetTarget(t.today, w.prayer, isPast = !vm.now.isBefore(w.end))
                    }
                }
            }
        }

        if (t.yesterdayPending.isNotEmpty()) {
            item {
                Card16 {
                    Text("গতকালের হিসাব বাকি", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Text("ঠিক করে লিখে রাখলে আপনার অগ্রগতি সঠিক থাকবে।", fontSize = 14.sp, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        t.yesterdayPending.forEach { p ->
                            Text(
                                p.bn,
                                modifier = Modifier
                                    .background(Emerald50, RoundedCornerShape(10.dp))
                                    .clickable { sheet = SheetTarget(t.today.minusDays(1), p, isPast = true) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                color = Emerald800, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        t.daily?.let { item { DailyCard(it) { onOpenContent(it.id) } } }

        if (t.unreviewed > 0) {
            item {
                Text(
                    "পরীক্ষামূলক সংস্করণ: ${Bn.num(t.unreviewed)}টি আয়াত/হাদিসের অনুবাদ ও সূত্র এখনো আলেমের রিভিউ পায়নি।",
                    fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    sheet?.let { s ->
        StatusSheet(
            date = s.date, prayer = s.prayer,
            current = if (s.date == t.today) t.log.status(s.prayer) else null,
            isPast = s.isPast,
            onPick = { st -> vm.checkIn(s.date, s.prayer, st); sheet = null },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun Header(vm: AppVm) {
    val t = vm.todayState ?: return
    val cur = vm.currentWindow()
    val next = vm.nextWindow()
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Emerald900, Emerald700)))
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        Text(Bn.date(t.today), color = Color.White, fontSize = 16.sp)
        Text(
            Bn.hijri(t.today, vm.prefs.hijriAdjust) + " · " + vm.prefs.placeName,
            color = GoldLight, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        if (cur != null && t.log.status(cur.prayer) == null) {
            val left = Duration.between(vm.now, cur.end).toMinutes()
            Text("এখন ${cur.prayer.bn}-এর সময়", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("শেষ হবে ${Bn.time(cur.end.toLocalTime())} — আর ${Bn.duration(left)}", color = GoldLight, fontSize = 16.sp)
        } else if (next != null) {
            val left = Duration.between(vm.now, next.start).toMinutes()
            Text("পরের নামাজ: ${next.prayer.bn}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("${Bn.time(next.start.toLocalTime())} — আর ${Bn.duration(left)}", color = GoldLight, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PrayerRow(w: PrayerWindow, status: com.istiqamah.app.core.PrayerStatus?, isFocus: Boolean, notStarted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !notStarted, onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, 14)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                w.prayer.bn + if (isFocus) "  ★" else "",
                fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (notStarted) Muted else Ink,
            )
            Text("${Bn.time(w.start.toLocalTime())} – ${Bn.time(w.end.toLocalTime())}", fontSize = 13.sp, color = Muted)
        }
        Text(
            when {
                status != null -> status.bn
                notStarted -> "এখনো শুরু হয়নি"
                else -> "হিসাব দিন"
            },
            fontSize = 14.sp,
            color = if (status == null && !notStarted) Emerald700 else Muted,
            fontWeight = if (status == null && !notStarted) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DailyCard(item: ContentItem, onClick: () -> Unit) {
    Card16(Modifier.clickable(onClick = onClick)) {
        Text("আজকের ${if (item.type == "hadith") "হাদিস" else "আয়াত"}", fontSize = 14.sp, color = Gold, fontWeight = FontWeight.Bold)
        if (item.arabic.isNotBlank()) {
            Text(
                item.arabic, fontFamily = Amiri, fontSize = 22.sp, color = Emerald900, lineHeight = 40.sp,
                textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(item.bn, fontSize = 16.sp, color = Ink, lineHeight = 24.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Text("— ${item.refBn}", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun PermissionWarnings(@Suppress("UNUSED_PARAMETER") refresh: TodayState, onOpenBattery: () -> Unit) {
    val ctx = LocalContext.current
    // অনুমতি দেওয়ার পর ফিরে এলে আবার যাচাই হবে (onResume → refresh)
    val notifOk = Notifier.canPost(ctx)
    val exactOk = Engine.canExact(ctx)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (notifOk && exactOk) return
    Card16 {
        Text("রিমাইন্ডার বন্ধ হয়ে আছে", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        if (!notifOk) {
            Text("নোটিফিকেশনের অনুমতি নেই।", fontSize = 14.sp, color = Muted)
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
            ) { Text("অনুমতি দিন") }
        }
        if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("নির্দিষ্ট সময়ে অ্যালার্মের অনুমতি নেই — রিমাইন্ডার কয়েক মিনিট দেরিতে আসতে পারে।", fontSize = 14.sp, color = Muted)
            Button(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
            ) { Text("অনুমতি দিন") }
        }
        TextButton(onClick = onOpenBattery) { Text("ব্যাটারি গাইড দেখুন") }
    }
}

@Composable
fun Card16(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SectionCard(modifier.padding(horizontal = 16.dp, vertical = 6.dp), content)
}
