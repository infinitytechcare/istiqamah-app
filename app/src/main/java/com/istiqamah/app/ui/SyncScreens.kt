package com.istiqamah.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.SyncPair
import com.istiqamah.app.engine.PhoneSync
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** সেটিংস → ম্যাকের সাথে সিঙ্ক: QR স্ক্যান, অবস্থা, এখনই সিঙ্ক, জোড়া ভাঙা */
@Composable
fun MacSyncScreen(vm: AppVm, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUnpair by remember { mutableStateOf<String?>(null) }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        val text = r.contents ?: return@rememberLauncherForActivityResult
        if (SyncPair.parse(text) == null) {
            message = "এটা ইস্তিকামাহ ম্যাকের QR নয়। ম্যাকে মেনু বারের চাঁদ → \"ফোনের সাথে সিঙ্ক…\" খুলে সেই QR স্ক্যান করুন।"
            return@rememberLauncherForActivityResult
        }
        busy = true
        scope.launch {
            val res = vm.pairMac(text)
            busy = false
            message = when (res) {
                PhoneSync.Result.OK -> "জোড়া লেগেছে ✓ এখন থেকে নামাজের চিহ্ন সব জায়গায় মিলে থাকবে।"
                null -> "QR পড়া যায়নি, আবার চেষ্টা করুন।"
                else -> "জোড়া রাখা হয়েছে, কিন্তু এখন ম্যাক পাওয়া যায়নি — ফোন আর ম্যাক একই Wi-Fi-তে আছে কি না দেখুন।"
            }
        }
    }
    fun scan() = scanner.launch(
        ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("ম্যাকের পর্দার QR কোডটা ফ্রেমের ভেতরে ধরুন")
            .setBeepEnabled(false)
            .setOrientationLocked(false),
    )

    key(vm.syncVersion) {
        val p = vm.prefs
        val pairs = p.syncPairs
        Column(
            Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            ScreenHeader("ম্যাকের সাথে সিঙ্ক", "একই Wi-Fi-তে — ইন্টারনেট বা সার্ভার ছাড়া", onBack)

            if (pairs.isEmpty()) {
                SectionCard {
                    Text("ল্যাপটপে কাজ করার সময়ও নামাজের ডাক", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "১. ম্যাকে ইস্তিকামাহ খুলুন → মেনু বারের চাঁদ → \"ফোনের সাথে সিঙ্ক…\"\n" +
                            "২. নিচের বোতাম চেপে ম্যাকের পর্দার QR কোড স্ক্যান করুন\n" +
                            "৩. ফোন আর ম্যাক একই Wi-Fi-তে থাকলেই হবে\n" +
                            "বাসা আর অফিসে আলাদা ম্যাক থাকলে দুটোরই QR একবার করে স্ক্যান করুন।",
                        fontSize = 15.sp, color = Ink, lineHeight = 24.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { scan() }, enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (busy) "জোড়া লাগছে…" else "QR স্ক্যান করুন") }
                }
            } else {
                for (pair in pairs) {
                    SectionCard {
                        Text("✓ ${p.syncMacName(pair.macId).ifBlank { pair.name }}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                        Spacer(Modifier.height(4.dp))
                        val ok = p.syncOk(pair.macId)
                        val at = p.syncAt(pair.macId).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
                        Text(
                            when {
                                at == null -> "এখনো সিঙ্ক হয়নি"
                                else -> "সর্বশেষ সিঙ্ক: ${Bn.date(at.toLocalDate())}, ${Bn.time(at.toLocalTime())}" +
                                    if (!ok) " · এখন এই Wi-Fi-তে নেই" else ""
                            },
                            fontSize = 14.sp, color = if (ok || at == null) Muted else Color(0xFF9A7B2F),
                        )
                        TextButton(onClick = { confirmUnpair = pair.macId }) { Text("এই ম্যাকের জোড়া ভাঙুন", color = Color(0xFFB5452F)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            val r = vm.syncMacNow()
                            busy = false
                            message = if (r == PhoneSync.Result.NOT_FOUND && pairs.size > 1) "এই Wi-Fi-তে কোনো জোড়া ম্যাক পাওয়া যায়নি।" else r.bn
                        }
                    },
                    enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "সিঙ্ক হচ্ছে…" else "এখনই সিঙ্ক করুন") }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { scan() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("আরেকটা ম্যাক যোগ করুন (QR স্ক্যান)") }
                Text(
                    "যে ম্যাক এই Wi-Fi-তে আছে, ফোন তার সাথে নিজে থেকে মেলায়। অফিসে দেওয়া চিহ্ন ফোন হয়ে বাসার ম্যাকে পৌঁছায়।",
                    fontSize = 13.sp, color = Muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }

            message?.let {
                Spacer(Modifier.height(10.dp))
                SectionCard { Text(it, fontSize = 15.sp, color = Ink) }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard {
                Text("কী মেলে, কী মেলে না", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Emerald800)
                Spacer(Modifier.height(6.dp))
                Text(
                    "✓ পাঁচ ওয়াক্তের চিহ্ন — দুই দিকেই। ফোনে দিলে সাথে সাথে ম্যাকে যায়; ম্যাকে দিলে ফোন ডাক বাজার ঠিক আগে খবর নেয়, তাই একই নামাজে দুই জায়গায় ডাক বাজে না।\n" +
                        "✓ জেলা, হিসাবের পদ্ধতি, জামাত, অফিসের সময় ও ডাকের মাত্রা — ফোন থেকে ম্যাকে।\n" +
                        "✗ গুনাহের ট্র্যাকার, যাকাত, দেনা, ওসিয়ত, মুহাসাবা, প্রশ্নের খাতা — কখনো ফোনের বাইরে যায় না।",
                    fontSize = 14.sp, color = Ink, lineHeight = 22.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "সব বার্তা QR-এর গোপন চাবি দিয়ে এনক্রিপ্ট করা, আর শুধু আপনার Wi-Fi-র ভেতরে চলে — ইন্টারনেটে বা কোনো সার্ভারে যায় না।",
                    fontSize = 13.sp, color = Muted, lineHeight = 20.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirmUnpair?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmUnpair = null },
            title = { Text("এই ম্যাকের জোড়া ভাঙবেন?") },
            text = { Text("এই ম্যাকের সাথে চিহ্ন আর মিলবে না। অন্য ম্যাক আর ফোনের সব চিহ্ন যেমন আছে থাকবে।") },
            confirmButton = { TextButton(onClick = { vm.unpairMac(id); confirmUnpair = null; message = null }) { Text("ভাঙুন") } },
            dismissButton = { TextButton(onClick = { confirmUnpair = null }) { Text("থাক") } },
        )
    }

}
