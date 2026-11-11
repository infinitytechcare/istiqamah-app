package com.istiqamah.app.core

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * পাঁচ ওয়াক্ত। ক্রম গুরুত্বপূর্ণ — ordinal দিয়ে ডেটাবেসে রাখা হয়।
 */
enum class Prayer(val bn: String) {
    FAJR("ফজর"),
    DHUHR("যোহর"),
    ASR("আসর"),
    MAGHRIB("মাগরিব"),
    ISHA("ইশা");

    companion object {
        fun fromOrdinal(i: Int): Prayer = entries[i]
    }
}

/** হিসাব পদ্ধতি — ফজর ও ইশার সূর্যের কোণ। */
enum class CalcMethod(val bn: String, val fajrAngle: Double, val ishaAngle: Double) {
    KARACHI("করাচি (বাংলাদেশ ইসলামিক ফাউন্ডেশন)", 18.0, 18.0),
    MWL("মুসলিম ওয়ার্ল্ড লীগ", 18.0, 17.0),
    EGYPT("মিশর", 19.5, 17.5),
    ISNA("উত্তর আমেরিকা (ISNA)", 15.0, 15.0),
}

/** আসরের হিসাব: ছায়া বস্তুর ১ গুণ (শাফেয়ি) না ২ গুণ (হানাফি)। */
enum class AsrMadhab(val bn: String, val shadowFactor: Double) {
    HANAFI("হানাফি", 2.0),
    SHAFII("শাফেয়ি/মালেকি/হাম্বলি", 1.0),
}

data class Location(val latitude: Double, val longitude: Double, val zone: ZoneId)

data class PrayerSettings(
    val method: CalcMethod = CalcMethod.KARACHI,
    val madhab: AsrMadhab = AsrMadhab.HANAFI,
    /** প্রতিটা ওয়াক্তে মিনিট যোগ/বিয়োগ (মসজিদের সময় বা ইহতিয়াতের জন্য)। ক্রম: Prayer.entries */
    val adjustMinutes: List<Int> = listOf(0, 0, 0, 0, 0),
)

/** একটা ওয়াক্তের শুরু ও শেষ। */
data class PrayerWindow(val prayer: Prayer, val start: ZonedDateTime, val end: ZonedDateTime) {
    val length: Duration get() = Duration.between(start, end)
    fun contains(t: ZonedDateTime) = !t.isBefore(start) && t.isBefore(end)
}

data class DayTimes(
    val date: LocalDate,
    val fajr: ZonedDateTime,
    val sunrise: ZonedDateTime,
    val dhuhr: ZonedDateTime,
    val asr: ZonedDateTime,
    val maghrib: ZonedDateTime,
    val isha: ZonedDateTime,
) {
    fun start(p: Prayer): ZonedDateTime = when (p) {
        Prayer.FAJR -> fajr
        Prayer.DHUHR -> dhuhr
        Prayer.ASR -> asr
        Prayer.MAGHRIB -> maghrib
        Prayer.ISHA -> isha
    }
}

/**
 * নামাজের সময়ের অফলাইন হিসাব (praytimes.org-এর জনপ্রিয় অ্যালগরিদম)।
 * কোনো লাইব্রেরি বা ইন্টারনেট লাগে না।
 */
object PrayerTimesCalculator {

    /** জাওয়াল (সূর্য মাথার ওপর) থেকে যোহর শুরু পর্যন্ত সতর্কতামূলক বিরতি, মিনিট */
    private const val DHUHR_SAFETY_MIN = 1

    fun compute(date: LocalDate, loc: Location, s: PrayerSettings = PrayerSettings()): DayTimes {
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - loc.longitude / (15.0 * 24.0)
        val lat = loc.latitude

        fun sunDecl(jd: Double) = sunPosition(jd).first
        fun eqt(jd: Double) = sunPosition(jd).second
        fun midDay(t: Double) = fixHour(12 - eqt(jDate + t))
        fun sunAngleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunDecl(jDate + t)
            val noon = midDay(t)
            val x = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            // উঁচু অক্ষাংশে কোণ কখনো না পৌঁছালে NaN হয় — তখন সীমায় আটকে দিই
            val tt = darccos(x.coerceIn(-1.0, 1.0)) / 15.0
            return noon + if (ccw) -tt else tt
        }
        fun asrTime(factor: Double, t: Double): Double {
            val decl = sunDecl(jDate + t)
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return sunAngleTime(angle, t, false)
        }

        // দুই ধাপে হিসাব — প্রথম অনুমান দিয়ে সূর্যের অবস্থান, তারপর নিখুঁত
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asr = 13.0; var sunset = 18.0; var isha = 18.0
        repeat(2) {
            fajr = sunAngleTime(s.method.fajrAngle, fajr / 24, true)
            sunrise = sunAngleTime(0.833, sunrise / 24, true)
            dhuhr = midDay(dhuhr / 24)
            asr = asrTime(s.madhab.shadowFactor, asr / 24)
            sunset = sunAngleTime(0.833, sunset / 24, false)
            isha = sunAngleTime(s.method.ishaAngle, isha / 24, false)
        }

        val start = date.atStartOfDay(loc.zone)
        val tzHours = start.offset.totalSeconds / 3600.0
        /**
         * ওয়াক্তের শুরু মিনিটে উপরের দিকে গোল করা হয় (আগে দেখানো ঠিক না);
         * সূর্যোদয় নিচের দিকে (ফজরের শেষ সময় আগে ধরা নিরাপদ)।
         */
        fun at(hours: Double, adj: Int, roundUp: Boolean = true): ZonedDateTime {
            val localMinutes = (hours + tzHours - loc.longitude / 15.0) * 60.0
            val minutes = (if (roundUp) ceil(localMinutes) else floor(localMinutes)).toLong() + adj
            return start.plusMinutes(minutes)
        }
        val a = s.adjustMinutes
        return DayTimes(
            date = date,
            fajr = at(fajr, a[0]),
            sunrise = at(sunrise, 0, roundUp = false),
            dhuhr = at(dhuhr, a[1] + DHUHR_SAFETY_MIN),
            asr = at(asr, a[2]),
            maghrib = at(sunset, a[3]),
            isha = at(isha, a[4]),
        )
    }

    /**
     * প্রতিটা ওয়াক্তের সময়সীমা। ইশা শেষ ধরা হয় ইসলামি মধ্যরাতে (মাগরিব থেকে পরের ফজরের অর্ধেক) —
     * রিমাইন্ডারের জন্য; মাকরুহ ছাড়া ইশা ফজর পর্যন্ত বৈধ।
     */
    fun windows(today: DayTimes, tomorrow: DayTimes): List<PrayerWindow> {
        val midnight = today.maghrib.plus(Duration.between(today.maghrib, tomorrow.fajr).dividedBy(2))
        return listOf(
            PrayerWindow(Prayer.FAJR, today.fajr, today.sunrise),
            PrayerWindow(Prayer.DHUHR, today.dhuhr, today.asr),
            PrayerWindow(Prayer.ASR, today.asr, today.maghrib),
            PrayerWindow(Prayer.MAGHRIB, today.maghrib, today.isha),
            PrayerWindow(Prayer.ISHA, today.isha, midnight),
        )
    }

    // ---------- জ্যোতির্বিদ্যার হিসাব ----------

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year; var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** @return (declination ডিগ্রি, equation of time ঘণ্টা) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    private fun rad(d: Double) = d * Math.PI / 180.0
    private fun deg(r: Double) = r * 180.0 / Math.PI
    private fun dsin(d: Double) = sin(rad(d))
    private fun dcos(d: Double) = cos(rad(d))
    private fun dtan(d: Double) = tan(rad(d))
    private fun darcsin(x: Double) = deg(asin(x))
    private fun darccos(x: Double) = deg(acos(x))
    private fun darctan2(y: Double, x: Double) = deg(atan2(y, x))
    private fun darccot(x: Double) = deg(atan(1 / x))
    private fun fix(a: Double, b: Double): Double { val r = a - b * floor(a / b); return if (r < 0) r + b else r }
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
}
