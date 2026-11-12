package com.istiqamah.app.core

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** নোটিফিকেশনের তীব্রতা — ইউজার নিজে বেছে নেন */
enum class Intensity(val code: Int, val bn: String, val desc: String) {
    LIGHT(1, "হালকা", "শুধু ওয়াক্ত শুরুর সময় একবার"),
    MEDIUM(2, "মাঝারি", "শুরুতে পুরস্কার, ১০ মিনিট পরে ক্ষতি, শেষের ২০ মিনিট আগে সতর্কতা"),
    STRONG(3, "কড়া", "মাঝারির সব + ওয়াক্তের মাঝামাঝি আরেকটা পুরস্কার");

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

    // ---- v0.3: পুরস্কারের ১০ মিনিট পরে ক্ষতির নোটিফিকেশন (তখনো না করলে) ----
    /** ওয়াক্ত শুরুর ১০ মিনিট পরে: না পড়লে বড় ক্ষতি */
    N1_LOSS(true, 0),
    AZKAR_MORNING_LOSS(false, 3),
    AZKAR_EVENING_LOSS(false, 3),
    MUHASABA_LOSS(false, 4),
    JUMUAH_DAY_LOSS(false, 5),

    // ---- v0.3: সুন্নত দিনলিপি (দৈনিক সীমার বাইরে, প্রতিটা আলাদা চালু/বন্ধ) ----
    BREAKFAST(false, 2),
    QAILULA(false, 2),
    DINNER(false, 2),
    SLEEP_PREP(false, 2),
    DUHA(false, 2),
    TAHAJJUD(false, 2),
    /** রবিবার ও বুধবার রাতে: আগামীকাল সোম/বৃহস্পতিবারের সুন্নত রোজা */
    FAST_EVE(false, 2);

    val isRoutine: Boolean get() = this in ROUTINE

    companion object {
        /** সুন্নত দিনলিপির সব ধরন — সেটিংসে এই ক্রমে দেখায় */
        val ROUTINE: List<NotifKind> get() = listOf(BREAKFAST, QAILULA, DINNER, SLEEP_PREP, FAST_EVE, DUHA, TAHAJJUD)
        /** শুরুতে চালু: খাবার, কাইলুলা, ঘুম, সোম/বৃহস্পতি রোজা; দোহা ও তাহাজ্জুদ ইউজার নিজে চালু করবেন */
        val ROUTINE_DEFAULT: Set<NotifKind> get() = setOf(BREAKFAST, QAILULA, DINNER, SLEEP_PREP, FAST_EVE)
    }

    /** এটা কি আগের কোনো নোটিফিকেশনের পরের "ক্ষতি" অংশ? */
    val isLossFollowUp: Boolean get() = this in Planner.FOLLOW_UPS.values
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
    /** সুন্নত দিনলিপির কোনগুলো চালু (core-এ ডিফল্ট ফাঁকা; অ্যাপ Prefs থেকে দেয়) */
    val routine: Set<NotifKind> = emptySet(),
    /** কাইলুলা যোহরের আগে (true) না পরে (false, ডিফল্ট) */
    val qailulaBeforeDhuhr: Boolean = false,
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
    /** পুরস্কারের নোটিফিকেশনের কত মিনিট পরে ক্ষতির নোটিফিকেশন */
    const val FOLLOW_UP_MIN = 10L
    private const val MIN_GAP_MIN = 10L

    /** কোন নোটিফিকেশনের পরে কোন ক্ষতির নোটিফিকেশন আসে */
    val FOLLOW_UPS: Map<NotifKind, NotifKind> = mapOf(
        NotifKind.N1_INVITE to NotifKind.N1_LOSS,
        NotifKind.AZKAR_MORNING to NotifKind.AZKAR_MORNING_LOSS,
        NotifKind.AZKAR_EVENING to NotifKind.AZKAR_EVENING_LOSS,
        NotifKind.MUHASABA to NotifKind.MUHASABA_LOSS,
        NotifKind.JUMUAH_DAY to NotifKind.JUMUAH_DAY_LOSS,
    )

    fun plan(input: PlannerInput): List<PlannedNotif> {
        val out = ArrayList<PlannedNotif>()
        for ((date, windows) in input.windowsByDate.toSortedMap()) {
            for (w in windows) {
                if ((date to w.prayer) in input.logged) continue
                val kinds = kindsFor(w.prayer, input)
                val n1 = w.start
                val n3 = w.end.minusMinutes(LAST_CALL_BEFORE_END_MIN)
                val n2 = w.start.plus(Duration.ofSeconds(w.length.seconds * 4 / 10))
                val n1Loss = n1.plusMinutes(FOLLOW_UP_MIN)
                for (k in kinds) {
                    val at = when (k) {
                        NotifKind.N1_INVITE -> n1
                        // শেষ সতর্কতার অন্তত ১০ মিনিট আগে আর ওয়াক্তের ভেতরে হলে তবেই
                        NotifKind.N1_LOSS ->
                            if (Duration.between(n1Loss, n3).toMinutes() >= MIN_GAP_MIN) n1Loss else null
                        NotifKind.N2_VIRTUE ->
                            if (Duration.between(n2, n3).toMinutes() >= MIN_GAP_MIN && Duration.between(n1Loss, n2).toMinutes() >= MIN_GAP_MIN) n2 else null
                        NotifKind.N3_LAST_CALL ->
                            if (Duration.between(n1, n3).toMinutes() >= MIN_GAP_MIN) n3 else null
                        else -> null
                    } ?: continue
                    if (at.isAfter(input.now)) out += PlannedNotif(date, w.prayer, k, at)
                }
            }
            out += extrasFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
            out += routineFor(date, windows, input.extras).filter { it.fireAt.isAfter(input.now) }
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
        val kept = list.sortedByDescending { it.kind.priority }.take(MAX_EXTRA_PER_DAY)
        // প্রতিটার ১০ মিনিট পরে ক্ষতির অংশ — একই রিমাইন্ডারের অংশ, তাই সীমায় গোনা হয় না
        val followUps = kept.mapNotNull { p -> FOLLOW_UPS[p.kind]?.let { PlannedNotif(p.date, null, it, p.fireAt.plusMinutes(FOLLOW_UP_MIN)) } }
        return kept + followUps
    }

    /**
     * সুন্নত দিনলিপি — নামাজের সময় থেকে হিসাব, তাই প্রতিদিন ঋতু অনুযায়ী বদলায়:
     * সকালের খাবার = সূর্যোদয় + ১.৫ ঘণ্টা · কাইলুলা = যোহরের ৪৫ মিনিট পরে (বা ১ ঘণ্টা আগে) ·
     * রাতের খাবার = মাগরিব + ২০ মিনিট · ঘুমের প্রস্তুতি = ইশা + ৭৫ মিনিট (মধ্যরাতের আগে) ·
     * দোহা = সূর্যোদয় + ২০ মিনিট · তাহাজ্জুদ = রাতের শেষ তৃতীয়াংশের শুরু · রোজার রিমাইন্ডার = রবি/বুধ ইশা + ৩০ মিনিট
     */
    fun routineFor(date: LocalDate, windows: List<PrayerWindow>, ex: ExtraReminders): List<PlannedNotif> {
        if (ex.routine.isEmpty()) return emptyList()
        val w = windows.associateBy { it.prayer }
        val fajr = w.getValue(Prayer.FAJR)
        val dhuhr = w.getValue(Prayer.DHUHR)
        val maghrib = w.getValue(Prayer.MAGHRIB)
        val isha = w.getValue(Prayer.ISHA)
        val sunrise = fajr.end
        // isha.end = ইসলামি মধ্যরাত = মাগরিব + রাতের অর্ধেক → পুরো রাত = ২ × (মধ্যরাত − মাগরিব)
        val night = Duration.between(maghrib.start, isha.end).multipliedBy(2)
        val out = ArrayList<PlannedNotif>()
        fun add(k: NotifKind, at: ZonedDateTime) { if (k in ex.routine) out += PlannedNotif(date, null, k, at) }
        add(NotifKind.DUHA, sunrise.plusMinutes(20))
        add(NotifKind.BREAKFAST, sunrise.plusMinutes(90))
        add(NotifKind.QAILULA, if (ex.qailulaBeforeDhuhr) dhuhr.start.minusMinutes(60) else dhuhr.start.plusMinutes(45))
        add(NotifKind.DINNER, maghrib.start.plusMinutes(20))
        add(NotifKind.SLEEP_PREP, minOf(isha.start.plusMinutes(75), isha.end.minusMinutes(30)))
        add(NotifKind.TAHAJJUD, maghrib.start.plus(night.multipliedBy(2).dividedBy(3)))
        if (date.dayOfWeek == DayOfWeek.SUNDAY || date.dayOfWeek == DayOfWeek.WEDNESDAY) {
            add(NotifKind.FAST_EVE, isha.start.plusMinutes(30))
        }
        return out
    }

    fun kindsFor(p: Prayer, input: PlannerInput): List<NotifKind> {
        // ফোকাসের বাইরে বা অভ্যাস হয়ে যাওয়া ওয়াক্ত: শুধু মৃদু আমন্ত্রণ
        if (p !in input.focus || p in input.habitFormed) return listOf(NotifKind.N1_INVITE)
        return when (input.intensity) {
            Intensity.LIGHT -> listOf(NotifKind.N1_INVITE)
            Intensity.MEDIUM -> listOf(NotifKind.N1_INVITE, NotifKind.N1_LOSS, NotifKind.N3_LAST_CALL)
            Intensity.STRONG -> listOf(NotifKind.N1_INVITE, NotifKind.N1_LOSS, NotifKind.N2_VIRTUE, NotifKind.N3_LAST_CALL)
        }
    }

    /** কোন ওয়াক্ত এখন চলছে (না থাকলে null) — হোম স্ক্রিন ও নোটিফিকেশন অ্যাকশনের জন্য */
    fun current(windows: List<PrayerWindow>, now: ZonedDateTime): PrayerWindow? = windows.firstOrNull { it.contains(now) }

    /** পরের ওয়াক্ত (আজ বা আগামীকাল) */
    fun next(windowsByDate: Map<LocalDate, List<PrayerWindow>>, now: ZonedDateTime): PrayerWindow? =
        windowsByDate.toSortedMap().values.flatten().firstOrNull { it.start.isAfter(now) }
}
