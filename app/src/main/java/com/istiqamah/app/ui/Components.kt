package com.istiqamah.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.data.Cities
import com.istiqamah.app.data.City
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.hypot

fun statusColor(s: PrayerStatus?): Color = when (s) {
    PrayerStatus.JAMAAH -> StatusJamaah
    PrayerStatus.ALONE -> StatusAlone
    PrayerStatus.QAZA -> StatusQaza
    PrayerStatus.MISSED -> StatusMissed
    PrayerStatus.EXCUSED -> StatusExcused
    null -> Color.Transparent
}

/** ছোট গোল দাগ — ক্যালেন্ডার ও তালিকায় অবস্থা বোঝাতে */
@Composable
fun StatusDot(s: PrayerStatus?, size: Int = 8) {
    val m = Modifier.size(size.dp).background(statusColor(s), CircleShape)
    Box(if (s == null) m.border(1.dp, StatusMissed, CircleShape) else m)
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

/**
 * একটা ওয়াক্তের অবস্থা বেছে নেওয়ার শিট।
 * "বিরতি" নিচে আলাদা করে — শুধু যার দরকার সে দেখবে, কাউকে কিছু জিজ্ঞেস করা হয় না।
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSheet(
    date: LocalDate,
    prayer: Prayer,
    current: PrayerStatus?,
    isPast: Boolean,
    onPick: (PrayerStatus?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Cream) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("${prayer.bn} · ${Bn.date(date)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Emerald800)
            Spacer(Modifier.height(12.dp))
            val options = buildList {
                add(PrayerStatus.JAMAAH to "জামাতে পড়েছি")
                add(PrayerStatus.ALONE to if (isPast) "একা, সময়মতো পড়েছিলাম" else "একা পড়েছি")
                if (isPast) add(PrayerStatus.QAZA to "কাযা করেছি")
                if (isPast) add(PrayerStatus.MISSED to "পড়া হয়নি (কাযার হিসাবে যোগ হবে)")
            }
            options.forEach { (s, label) -> SheetRow(label, s, s == current) { onPick(s) } }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Divider)
            SheetRow("বিরতি (গোপন — স্ট্রিক ভাঙবে না)", PrayerStatus.EXCUSED, current == PrayerStatus.EXCUSED) { onPick(PrayerStatus.EXCUSED) }
            if (current != null) {
                TextButton(onClick = { onPick(null) }) { Text("হিসাব মুছে দিন", color = Muted) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetRow(label: String, s: PrayerStatus, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Emerald50 else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusDot(s, 14)
        Text(label, fontSize = 17.sp, color = Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------- লোকেশন ----------

fun nearestCity(lat: Double, lng: Double): City = Cities.all.minBy { hypot(it.lat - lat, it.lng - lng) }

fun City.toLocation(): Location = Location(lat, lng, ZoneId.of("Asia/Dhaka"))

/**
 * ফোনের লোকেশন একবার নেওয়া (Google Play Services ছাড়াই)।
 * অনুমতি আগে নিতে হবে; না পেলে cb(null)।
 */
@SuppressLint("MissingPermission")
fun fetchLocationOnce(ctx: Context, cb: (android.location.Location?) -> Unit) {
    val lm = ctx.getSystemService(LocationManager::class.java) ?: return cb(null)
    try {
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) return cb(last)
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return cb(null)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { cb(it) }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, { loc -> cb(loc) }, Looper.getMainLooper())
        }
    } catch (e: SecurityException) {
        cb(null)
    }
}
