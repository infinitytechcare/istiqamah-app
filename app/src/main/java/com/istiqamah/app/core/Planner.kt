package com.istiqamah.app.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** নোটিফিকেশনের তীব্রতা — ইউজার নিজে বেছে নেন */
enum class Intensity(val code: Int, val bn: String, val desc: String) {
    LIGHT(1, "হালকা", "শুধু ওয়াক্ত শুরুর সময় একবার"),
    MEDIUM(2, "মাঝারি", "শুরুতে + শেষ হওয়ার ২০ মিনিট আগে"),
    STRONG(3, "কড়া", "শুরুতে + মাঝামাঝি + শেষের আগে");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: MEDIUM
    }
}

/**
 * নোটিফিকেশনের ধরন। ordinal নোটিফিকেশন আইডিতে যায় — নতুন ধরন শুধু শেষে যোগ করুন।
 * isPrayer = নামাজের রিমাইন্ডার (দৈনিক সীমার বাইরে); বাকিগুলো দিনে সর্বোচ্চ [Planner.MAX_EXTRA_PER_DAY]টি।
 */
enum class NotifKind(val isPrayer: Boolean, val priority: Int) {
    /** আমন্ত্রণ — ওয়াক্ত শুরু */
    N1_INVITE(true, 0),
    /** ফজিলত — ওয়াক্তের ৪০% পার */
    N2_VIRTUE(true, 0),
    /** শেষ সতর্কতা + আশা — শেষের ২০ মিনিট আগে */
    N3_LAST_CALL(true, 0),
    /** দৈনিক আয়াত (সবচেয়ে কম গুরুত্ব — সীমা ছাড়ালে প্রথমে বাদ) */
    DAILY_AYAH(false, 1),
    /** সকালের আযকার — ফজরের পর */
    AZKAR_MORNING(false, 3),
    /** সন্ধ্যার আযকার — আসরের পর */
    AZKAR_EVENING(false, 3),
    /** রাতের মুহাসাবা */
    MUHASABA(false, 4),
    /** বৃহস্পতিবার মাগরিবের পর: সূরা কাহফ শুরুর রিমাইন্ডার */
    JUMUAH_EVE(false, 5),
    /** শুক্রবার সকাল: গোসল, আগে মসজিদে, দরুদ */
    JUMUAH_DAY(false, 5),
    /** শুক্রবার আসরের পর: সাপ্তাহিক পর্যালোচনা */
    WEEKLY_REVIEW(false, 6),
}

data class PlannedNotif(
    val date: LocalDate,
    val prayer: Prayer?,
    val kind: NotifKind,
    val fireAt: ZonedDateTime,
) {
    /**
     * PendingIntent requestCode / নোটিফিকেশন আইডি — তারিখ+ওয়াক্ত+ধরন মিলে অনন্য, তাই বাতিলও করা যায়।
     * সর্বোচ্চ ≈ ১ কোটি, তাই স্নুজ ও অ্যাকশনের অফসেটের সাথে মেলে না।
     */
    val id: Int get() = ((date.toEpochDay() % 10_000).toInt() * 1000) + ((prayer?.ordinal ?: 9) * 100) + kind.ordinal
}

/** নামাজের বাইরের রিমাইন্ডারগুলোর চালু/বন্ধ ও সময় */
data class ExtraReminders(
    val dailyAyahAt: LocalTime? = LocalTime.of(9, 0),
    val azkar: Boolean = true,
    /** null = বন্ধ */
    val muhasabaAt: LocalTime? = LocalTime.of(22, 0),
    val jumuah: Boolean = true,
    val weekly: Boolean = true,
)

data class PlannerInput(
    val now: ZonedDateTime,
    /** আজ ও আগামীকালের ওয়াক্ত-সীমা (তারিখসহ) */
    val windowsByDate: Map<LocalDate, List<PrayerWindow>>,
    val intensity: Intensity,
    val focus: Set<Prayer>,
    /** যে ওয়াক্ত অভ্যাসে পরিণত — শুধু N1 */
    val habitFormed: Set<Prayer>,
    /** ইতিমধ্যে লগ হওয়া (তারিখ, ওয়াক্ত) — এগুলোর আর রিমাইন্ডার লাগবে না */
    val logged: Set<Pair<LocalDate, Prayer>>,
    val extras: ExtraReminders = ExtraReminders(),
    /** আজকের কোন আযকার সেট শেষ হয়ে গেছে — তার রিমাইন্ডার লাগবে না */
    val azkarDone: Set<Pair<LocalDate, AzkarSet>> = emptySet(),
    /** কোন দিনের মুহাসাবা হয়ে গেছে */
    val muhasabaDone: Set<LocalDate> = emptySet(),
)

object Planner {
    const val LAST_CALL_BEFORE_END_MIN = 20L
    /** নামাজ ছাড়া অন্য নোটিফিকেশন দিনে সর্বোচ্চ */
    const val MAX_EXTRA_PER_DAY = 4
    private const val MIN_GAP_MIN = 10L

    fun plan(input: PlannerInput): List<PlannedNotif> {
        val out = ArrayList<PlannedNotif>()
        for ((date, windows) in input.windowsByDate.toSortedMap()) {
            for (w in windows) {
                if ((date to w.prayer) in input.logged) continue
                val kinds = kindsFor(w.prayer, input)
                val n1 = w.start
                val n3 = w.end.minusMinutes(LAST_CALL_BEFORE_END_MIN)
                val n2 = w.start.plus(Duration.ofSeconds(w.length.seconds * 4 / 10))
                for (k in kinds) {
                    val at = when (k) {
                        NotifKind.N1_INVITE -> n1
                        NotifKind.N2_VIRTUE ->
                            if (Duration.between(n2, n3).toMinutes() >= MIN_GAP_MIN && n2.isAfter(n1)) n2 else null
                        NotifKind.N3_LAST_CALL ->
                            if (Duration.between(n1, n3).toMinutes() >= MIN_GAP_MIN) n3 else null
                        else -> null
                    } ?: continue
                    if (at.isAfter(input.now)) out += PlannedNotif(date, w.prayer, k, at)
                }
            }
            out += extrasFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
        }
        return out.sortedBy { it.fireAt }
    }

    /**
     * নামাজের বাইরের রিমাইন্ডার — দিনে সর্বোচ্চ [MAX_EXTRA_PER_DAY]টি, গুরুত্ব অনুযায়ী।
     * রাতের নীরব সময় (ইশার শেষ → ফজর) এড়িয়ে চলে।
     */
    fun extrasFor(date: LocalDate, windows: List<PrayerWindow>, input: PlannerInput): List<PlannedNotif> {
        val ex = input.extras
        val byPrayer = windows.associateBy { it.prayer }
        val fajr = byPrayer.getValue(Prayer.FAJR)
        val asr = byPrayer.getValue(Prayer.ASR)
        val maghrib = byPrayer.getValue(Prayer.MAGHRIB)
        val isha = byPrayer.getValue(Prayer.ISHA)
        val zone = fajr.start.zone
        val list = ArrayList<PlannedNotif>()

        ex.dailyAyahAt?.let { list += PlannedNotif(date, null, NotifKind.DAILY_AYAH, date.atTime(it).atZone(zone)) }

        if (ex.azkar) {
            if ((date to AzkarSet.MORNING) !in input.azkarDone) {
                // ফজরের ২০ মিনিট পরে — সূর্যোদয়ের আগেই পড়ার সুযোগ থাকে
                val at = minOf(fajr.start.plusMinutes(20), fajr.end.minusMinutes(15)).let { if (it.isBefore(fajr.start)) fajr.start else it }
                list += PlannedNotif(date, null, NotifKind.AZKAR_MORNING, at)
            }
            if ((date to AzkarSet.EVENING) !in input.azkarDone) {
                list += PlannedNotif(date, null, NotifKind.AZKAR_EVENING, asr.start.plusMinutes(30))
            }
        }

        ex.muhasabaAt?.let { t ->
            if (date !in input.muhasabaDone) {
                var at = date.atTime(t).atZone(zone)
                // ইশার সময়ের শেষ সীমার আগে (নীরব সময়ে নয়) আর মাগরিবের পরে
                val latest = isha.end.minusMinutes(15)
                if (at.isAfter(latest)) at = latest
                if (at.isBefore(maghrib.start)) at = isha.start.plusMinutes(30)
                list += PlannedNotif(date, null, NotifKind.MUHASABA, at)
            }
        }

        if (ex.jumuah) {
            if (date.dayOfWeek == DayOfWeek.THURSDAY) list += PlannedNotif(date, null, NotifKind.JUMUAH_EVE, maghrib.start.plusMinutes(30))
            if (date.dayOfWeek == DayOfWeek.FRIDAY) {
                val morning = date.atTime(9, 0).atZone(zone)
                list += PlannedNotif(date, null, NotifKind.JUMUAH_DAY, morning)
            }
        }
        if (ex.weekly && date.dayOfWeek == DayOfWeek.FRIDAY) {
            list += PlannedNotif(date, null, NotifKind.WEEKLY_REVIEW, asr.start.plusMinutes(45))
        }

        // সীমা: গুরুত্ব বেশি যেগুলোর সেগুলো রাখি
        return list.sortedByDescending { it.kind.priority }.take(MAX_EXTRA_PER_DAY)
    }

    fun kindsFor(p: Prayer, input: PlannerInput): List<NotifKind> {
        // ফোকাসের বাইরে বা অভ্যাস হয়ে যাওয়া ওয়াক্ত: শুধু মৃদু আমন্ত্রণ
        if (p !in input.focus || p in input.habitFormed) return listOf(NotifKind.N1_INVITE)
        return when (input.intensity) {
            Intensity.LIGHT -> listOf(NotifKind.N1_INVITE)
            Intensity.MEDIUM -> listOf(NotifKind.N1_INVITE, NotifKind.N3_LAST_CALL)
            Intensity.STRONG -> listOf(NotifKind.N1_INVITE, NotifKind.N2_VIRTUE, NotifKind.N3_LAST_CALL)
        }
    }

    /** কোন ওয়াক্ত এখন চলছে (না থাকলে null) — হোম স্ক্রিন ও নোটিফিকেশন অ্যাকশনের জন্য */
    fun current(windows: List<PrayerWindow>, now: ZonedDateTime): PrayerWindow? = windows.firstOrNull { it.contains(now) }

    /** পরের ওয়াক্ত (আজ বা আগামীকাল) */
    fun next(windowsByDate: Map<LocalDate, List<PrayerWindow>>, now: ZonedDateTime): PrayerWindow? =
        windowsByDate.toSortedMap().values.flatten().firstOrNull { it.start.isAfter(now) }
}
