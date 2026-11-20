package com.istiqamah.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.data.ContentRepo
import kotlinx.coroutines.delay

/** ৬০ সেকেন্ডের প্রস্তুতির একটা ধাপ */
private data class PrimeStep(val head: String, val arabic: String = "", val body: String, val ref: String = "")

/**
 * নামাজের আগে ৬০ সেকেন্ড: থামা → রবের সামনে দাঁড়ানোর কথা মনে করা (২:৪৫–৪৬) → বিদায়ী নামাজ →
 * "তিনি দেখছেন" (২৬:২১৮–২১৯) → অলসতা থেকে আশ্রয়ের দোয়া → প্রথম ধাপ।
 * একই ক্রম প্রতিবার — বারবার একই ইশারা আর একই প্রথম কাজ থেকেই অভ্যাস "স্বয়ংক্রিয়" হয়।
 * প্রতিটা ধাপ ১০ সেকেন্ড; ট্যাপ করলে পরের ধাপ।
 */
@Composable
fun PrimingFlow(
    onWudu: () -> Unit,
    onPrayed: () -> Unit,
    onClose: () -> Unit,
    dark: Boolean = false,
) {
    val ctx = LocalContext.current
    val steps = remember {
        fun c(id: String) = ContentRepo.byId(ctx, id)
        listOfNotNull(
            PrimeStep("থামুন", body = "ফোনটা নামিয়ে রাখুন। ধীরে তিনবার শ্বাস নিন।\nআপনি এখন রবের ডাকে সাড়া দিতে যাচ্ছেন।"),
            c("q_2_45_46")?.let { PrimeStep("নামাজ কঠিন — কিন্তু কাদের জন্য নয়?", it.arabic, it.shortBn, it.refBn) },
            c("h_ibnmajah_4171")?.let { PrimeStep("ভাবুন: এটাই হয়তো শেষ নামাজ", body = it.shortBn, ref = it.refBn) },
            c("q_26_218_219")?.let { PrimeStep("ঘরে একা — কিন্তু তিনি দেখছেন", it.arabic, it.shortBn, it.refBn) },
            c("h_bukhari_6369")?.let { PrimeStep("অলসতা থেকে আশ্রয় চান", it.arabic, it.shortBn, it.refBn) },
            PrimeStep("এখন শুধু এক পা", body = "পুরো নামাজের কথা ভাববেন না।\n\"আল্লাহু আকবার\" বলে উঠে দাঁড়ান — শুধু ওযুর জায়গা পর্যন্ত।"),
        )
    }
    var index by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val last = index == steps.lastIndex
    // প্রতি ধাপ ১০ সেকেন্ড — শেষ ধাপে থামে
    LaunchedEffect(index) {
        progress = 0f
        if (last) return@LaunchedEffect
        repeat(100) {
            delay(100)
            progress = (it + 1) / 100f
        }
        index = (index + 1).coerceAtMost(steps.lastIndex)
    }
    val fg = if (dark) Color.White else Ink
    val sub = if (dark) GoldLight else Muted
    val step = steps[index]
    Column(
        Modifier.fillMaxSize().background(if (dark) Emerald900 else Cream).statusBarsPadding().navigationBarsPadding().padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("৬০ সেকেন্ডের প্রস্তুতি", color = sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("বন্ধ", color = sub) }
        }
        LinearProgressIndicator(
            progress = { (index + if (last) 1f else progress) / steps.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Gold, trackColor = if (dark) Emerald700 else Emerald50,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .clickable(enabled = !last) { index = (index + 1).coerceAtMost(steps.lastIndex) }
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(step.head, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            if (step.arabic.isNotBlank()) {
                Text(
                    step.arabic, color = fg, fontSize = 24.sp, lineHeight = 42.sp, textAlign = TextAlign.Center,
                    style = TextStyle(textDirection = TextDirection.Rtl),
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(step.body, color = fg, fontSize = 19.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            if (step.ref.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("— ${step.ref}", color = sub, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            if (!last) {
                Spacer(Modifier.height(20.dp))
                Text("ট্যাপ করলে পরের ধাপ", color = sub, fontSize = 12.sp)
            }
        }
        if (last) {
            Button(
                onClick = onWudu, modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald900),
            ) { Text("ওযু করতে যাচ্ছি", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPrayed, modifier = Modifier.fillMaxWidth()) { Text("পড়ে ফেলেছি ✓", color = fg) }
        }
    }
}
