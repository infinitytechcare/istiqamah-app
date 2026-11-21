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
    FAST_EVE(false, 2),

    // ---- v0.4: ঘরে-একা মোড ----
    /** ক্ষতির নোটিফিকেশনের পর প্রতি ১৫ মিনিটে নাছোড় ডাক (একা থাকলেও আল্লাহ দেখছেন / অলসতা / প্রশান্তি) */
    HOME_NUDGE_1(true, 0),
    HOME_NUDGE_2(true, 0),
    HOME_NUDGE_3(true, 0),
    /** "ওযু করতে যাচ্ছি" চাপার ১২ মিনিট পরে খোঁজ — Planner বসায় না, অ্যাকশন থেকে বসে */
    WUDU_CHECK(true, 0),
    /** ইশার পর জবাবদিহি বন্ধুকে আজকের হিসাব পাঠানোর রিমাইন্ডার */
    DAILY_REPORT(false, 2),

    // ---- v0.5 ----
    /** (পুরুষ) জামাত ছুটে গেছে — তবু ওয়াক্ত আছে */
    JAMAAT_MISSED(true, 0),
    /** (ঐচ্ছিক) চেক-ইন না করা পর্যন্ত প্রতি ১০ মিনিটে শব্দ ছাড়া — Planner শুধু পরেরটা বসায় */
    SILENT_REPEAT(true, 0),
    /** (মহিলা) বিরতির ৩য় দিন থেকে দিনে একবার: বিরতি শেষ হলে জানান */
    PAUSE_CHECK(false, 1),

    // ---- v0.6: দৈনিক কুরআন ----
    /** ফজরের পরে (বা বাছাই করা সময়ে): আজকের পড়া — বড় পুরস্কার */
    QURAN_DAILY(false, 1),
    /** ইশার পরে, আজকের লক্ষ্য পূরণ না হলে: ক্ষতি + আশা */
    QURAN_LOSS(false, 1),

    // ---- v0.7: রোজা ও রমজান ----
    /** (রমজান) সাহরি খাওয়ার ডাক — ফজরের suhoorLead মিনিট আগে */
    SUHOOR(false, 0),
    /** (রমজান) সাহরি শেষ হতে ১০ মিনিট */
    SUHOOR_END(false, 0),
    /** (রমজান) ইফতারের ১০ মিনিট আগে — দোয়া, আর "রোজা রেখেছি" চেক-ইন */
    IFTAR(false, 0),
    /** (রমজান) শেষ দশকের বেজোড় রাত — লাইলাতুল কদর */
    QADR_NIGHT(false, 1),
    /** কাল থেকে রমজান */
    RAMADAN_EVE(false, 0),

    // ---- v0.8: যাকাত, সদকা, জিলহজ ----
    /** যাকাতের বার্ষিক হিজরি তারিখের ৭ দিন আগে */
    ZAKAT_SOON(false, 1),
    /** যাকাতের দিন */
    ZAKAT_DAY(false, 0),
    /** সাপ্তাহিক গোপন সদকা */
    SADAQAH_WEEKLY(false, 2),
    /** কাল থেকে জিলহজের প্রথম দশ দিন (কুরবানিদাতার চুল-নখ না কাটা) */
    DHUL_HIJJAH_EVE(false, 1);

    val isHomeNudge: Boolean get() = this == HOME_NUDGE_1 || this == HOME_NUDGE_2 || this == HOME_NUDGE_3

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
    /** v0.4: জবাবদিহি বন্ধুকে দৈনিক হিসাব পাঠানোর রিমাইন্ডার */
    val dailyReport: Boolean = false,
    /** v0.6: দৈনিক কুরআন; quranAt null = ফজরের পরে (সূর্যোদয়ের ১৫ মিনিট আগে) */
    val quran: Boolean = false,
    val quranAt: LocalTime? = null,
    /** v0.7: রমজানের সাহরি-ইফতার-কদর রিমাইন্ডার */
    val fasting: Boolean = false,
    val suhoorLeadMin: Int = 45,
    /** কোন নফল রোজাগুলোর আগের রাতে মনে করাবে (FAST_EVE সুন্নত দিনলিপিতে চালু থাকলে) */
    val nafl: Set<NaflFast> = NaflFast.entries.toSet(),
    /** v0.8: যাকাতের বার্ষিক হিজরি তারিখ (মাস, দিন); null = নেই */
    val zakatHijri: Pair<Int, Int>? = null,
    /** সাপ্তাহিক সদকার দিন; null = বন্ধ */
    val sadaqahDay: DayOfWeek? = null,
    /** জিলহজের প্রথম দশকের রিমাইন্ডার */
    val dhulHijjah: Boolean = false,
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
    /** v0.4: যে ওয়াক্তগুলো ঘরে পড়ার কথা — [Situation.homePrayers] */
    val home: Set<Pair<LocalDate, Prayer>> = emptySet(),
    /** v0.5: (পুরুষ) জামাতের সময় — [Jamaat.times] */
    val jamaat: Map<Pair<LocalDate, Prayer>, JamaatTime> = emptyMap(),
    /** জামাতের কত মিনিট আগে প্রথম ডাক */
    val leadMin: Long = 15,
    /** (মহিলা) বিরতি শুরুর তারিখ; null = বিরতি নেই */
    val pausedFrom: LocalDate? = null,
    /** চেক-ইন না করা পর্যন্ত প্রতি ১০ মিনিটে নীরব রিমাইন্ডার */
    val silentRepeat: Boolean = false,
    /** v0.6: কোন দিনের কুরআনের লক্ষ্য পূরণ হয়েছে */
    val quranDone: Set<LocalDate> = emptySet(),
    /** v0.7: প্রতিটা তারিখের হিজরি (সংশোধনসহ) — আজ, কাল, পরশু */
    val hijri: Map<LocalDate, HijriDay> = emptyMap(),
    /** কোন দিনের রোজা লেখা হয়ে গেছে */
    val fastLogged: Set<LocalDate> = emptySet(),
    /** এ বছরের শাওয়ালে কয়টা রোজা রাখা হয়েছে */
    val shawwalDone: Int = 0,
)

object Planner {
    const val LAST_CALL_BEFORE_END_MIN = 20L
    /** নামাজ ছাড়া অন্য নোটিফিকেশন দিনে সর্বোচ্চ */
    const val MAX_EXTRA_PER_DAY = 4
    /** পুরস্কারের নোটিফিকেশনের কত মিনিট পরে ক্ষতির নোটিফিকেশন */
    const val FOLLOW_UP_MIN = 10L
    /** জামাতের কত মিনিট পরে "জামাত ছুটে গেছে" */
    const val JAMAAT_MISSED_AFTER_MIN = 15L
    /** নীরব রিমাইন্ডারের ব্যবধান */
    const val SILENT_EVERY_MIN = 10L
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
            val paused = Pause.isPaused(date, input.pausedFrom)
            for (w in windows) {
                if (paused) break // বিরতিতে নামাজ মাফ — কোনো নামাজের রিমাইন্ডার নয়
                if ((date to w.prayer) in input.logged) continue
                val jam = input.jamaat[date to w.prayer]?.at
                val kinds = kindsFor(w.prayer, input, date).let { ks ->
                    // জামাত থাকলে আর ক্ষতির ডাক চালু থাকলে: জামাত ছুটে যাওয়ার ডাকও
                    if (jam != null && NotifKind.N1_LOSS in ks) ks + NotifKind.JAMAAT_MISSED else ks
                }
                // পুরুষ: জামাতের lead মিনিট আগে (ওয়াক্ত শুরুর আগে নয়); মহিলা/জামাত নেই: ওয়াক্ত শুরুতে
                val n1 = jam?.minusMinutes(input.leadMin)?.let { if (it.isBefore(w.start)) w.start else it } ?: w.start
                val n3 = w.end.minusMinutes(LAST_CALL_BEFORE_END_MIN)
                val n2 = w.start.plus(Duration.ofSeconds(w.length.seconds * 4 / 10))
                val n1Loss = n1.plusMinutes(FOLLOW_UP_MIN)
                val missed = jam?.plusMinutes(JAMAAT_MISSED_AFTER_MIN)
                // ঘরের নাছোড় ডাক: জামাত থাকলে জামাত-ছুটে-যাওয়ার পর থেকে, নইলে ক্ষতির ডাকের পর থেকে
                val nudgeBase = missed ?: n1Loss
                val loud = ArrayList<ZonedDateTime>()
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
                        NotifKind.HOME_NUDGE_1, NotifKind.HOME_NUDGE_2, NotifKind.HOME_NUDGE_3 -> {
                            val i = k.ordinal - NotifKind.HOME_NUDGE_1.ordinal + 1
                            val at = nudgeBase.plusMinutes(Situation.NUDGE_EVERY_MIN * i)
                            if (Duration.between(at, n3).toMinutes() >= MIN_GAP_MIN) at else null
                        }
                        NotifKind.JAMAAT_MISSED ->
                            missed?.takeIf { Duration.between(it, n3).toMinutes() >= MIN_GAP_MIN && it.isAfter(n1Loss) }
                        else -> null
                    } ?: continue
                    loud += at
                    if (at.isAfter(input.now)) out += PlannedNotif(date, w.prayer, k, at)
                }
                if (input.silentRepeat) {
                    nextSilentRepeat(w, n1, loud, input.now)?.let { out += PlannedNotif(date, w.prayer, NotifKind.SILENT_REPEAT, it) }
                }
            }
            pauseCheckFor(date, windows, input)?.takeIf { it.fireAt.isAfter(input.now) }?.let { out += it }
            out += quranFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
            out += fastingFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
            out += charityFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
            out += extrasFor(date, windows, input).filter { it.fireAt.isAfter(input.now) }
            out += routineFor(date, windows, input.extras).filter { it.fireAt.isAfter(input.now) }
            reportFor(date, windows, input.extras)?.takeIf { it.fireAt.isAfter(input.now) }?.let { out += it }
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
        // FAST_EVE (নফল রোজার আগের রাত) এখন fastingFor-এ — হিজরি তারিখ লাগে
        return out
    }

    /**
     * জবাবদিহির রিমাইন্ডার: ইশা শুরুর ১০০ মিনিট পরে (ততক্ষণে সাধারণত ইশা হয়ে যায়),
     * কিন্তু ইশার শেষ সীমার ১০ মিনিট আগের পরে নয়।
     */
    fun reportFor(date: LocalDate, windows: List<PrayerWindow>, ex: ExtraReminders): PlannedNotif? {
        if (!ex.dailyReport) return null
        val isha = windows.first { it.prayer == Prayer.ISHA }
        val at = minOf(isha.start.plusMinutes(100), isha.end.minusMinutes(10))
        return PlannedNotif(date, null, NotifKind.DAILY_REPORT, at)
    }

    /**
     * নীরব রিমাইন্ডারের পরের সময়: প্রথম ডাকের পর প্রতি ১০ মিনিটের ঘর থেকে এখনের পরেরটা।
     * জোরালো ডাকের ৩ মিনিটের মধ্যে পড়লে বাদ, আর ওয়াক্ত শেষের ২ মিনিট আগ পর্যন্ত।
     * Planner শুধু পরেরটা বসায়; সেটা বাজলে পুরো হিসাব আবার হয়, তাই পরেরটা বসে যায়।
     */
    fun nextSilentRepeat(w: PrayerWindow, n1: ZonedDateTime, loud: List<ZonedDateTime>, now: ZonedDateTime): ZonedDateTime? {
        var t = n1.plusMinutes(SILENT_EVERY_MIN)
        val last = w.end.minusMinutes(2)
        while (!t.isAfter(last)) {
            val clash = loud.any { Math.abs(Duration.between(it, t).toMinutes()) < 3 }
            if (t.isAfter(now) && !clash) return t
            t = t.plusMinutes(SILENT_EVERY_MIN)
        }
        return null
    }

    /**
     * দৈনিক কুরআন: ফজরের পরে (সূর্যোদয়ের ১৫ মিনিট আগে) বা বাছাই করা সময়ে পুরস্কার;
     * ইশা শুরুর ৪৫ মিনিট পরে (ইশার শেষের আগে) লক্ষ্য পূরণ না হলে ক্ষতি। লক্ষ্য পূরণ হলে কোনোটাই নয়।
     */
    fun quranFor(date: LocalDate, windows: List<PrayerWindow>, input: PlannerInput): List<PlannedNotif> {
        val ex = input.extras
        if (!ex.quran || date in input.quranDone) return emptyList()
        val fajr = windows.first { it.prayer == Prayer.FAJR }
        val isha = windows.first { it.prayer == Prayer.ISHA }
        val daily = ex.quranAt?.let { date.atTime(it).atZone(fajr.start.zone) } ?: fajr.end.minusMinutes(15)
        val loss = minOf(isha.start.plusMinutes(45), isha.end.minusMinutes(10))
        return listOfNotNull(
            PlannedNotif(date, null, NotifKind.QURAN_DAILY, daily),
            PlannedNotif(date, null, NotifKind.QURAN_LOSS, loss).takeIf { it.fireAt.isAfter(daily.plusMinutes(60)) },
        )
    }

    /**
     * রোজা: রমজানে সাহরি (ফজরের suhoorLead মিনিট আগে), সাহরি শেষের ১০ মিনিট আগে, ইফতারের ১০ মিনিট আগে,
     * শেষ দশকের বেজোড় রাতে ইশার ৯০ মিনিট পরে কদর; রমজানের আগের সন্ধ্যায় মাগরিবের ৩০ মিনিট পরে।
     * রমজানের বাইরে: কাল নফল রোজার দিন হলে আজ ইশার ৩০ মিনিট পরে (সুন্নত দিনলিপিতে চালু থাকলে)।
     * বিরতিতে (মহিলা) রোজার রিমাইন্ডার নেই।
     */
    fun fastingFor(date: LocalDate, windows: List<PrayerWindow>, input: PlannerInput): List<PlannedNotif> {
        if (Pause.isPaused(date, input.pausedFrom)) return emptyList()
        val ex = input.extras
        val h = input.hijri[date]
        val tomorrowH = input.hijri[date.plusDays(1)]
        val w = windows.associateBy { it.prayer }
        val fajr = w.getValue(Prayer.FAJR)
        val maghrib = w.getValue(Prayer.MAGHRIB)
        val isha = w.getValue(Prayer.ISHA)
        val out = ArrayList<PlannedNotif>()
        fun add(k: NotifKind, at: ZonedDateTime) { out += PlannedNotif(date, null, k, at) }
        if (ex.fasting) {
            if (h?.isRamadan == true) {
                add(NotifKind.SUHOOR, fajr.start.minusMinutes(ex.suhoorLeadMin.toLong()))
                add(NotifKind.SUHOOR_END, fajr.start.minusMinutes(Fasting.SUHOOR_END_WARN_MIN))
                if (date !in input.fastLogged) add(NotifKind.IFTAR, maghrib.start.minusMinutes(Fasting.IFTAR_BEFORE_MIN))
                if (Fasting.isOddQadrNightTonight(h)) add(NotifKind.QADR_NIGHT, minOf(isha.start.plusMinutes(90), isha.end.minusMinutes(10)))
            }
            if (h?.isRamadan != true && tomorrowH?.isRamadan == true) add(NotifKind.RAMADAN_EVE, maghrib.start.plusMinutes(30))
        }
        if (NotifKind.FAST_EVE in ex.routine && tomorrowH?.isRamadan != true) {
            val reasons = Fasting.naflReasons(date.plusDays(1), tomorrowH, input.shawwalDone).filter { it in ex.nafl }
            if (reasons.isNotEmpty()) add(NotifKind.FAST_EVE, isha.start.plusMinutes(30))
        }
        return out
    }

    /**
     * যাকাত: বার্ষিক হিজরি তারিখের ৭ দিন আগে আর সেদিন (সূর্যোদয়ের ৩ ঘণ্টা পরে)।
     * সদকা: বাছাই করা বারে আসরের ৩০ মিনিট পরে। জিলহজ: ১ জিলহজের আগের সন্ধ্যায় মাগরিবের ২০ মিনিট পরে।
     * input.hijri-তে তারিখ+৭ পর্যন্ত থাকা দরকার।
     */
    fun charityFor(date: LocalDate, windows: List<PrayerWindow>, input: PlannerInput): List<PlannedNotif> {
        val ex = input.extras
        val w = windows.associateBy { it.prayer }
        val morning = w.getValue(Prayer.FAJR).end.plusHours(3)
        val out = ArrayList<PlannedNotif>()
        fun add(k: NotifKind, at: ZonedDateTime) { out += PlannedNotif(date, null, k, at) }
        ex.zakatHijri?.let { (m, d) ->
            fun hits(x: LocalDate) = input.hijri[x]?.let { it.month == m && it.day == d } == true
            if (hits(date)) add(NotifKind.ZAKAT_DAY, morning)
            if (hits(date.plusDays(7))) add(NotifKind.ZAKAT_SOON, morning)
        }
        if (ex.sadaqahDay != null && date.dayOfWeek == ex.sadaqahDay) add(NotifKind.SADAQAH_WEEKLY, w.getValue(Prayer.ASR).start.plusMinutes(30))
        if (ex.dhulHijjah) {
            val t = input.hijri[date.plusDays(1)]
            if (t != null && t.month == 12 && t.day == 1) add(NotifKind.DHUL_HIJJAH_EVE, w.getValue(Prayer.MAGHRIB).start.plusMinutes(20))
        }
        return out
    }

    /** বিরতির ৩য় দিন থেকে যোহরের ৩০ মিনিট পরে একবার — "বিরতি শেষ হলে জানান" */
    fun pauseCheckFor(date: LocalDate, windows: List<PrayerWindow>, input: PlannerInput): PlannedNotif? {
        val from = input.pausedFrom ?: return null
        if (!Pause.isPaused(date, from) || Pause.dayNumber(date, from) < Pause.CHECK_AFTER_DAYS) return null
        val dhuhr = windows.first { it.prayer == Prayer.DHUHR }
        return PlannedNotif(date, null, NotifKind.PAUSE_CHECK, dhuhr.start.plusMinutes(30))
    }

    fun kindsFor(p: Prayer, input: PlannerInput, date: LocalDate? = null): List<NotifKind> {
        // ঘরে-একা ওয়াক্ত: তীব্রতা বা অভ্যাস যা-ই হোক, পুরো ডাক — এখানেই অলসতা জেতে
        if (date != null && (date to p) in input.home) {
            return listOf(
                NotifKind.N1_INVITE, NotifKind.N1_LOSS,
                NotifKind.HOME_NUDGE_1, NotifKind.HOME_NUDGE_2, NotifKind.HOME_NUDGE_3,
                NotifKind.N3_LAST_CALL,
            )
        }
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
