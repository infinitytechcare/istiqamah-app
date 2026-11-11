package com.istiqamah.app.core

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

enum class NotifKind {
    /** আমন্ত্রণ — ওয়াক্ত শুরু */
    N1_INVITE,
    /** ফজিলত — ওয়াক্তের ৪০% পার */
    N2_VIRTUE,
    /** শেষ সতর্কতা + আশা — শেষের ২০ মিনিট আগে */
    N3_LAST_CALL,
    /** দৈনিক আয়াত */
    DAILY_AYAH,
}

data class PlannedNotif(
    val date: LocalDate,
    val prayer: Prayer?,
    val kind: NotifKind,
    val fireAt: ZonedDateTime,
) {
    /** PendingIntent requestCode — তারিখ+ওয়াক্ত+ধরন মিলে অনন্য, তাই বাতিলও করা যায় */
    val id: Int get() = ((date.toEpochDay() % 10_000).toInt() * 100) + ((prayer?.ordinal ?: 9) * 10) + kind.ordinal
}

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
    val dailyAyahAt: LocalTime?,
)

object Planner {
    const val LAST_CALL_BEFORE_END_MIN = 20L
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
                        NotifKind.DAILY_AYAH -> null
                    } ?: continue
                    if (at.isAfter(input.now)) out += PlannedNotif(date, w.prayer, k, at)
                }
            }
            input.dailyAyahAt?.let { t ->
                val at = date.atTime(t).atZone(input.now.zone)
                if (at.isAfter(input.now)) out += PlannedNotif(date, null, NotifKind.DAILY_AYAH, at)
            }
        }
        return out.sortedBy { it.fireAt }
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
