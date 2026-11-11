package com.istiqamah.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

// =====================================================================
// ধাপের অগ্রগতি — "পরের ধাপে যেতে আর কতদূর"
// =====================================================================

data class StageProgress(
    /** যা মাপা হচ্ছে, যেমন "গত ৭ দিনে মাগরিব পড়া" */
    val labelBn: String,
    val current: Int,
    val target: Int,
    /** মাপের সংখ্যা শতাংশে কিনা (তখন current/target শতাংশ) */
    val percent: Boolean,
    /** ন্যূনতম অপেক্ষার বাকি দিন (০ হলে শর্ত পূরণ হলেই উন্নতি) */
    val waitDays: Int,
    /** পরের ধাপের নাম বা "নতুন ওয়াক্ত" */
    val nextBn: String,
) {
    val fraction: Float get() = if (target == 0) 1f else (current.toFloat() / target).coerceIn(0f, 1f)
}

object Progress {
    /** [Habit.evaluateStage]-এর একই শর্ত, ইউজারকে দেখানোর মতো করে। শেষ ধাপে null। */
    fun of(state: StageState, logs: Map<LocalDate, DayLog>, today: LocalDate): StageProgress? {
        val daysSince = ChronoUnit.DAYS.between(state.since, today).toInt()
        fun daysWhere(n: Int, pred: (DayLog?) -> Boolean) = (1..n).count { pred(logs[today.minusDays(it.toLong())]) }
        val focusNames = state.focus.sortedBy { it.ordinal }.joinToString(", ") { it.bn }
        return when (state.stage) {
            Stage.TAWBAH -> StageProgress(
                "গত ৭ দিনে $focusNames পড়েছেন", daysWhere(7) { d -> state.focus.all { d?.status(it)?.prayed == true } },
                5, false, (5 - daysSince).coerceAtLeast(0), "ধাপ ২ · ${Stage.ISLAM.bn}",
            )
            Stage.ISLAM -> if (state.focus.size < 5) {
                val next = Habit.nextPrayerToAdd(state.focus)
                StageProgress(
                    "গত ১৪ দিনে লক্ষ্যের সব ওয়াক্ত পড়েছেন", daysWhere(14) { d -> state.focus.all { d?.status(it)?.prayed == true } },
                    10, false, (14 - daysSince).coerceAtLeast(0), "নতুন ওয়াক্ত: ${next?.bn ?: ""}",
                )
            } else {
                StageProgress(
                    "গত ১৪ দিনে সময়মতো নামাজ", (Habit.onTimeRatio(logs, today, 14) * 100).roundToInt(),
                    50, true, (14 - daysSince).coerceAtLeast(0), "ধাপ ৩ · ${Stage.IMAN.bn}",
                )
            }
            Stage.IMAN -> StageProgress(
                "গত ৩০ দিনে সময়মতো নামাজ", (Habit.onTimeRatio(logs, today, 30) * 100).roundToInt(),
                90, true, (30 - daysSince).coerceAtLeast(0), "ধাপ ৪ · ${Stage.IHSAN.bn}",
            )
            Stage.IHSAN -> null
        }
    }
}

// =====================================================================
// সাপ্তাহিক পর্যালোচনা
// =====================================================================

data class WeeklyReview(
    val from: LocalDate,
    val to: LocalDate,
    /** প্রতি ওয়াক্তে কয় দিন সময়মতো (জামাতে/একা) */
    val onTime: Map<Prayer, Int>,
    /** প্রতি ওয়াক্তে কয় দিন পড়া হয়েছে (কাযাসহ) */
    val prayed: Map<Prayer, Int>,
    val onTimeTotal: Int,
    val prevOnTimeTotal: Int,
    val jamaahTotal: Int,
    /** সবচেয়ে কম সময়মতো পড়া ওয়াক্ত (সমান হলে ফজর আগে) */
    val hardest: Prayer?,
    val best: Prayer?,
    val muhasabaDays: Int,
    val azkarSessions: Int,
) {
    val change: Int get() = onTimeTotal - prevOnTimeTotal
}

object Weekly {
    /** today সহ শেষ ৭ দিন (শনি–শুক্র হলে পুরো সপ্তাহ), আগের ৭ দিনের সাথে তুলনা */
    fun review(
        logs: Map<LocalDate, DayLog>,
        today: LocalDate,
        muhasabaDates: Set<LocalDate> = emptySet(),
        azkarDone: Set<Pair<LocalDate, AzkarSet>> = emptySet(),
    ): WeeklyReview {
        val from = today.minusDays(6)
        val days = (0..6).map { from.plusDays(it.toLong()) }
        val prevDays = (7..13).map { today.minusDays(it.toLong()) }
        fun countOnTime(ds: List<LocalDate>, p: Prayer) = ds.count { logs[it]?.status(p)?.onTime == true }
        val onTime = Prayer.entries.associateWith { countOnTime(days, it) }
        val prayed = Prayer.entries.associateWith { p -> days.count { logs[it]?.status(p)?.prayed == true } }
        // বিরতির দিন বাদে তুলনা
        val counted = Prayer.entries.associateWith { p -> days.count { logs[it]?.status(p) != PrayerStatus.EXCUSED } }
        val rate = { p: Prayer -> if (counted.getValue(p) == 0) 1.0 else onTime.getValue(p).toDouble() / counted.getValue(p) }
        val hardest = Prayer.entries.filter { counted.getValue(it) > 0 }.minWithOrNull(compareBy<Prayer> { rate(it) }.thenBy { it.ordinal })
        val best = Prayer.entries.filter { counted.getValue(it) > 0 }.maxWithOrNull(compareBy<Prayer> { rate(it) }.thenByDescending { it.ordinal })
        return WeeklyReview(
            from = from, to = today,
            onTime = onTime, prayed = prayed,
            onTimeTotal = onTime.values.sum(),
            prevOnTimeTotal = Prayer.entries.sumOf { countOnTime(prevDays, it) },
            jamaahTotal = days.sumOf { d -> Prayer.entries.count { logs[d]?.status(it) == PrayerStatus.JAMAAH } },
            hardest = hardest.takeIf { it != null && rate(it) < 1.0 },
            best = best,
            muhasabaDays = days.count { it in muhasabaDates },
            azkarSessions = azkarDone.count { it.first in days },
        )
    }

    /** কঠিন ওয়াক্তের জন্য একটা বাস্তব পরামর্শ — পরের সপ্তাহের লক্ষ্য বাছাইয়ে প্রস্তাব */
    fun suggestions(hardest: Prayer?): List<String> = when (hardest) {
        Prayer.FAJR -> listOf(
            "ইশার পর আধা ঘণ্টার মধ্যে ঘুমাতে যাব",
            "ফোনের অ্যালার্ম বিছানা থেকে দূরে রাখব",
            "ঘুমানোর আগে ফজরে ওঠার নিয়ত করে দোয়া পড়ব",
        )
        Prayer.DHUHR -> listOf(
            "অফিস/ক্লাসে যোহরের জন্য ১৫ মিনিট আগে থেকে সময় আটকে রাখব",
            "জায়নামাজ ব্যাগে বা অফিসে রেখে দেব",
        )
        Prayer.ASR -> listOf(
            "আসরের আজান শুনলেই কাজ থামিয়ে ওযু করব",
            "আসর পড়ে তারপর বিকালের নাস্তা/বাইরে যাওয়া",
        )
        Prayer.MAGHRIB -> listOf(
            "মাগরিবের আগে বাসায় বা মসজিদের কাছে থাকার চেষ্টা করব",
            "আজান হলেই ৫ মিনিটের মধ্যে দাঁড়িয়ে যাব",
        )
        Prayer.ISHA -> listOf(
            "রাতের খাবারের আগে ইশা পড়ে নেব",
            "ইশা পড়ার আগে মোবাইল/টিভি নয়",
        )
        null -> listOf(
            "পাঁচ ওয়াক্তের একটা অন্তত জামাতে পড়ব",
            "প্রতিদিন সকাল-সন্ধ্যার আযকার পড়ব",
            "প্রতি রাতে মুহাসাবা করব",
        )
    }

    /** শুক্রবার কিনা (বাংলাদেশে সপ্তাহের শেষ দিন) */
    fun isReviewDay(d: LocalDate) = d.dayOfWeek == DayOfWeek.FRIDAY
}

// =====================================================================
// গুনাহ ছাড়ার ট্র্যাকার
// =====================================================================

/** ভুলের সময়ের পরিস্থিতি — কোড ডেটাবেসে যায়, বদলাবেন না */
enum class TriggerTag(val code: Int, val group: String, val bn: String) {
    ALONE_HOME(1, "কোথায়", "বাসায় একা"),
    OUTSIDE(2, "কোথায়", "বাইরে"),
    WORK(3, "কোথায়", "কাজে/ক্লাসে"),
    ONLINE(4, "কোথায়", "ফোনে/অনলাইনে"),
    WITH_FRIENDS(10, "কার সাথে", "বন্ধুদের সাথে"),
    WITH_FAMILY(11, "কার সাথে", "পরিবারের সাথে"),
    TIRED(20, "অনুভূতি", "ক্লান্ত"),
    BORED(21, "অনুভূতি", "একঘেয়ে লাগছিল"),
    ANGRY(22, "অনুভূতি", "রাগ"),
    WORRIED(23, "অনুভূতি", "দুশ্চিন্তা"),
    STRESSED(24, "অনুভূতি", "চাপে ছিলাম");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c }
    }
}

/** নিজের লেখা লক্ষ্য। title ডেটাবেসে এনক্রিপ্ট করা থাকে। */
data class SinGoal(val id: Long, val title: String, val startedOn: LocalDate, val active: Boolean)

data class SinEvent(val goalId: Long, val at: LocalDateTime, val tags: Set<TriggerTag>)

/** প্রস্তাবিত লক্ষ্য — ইউজার চাইলে নিজে লিখবেন */
val SIN_PRESETS = listOf(
    "গিবত (পরনিন্দা)", "মিথ্যা বলা", "গালি/খারাপ কথা", "চোখের হেফাজত", "গান-বাজনা", "রাগ",
    "অহংকার", "সময় নষ্ট (স্ক্রল)", "বাবা-মার সাথে কড়া কথা", "নামাজ দেরিতে পড়া",
)

data class SinStats(
    val cleanDays: Int,
    val bestCleanDays: Int,
    val eventsLast30: Int,
    /** ৫টার বেশি ঘটনা হলে প্যাটার্ন: যেমন "বেশিরভাগ সময় রাত ১১টার পর" */
    val insights: List<String>,
)

object Sins {
    const val MIN_EVENTS_FOR_PATTERN = 5

    fun stats(goal: SinGoal, events: List<SinEvent>, today: LocalDate): SinStats {
        val mine = events.filter { it.goalId == goal.id }.sortedBy { it.at }
        val last = mine.lastOrNull()?.at?.toLocalDate()
        val clean = ChronoUnit.DAYS.between(last ?: goal.startedOn, today).toInt().coerceAtLeast(0)
        // সেরা ধারা: শুরু→প্রথম ঘটনা, ঘটনা→ঘটনা, শেষ ঘটনা→আজ
        val points = listOf(goal.startedOn) + mine.map { it.at.toLocalDate() } + listOf(today)
        val best = points.zipWithNext().maxOfOrNull { (a, b) -> ChronoUnit.DAYS.between(a, b).toInt() } ?: clean
        return SinStats(
            cleanDays = clean,
            bestCleanDays = maxOf(best, clean),
            eventsLast30 = mine.count { !it.at.toLocalDate().isBefore(today.minusDays(30)) },
            insights = insights(mine),
        )
    }

    /** সময়ের ভাগ — বাংলায় */
    fun timeBucket(h: Int): String = when (h) {
        in 5..11 -> "সকালে"
        in 12..15 -> "দুপুরে"
        in 16..18 -> "বিকাল-সন্ধ্যায়"
        in 19..22 -> "রাতে (১০টার আগে)"
        else -> "গভীর রাতে (১১টার পর)"
    }

    fun insights(events: List<SinEvent>): List<String> {
        if (events.size < MIN_EVENTS_FOR_PATTERN) return emptyList()
        val out = ArrayList<String>()
        val n = events.size
        events.groupingBy { timeBucket(it.at.hour) }.eachCount().maxByOrNull { it.value }?.let { (bucket, c) ->
            if (c * 2 >= n) out += "বেশিরভাগ সময় হয়েছে $bucket (${Bn.num(n)}বারের মধ্যে ${Bn.num(c)}বার)"
        }
        events.flatMap { it.tags }.groupingBy { it }.eachCount()
            .filter { it.value * 2 >= n }
            .entries.sortedByDescending { it.value }
            .take(2)
            .forEach { (t, c) -> out += "${t.group}: ${t.bn} (${Bn.num(n)}বারের মধ্যে ${Bn.num(c)}বার)" }
        val weekend = events.count { it.at.dayOfWeek == DayOfWeek.FRIDAY || it.at.dayOfWeek == DayOfWeek.SATURDAY }
        if (weekend * 10 >= n * 6) out += "ছুটির দিনে বেশি হয় (শুক্র-শনি)"
        return out
    }
}

// =====================================================================
// আযকার
// =====================================================================

enum class AzkarSet(val code: Int, val bn: String, val whenBn: String) {
    MORNING(1, "সকালের আযকার", "ফজরের পর থেকে সূর্য ওঠা পর্যন্ত উত্তম"),
    EVENING(2, "সন্ধ্যার আযকার", "আসরের পর থেকে সূর্যাস্ত পর্যন্ত উত্তম"),
    AFTER_SALAH(3, "নামাজের পরের যিকির", "প্রতি ফরজ নামাজের সালাম ফেরানোর পর"),
    SLEEP(4, "ঘুমের আগের আমল", "বিছানায় যাওয়ার পর");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c }
    }
}

data class AzkarItem(
    val id: String,
    val set: AzkarSet,
    val order: Int,
    val titleBn: String,
    val arabic: String,
    /** বাংলা উচ্চারণ — দীর্ঘ কুরআনের অংশে ফাঁকা থাকতে পারে */
    val pronunciationBn: String,
    val meaningBn: String,
    val count: Int,
    val refBn: String,
    val grading: String,
    /** ফজিলত বা নির্দেশনা, যেমন "ফজর ও মাগরিবের পর ৩ বার" */
    val noteBn: String = "",
    val reviewedBy: String? = null,
)

object Azkar {
    /** একটা সেটে কতটা পড়া হলো (০..১) — প্রতি আইটেম তার সংখ্যা পর্যন্ত গোনা হয় */
    fun completion(items: List<AzkarItem>, counts: Map<String, Int>): Float {
        val total = items.sumOf { it.count }
        if (total == 0) return 0f
        val done = items.sumOf { minOf(counts[it.id] ?: 0, it.count) }
        return done.toFloat() / total
    }

    fun isDone(items: List<AzkarItem>, counts: Map<String, Int>): Boolean =
        items.isNotEmpty() && items.all { (counts[it.id] ?: 0) >= it.count }

    /** এখন কোন সেট পড়ার সময় (হোম স্ক্রিনে প্রস্তাব) */
    fun suggested(now: java.time.ZonedDateTime, windows: List<PrayerWindow>): AzkarSet? {
        val w = windows.associateBy { it.prayer }
        val fajr = w[Prayer.FAJR] ?: return null
        val asr = w[Prayer.ASR] ?: return null
        val isha = w[Prayer.ISHA] ?: return null
        return when {
            !now.isBefore(fajr.start) && now.isBefore(fajr.end.plusHours(3)) -> AzkarSet.MORNING
            !now.isBefore(asr.start) && now.isBefore(isha.start) -> AzkarSet.EVENING
            !now.isBefore(isha.start) -> AzkarSet.SLEEP
            else -> null
        }
    }
}
