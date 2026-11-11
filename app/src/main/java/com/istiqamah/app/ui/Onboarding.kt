package com.istiqamah.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.data.Cities
import java.time.ZoneId

private const val STEPS = 6

@Composable
fun OnboardingScreen(vm: AppVm) {
    var step by remember { mutableIntStateOf(0) }
    var place by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf<Location?>(null) }
    var avg by remember { mutableIntStateOf(-1) }
    var first by remember { mutableStateOf(Prayer.MAGHRIB) }
    var intensity by remember { mutableStateOf(Intensity.MEDIUM) }

    BackHandler(enabled = step > 0) { step-- }

    Column(
        Modifier.fillMaxSize().background(Cream).statusBarsPadding().navigationBarsPadding().padding(20.dp)
    ) {
        if (step > 0) {
            LinearProgressIndicator(
                progress = { step / (STEPS - 1).toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Emerald700, trackColor = Emerald50,
            )
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.weight(1f)) {
            when (step) {
                0 -> Welcome()
                1 -> LocationStep(place) { p, l -> place = p; loc = l }
                2 -> Question(
                    "গত সপ্তাহে দিনে গড়ে কয় ওয়াক্ত নামাজ পড়েছেন?",
                    "সৎভাবে উত্তর দিন — এটা শুধু আপনার ফোনে থাকবে, কেউ দেখবে না। এখান থেকেই আপনার যাত্রা শুরু হবে।",
                    listOf(0 to "এখনো পড়া হয় না", 1 to "১–২ ওয়াক্ত", 3 to "৩–৪ ওয়াক্ত", 5 to "পাঁচ ওয়াক্তই"),
                    avg,
                ) { avg = it }
                3 -> if (avg >= 5) {
                    Info("মাশাআল্লাহ!", "আপনি ধাপ ৩ (দৃঢ় হওয়া) থেকে শুরু করবেন। লক্ষ্য: পাঁচ ওয়াক্ত সময়মতো, বিশেষ করে ফজর।")
                } else {
                    Question(
                        "কোন ওয়াক্ত দিয়ে শুরু করতে চান?",
                        "একটা ওয়াক্ত দিয়ে শুরু করাই সবচেয়ে কার্যকর। বেশিরভাগ মানুষের জন্য মাগরিব সবচেয়ে সহজ — সময় কম, সবাই বাসায় থাকে।",
                        Prayer.entries.map { it.ordinal to it.bn + if (it == Prayer.MAGHRIB) " (প্রস্তাবিত)" else "" },
                        first.ordinal,
                    ) { first = Prayer.fromOrdinal(it) }
                }
                4 -> Question(
                    "কতটা মনে করিয়ে দেব?",
                    "পরে সেটিংস থেকে বদলাতে পারবেন। একটা ওয়াক্ত টানা ১৪ দিন সময়মতো পড়লে সেটার বাড়তি রিমাইন্ডার নিজেই কমে যাবে।",
                    Intensity.entries.map { it.code to "${it.bn} — ${it.desc}" },
                    intensity.code,
                ) { intensity = Intensity.fromCode(it) }
                5 -> PermissionStep()
            }
        }
        val canNext = when (step) { 1 -> loc != null; 2 -> avg >= 0; else -> true }
        Button(
            onClick = {
                if (step < STEPS - 1) step++
                else loc?.let { vm.finishOnboarding(place, it, avg.coerceAtLeast(0), first, intensity) }
            },
            enabled = canNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Emerald700),
        ) {
            Text(
                when (step) { 0 -> "বিসমিল্লাহ, শুরু করি"; STEPS - 1 -> "যাত্রা শুরু"; else -> "পরের ধাপ" },
                fontSize = 17.sp,
            )
        }
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", fontFamily = Amiri, fontSize = 26.sp, color = Emerald800,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("ইস্তিকামাহ", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("দ্বীনের ওপর অবিচল থাকার সঙ্গী", fontSize = 18.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
        Text(
            "এই অ্যাপ আপনাকে ধাপে ধাপে নিয়মিত নামাজি হতে সাহায্য করবে — সঠিক সময়ে কুরআনের আয়াত ও সহিহ হাদিস দিয়ে মনে করিয়ে দেবে।\n\n" +
                "আপনার সব তথ্য শুধু এই ফোনে থাকে। অ্যাপটার ইন্টারনেট অনুমতিই নেই।",
            fontSize = 17.sp, color = Ink, lineHeight = 26.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text("“আল্লাহর কাছে সবচেয়ে প্রিয় আমল তা-ই, যা নিয়মিত করা হয় — অল্প হলেও।”\n— সহিহ বুখারি ও মুসলিম",
            fontSize = 15.sp, color = Emerald700, lineHeight = 22.sp)
    }
}

@Composable
private fun Info(title: String, body: String) {
    Column {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Spacer(Modifier.height(12.dp))
        Text(body, fontSize = 17.sp, color = Ink, lineHeight = 26.sp)
    }
}

@Composable
private fun Question(title: String, hint: String, options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Emerald800, lineHeight = 32.sp)
        Spacer(Modifier.height(8.dp))
        Text(hint, fontSize = 15.sp, color = Muted, lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        options.forEach { (v, label) ->
            val sel = v == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .background(if (sel) Emerald50 else Color.White, RoundedCornerShape(14.dp))
                    .clickable { onSelect(v) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, fontSize = 17.sp, color = if (sel) Emerald800 else Ink, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
fun LocationStep(place: String, onPicked: (String, Location) -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var gpsMsg by remember { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) { gpsMsg = "অনুমতি পাওয়া যায়নি — নিচের তালিকা থেকে জেলা বেছে নিন।"; return@rememberLauncherForActivityResult }
        gpsMsg = "লোকেশন খোঁজা হচ্ছে…"
        fetchLocationOnce(ctx) { l ->
            if (l == null) gpsMsg = "লোকেশন পাওয়া যায়নি (GPS চালু আছে?) — তালিকা থেকে বেছে নিন।"
            else {
                val near = nearestCity(l.latitude, l.longitude)
                gpsMsg = null
                onPicked("${near.bn}-এর কাছে (GPS)", Location(l.latitude, l.longitude, ZoneId.systemDefault()))
            }
        }
    }
    Column {
        Text("আপনি কোথায় থাকেন?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Text("নামাজের সময় হিসাবের জন্য। লোকেশন কোথাও পাঠানো হয় না।", fontSize = 15.sp, color = Muted)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { permLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.MyLocation, null)
            Spacer(Modifier.padding(4.dp))
            Text("GPS দিয়ে খুঁজে নিন")
        }
        gpsMsg?.let { Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp)) }
        if (place.isNotEmpty()) {
            Text("নির্বাচিত: $place", color = Emerald700, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("জেলা খুঁজুন") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            items(Cities.all.filter { query.isBlank() || it.bn.contains(query.trim()) }) { c ->
                Text(
                    c.bn,
                    fontSize = 17.sp,
                    color = if (place == c.bn) Emerald700 else Ink,
                    fontWeight = if (place == c.bn) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().clickable { onPicked(c.bn, c.toLocation()) }.padding(vertical = 12.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionStep() {
    var notifAsked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifAsked = true }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("শেষ ধাপ: অনুমতি", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Emerald800)
        Spacer(Modifier.height(8.dp))
        Text(
            "রিমাইন্ডার ঠিক সময়ে পেতে নোটিফিকেশনের অনুমতি লাগবে। " +
                "Xiaomi, Realme, Vivo, Oppo ফোন অনেক সময় ব্যাকগ্রাউন্ডের অ্যাপ বন্ধ করে দেয় — " +
                "অ্যাপে ঢোকার পর \"ব্যাটারি গাইড\" দেখে নেবেন।",
            fontSize = 16.sp, color = Ink, lineHeight = 24.sp,
        )
        Spacer(Modifier.height(20.dp))
        if (Build.VERSION.SDK_INT >= 33) {
            Button(
                onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Emerald900),
            ) { Text(if (notifAsked) "নোটিফিকেশন অনুমতি ✓" else "নোটিফিকেশনের অনুমতি দিন") }
        } else {
            Text("✓ এই ফোনে নোটিফিকেশন আলাদা অনুমতি ছাড়াই চলে।", color = Emerald700)
        }
        Spacer(Modifier.height(16.dp))
        Text("আজ ${Bn.date(java.time.LocalDate.now())} থেকে আপনার যাত্রা শুরু। আল্লাহ সহজ করুন।", color = Muted, fontSize = 15.sp)
    }
}
