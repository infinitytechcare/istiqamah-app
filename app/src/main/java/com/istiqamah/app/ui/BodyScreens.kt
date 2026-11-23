package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Body
import com.istiqamah.app.core.BodyAct
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.FitLevel
import com.istiqamah.app.engine.Engine

/** v0.17: শরীরের হক — আজকের তিন কাজ, সপ্তাহের হিসাব, ধাপ অনুযায়ী ব্যায়াম, কখন করবেন, দলিল */
@Composable
fun BodyScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = vm.prefs
    var version by remember { mutableStateOf(0) }
    val today = remember { Engine.today(ctx) }
    val log = remember(version) { p.bodyLog }
    val level = remember(version) { p.fitLevel }
    val walkOn = remember(version) { p.walkReminder }
    val next = Body.suggestNext(log, level, today)

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        ScreenHeader("শরীরের হক", "শক্তিশালী মুমিন আল্লাহর কাছে বেশি প্রিয়", onBack)

        // আজকের উৎসাহ
        val proof = Body.proofFor(today)
        SectionCard {
            Text("“${proof.text}”", fontSize = 17.sp, color = Emerald800, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            Text("— ${proof.ref}", fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(6.dp))
            Text("নিয়ত: ${Body.NIYYAH}", fontSize = 14.sp, color = Ink)
        }
        Spacer(Modifier.height(12.dp))

        // আজ
        SectionCard {
            Text("আজ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            for (a in BodyAct.entries) {
                val done = a in log[today].orEmpty()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable { p.toggleBody(today, a); version++ }.padding(vertical = 2.dp),
                ) {
                    Checkbox(checked = done, onCheckedChange = { p.toggleBody(today, a); version++ },
                        colors = CheckboxDefaults.colors(checkedColor = Emerald700))
                    Column(Modifier.weight(1f)) {
                        Text("${a.icon} ${a.bn}", fontSize = 16.sp, color = Ink)
                        val sub = when (a) {
                            BodyAct.WALK -> "${Bn.num(level.walkMin)} মিনিট · এ সপ্তাহে ${Bn.num(Body.daysThisWeek(log, a, today))}/৭ দিন" +
                                Body.streak(log, a, today).takeIf { it >= 2 }?.let { " · টানা ${Bn.num(it)} দিন" }.orEmpty()
                            BodyAct.STRENGTH -> "এ সপ্তাহে ${Bn.num(Body.daysThisWeek(log, a, today))}/${Bn.num(Body.STRENGTH_TARGET)} দিন"
                            BodyAct.STAND -> "প্রতি ঘণ্টায় ২–৩ মিনিট হাঁটা বা স্ট্রেচ"
                        }
                        Text(sub, fontSize = 13.sp, color = Muted)
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color(0xFFE6E0D2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ফজরের পর হাঁটার রিমাইন্ডার", fontSize = 16.sp, color = Ink)
                    Text("জামাতের ২৫ মিনিট পরে, সূর্যোদয়ের আগে; হাঁটা হয়ে গেলে আসে না", fontSize = 13.sp, color = Muted)
                }
                Switch(
                    checked = walkOn,
                    onCheckedChange = { p.walkReminder = it; version++; vm.settingsChanged() },
                    colors = SwitchDefaults.colors(checkedTrackColor = Emerald700),
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ধাপ ও ব্যায়াম
        SectionCard {
            Text("শক্তির ব্যায়াম — সপ্তাহে ৩ দিন, ১৫ মিনিট", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in FitLevel.entries) {
                    FilterChip(
                        selected = l == level, onClick = { p.fitLevel = l; version++ },
                        label = { Text(l.bn) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Emerald700, selectedLabelColor = Color.White),
                    )
                }
            }
            next?.let { n ->
                Text("মাশাআল্লাহ! গত ৪ সপ্তাহে নিয়মিত হয়েছে — এবার \"${n.bn}\" ধাপে যেতে পারেন।", fontSize = 13.sp, color = Emerald700)
                TextButton(onClick = { p.fitLevel = n; version++ }) { Text("${n.bn} ধাপে যাই ›", color = Emerald800) }
            }
            for (e in Body.EXERCISES) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(e.how, fontSize = 13.sp, color = Muted, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(e.amount(level), fontSize = 14.sp, color = Emerald800, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("অল্প দিয়ে শুরু করে ধীরে বাড়ান — নিয়মিত অল্প আমল আল্লাহর কাছে বেশি প্রিয়।", fontSize = 13.sp, color = Muted)
        }
        Spacer(Modifier.height(12.dp))

        // কখন করবেন
        SectionCard {
            Text("কখন ও কীভাবে", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            for (t in Body.WHEN) {
                Spacer(Modifier.height(4.dp))
                Text("• $t", fontSize = 14.sp, color = Ink, lineHeight = 21.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // দলিল
        SectionCard {
            Text("কোরআন ও হাদিসে", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
            for (b in Body.PROOFS) {
                Spacer(Modifier.height(8.dp))
                Text(b.text, fontSize = 15.sp, color = Ink, lineHeight = 23.sp)
                Text("— ${b.ref}", fontSize = 12.sp, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text("দলিলগুলো আলেমের রিভিউয়ের তালিকায় আছে।", fontSize = 12.sp, color = Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}
