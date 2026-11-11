package com.istiqamah.app.core

import java.time.LocalDate

/** একটা ওয়াক্তের অবস্থা। code ডেটাবেসে রাখা হয় — বদলাবেন না। */
enum class PrayerStatus(val code: Int, val bn: String, val prayed: Boolean, val onTime: Boolean) {
    JAMAAH(1, "জামাতে", prayed = true, onTime = true),
    ALONE(2, "একা, সময়মতো", prayed = true, onTime = true),
    QAZA(3, "কাযা", prayed = true, onTime = false),
    MISSED(4, "পড়িনি", prayed = false, onTime = false),
    /** মহিলাদের বিরতি — গোপন; স্ট্রিক ভাঙে না, হিসাবেও ধরা হয় না */
    EXCUSED(5, "বিরতি", prayed = false, onTime = false);

    companion object {
        fun fromCode(c: Int): PrayerStatus? = entries.firstOrNull { it.code == c }
    }
}

/** এক দিনের লগ: যে ওয়াক্ত লগ হয়নি সেটা map-এ নেই। */
data class DayLog(val date: LocalDate, val statuses: Map<Prayer, PrayerStatus>) {
    fun status(p: Prayer): PrayerStatus? = statuses[p]
}

/**
 * ঈমানি যাত্রার ৪টা ধাপ। ধাপ কখনো নিচে নামে না।
 */
enum class Stage(val number: Int, val bn: String, val subtitle: String) {
    TAWBAH(1, "ফিরে আসা", "তাওবাহ — একটা ওয়াক্ত দিয়ে শুরু"),
    ISLAM(2, "ভিত গড়া", "ইসলাম — ধীরে ধীরে পাঁচ ওয়াক্ত"),
    IMAN(3, "দৃঢ় হওয়া", "ঈমান — পাঁচ ওয়াক্ত নিয়মিত"),
    IHSAN(4, "উন্নতি", "ইহসান — সুন্নত, নফল ও খুশু");

    companion object {
        fun fromNumber(n: Int): Stage = entries.firstOrNull { it.number == n } ?: TAWBAH
    }
}

data class StageState(
    val stage: Stage,
    /** যে ওয়াক্তগুলোতে পুরো রিমাইন্ডার চলে ও স্ট্রিক গোনা হয় */
    val focus: Set<Prayer>,
    /** বর্তমান ধাপ বা শেষ ওয়াক্ত যোগের তারিখ */
    val since: LocalDate,
)

data class StageChange(val newState: StageState, val messageBn: String)

object Habit {

    /** ওয়াক্ত যোগের ক্রম: সহজ থেকে কঠিন — ফজর সবশেষে */
    val ADD_ORDER = listOf(Prayer.MAGHRIB, Prayer.ISHA, Prayer.DHUHR, Prayer.ASR, Prayer.FAJR)

    /**
     * অনবোর্ডিং-এর উত্তর থেকে শুরুর ধাপ।
     * @param avgDailyPrayers গত সপ্তাহে দিনে গড় ওয়াক্ত: 0, 1 (১–২), 3 (৩–৪), 5
     * @param chosenFirst ধাপ ১-এ ইউজার কোন ওয়াক্ত দিয়ে শুরু করতে চান
     */
    fun initialStage(avgDailyPrayers: Int, chosenFirst: Prayer, today: LocalDate): StageState = when {
        avgDailyPrayers >= 5 -> StageState(Stage.IMAN, Prayer.entries.toSet(), today)
        avgDailyPrayers >= 2 -> {
            // ইতিমধ্যে যা পড়েন তার কাছাকাছি: প্রথম ৩টা ওয়াক্ত দিয়ে শুরু
            val focus = (listOf(chosenFirst) + ADD_ORDER).distinct().take(3).toSet()
            StageState(Stage.ISLAM, focus, today)
        }
        else -> StageState(Stage.TAWBAH, setOf(chosenFirst), today)
    }

    /**
     * দিনের শেষে (বা অ্যাপ খোলায়) ধাপ মূল্যায়ন। শর্ত পূরণ হলে নতুন অবস্থা ফেরত দেয়, না হলে null।
     * logs: অন্তত গত ৩০ দিনের লগ (আজ বাদে) — যে দিন নেই সেটা সব ওয়াক্ত "লগ নেই" ধরা হয়।
     */
    fun evaluateStage(state: StageState, logs: Map<LocalDate, DayLog>, today: LocalDate): StageChange? {
        fun daysWhere(n: Int, pred: (DayLog?) -> Boolean): Int =
            (1..n).count { pred(logs[today.minusDays(it.toLong())]) }

        val daysSince = java.time.temporal.ChronoUnit.DAYS.between(state.since, today)

        when (state.stage) {
            Stage.TAWBAH -> {
                // ৭ দিনের মধ্যে ৫ দিন ফোকাস ওয়াক্ত পড়া হলে (কাযা হলেও চলবে)
                if (daysSince >= 5) {
                    val ok = daysWhere(7) { d -> state.focus.all { d?.status(it)?.prayed == true } }
                    if (ok >= 5) {
                        val next = nextPrayerToAdd(state.focus)
                        return StageChange(
                            StageState(Stage.ISLAM, state.focus + listOfNotNull(next), today),
                            "আলহামদুলিল্লাহ! আপনি ধাপ ২-এ উঠেছেন। এবার যোগ হলো ${next?.bn ?: ""}।"
                        )
                    }
                }
            }
            Stage.ISLAM -> {
                if (state.focus.size < 5) {
                    // প্রতি ১৪ দিনে, ফোকাস ওয়াক্তগুলো ১৪ দিনের অন্তত ১০ দিন পড়া হলে নতুন ওয়াক্ত
                    if (daysSince >= 14) {
                        val ok = daysWhere(14) { d -> state.focus.all { d?.status(it)?.prayed == true } }
                        if (ok >= 10) {
                            val next = nextPrayerToAdd(state.focus) ?: return null
                            return StageChange(
                                state.copy(focus = state.focus + next, since = today),
                                "মাশাআল্লাহ! এবার ${next.bn}ও আপনার লক্ষ্যে যোগ হলো।"
                            )
                        }
                    }
                } else {
                    // ১৪ দিনে ৫ ওয়াক্তের ৫০%+ সময়মতো
                    if (daysSince >= 14 && onTimeRatio(logs, today, 14) >= 0.5) {
                        return StageChange(
                            StageState(Stage.IMAN, Prayer.entries.toSet(), today),
                            "আলহামদুলিল্লাহ! আপনি ধাপ ৩-এ — পাঁচ ওয়াক্ত এখন আপনার জীবনের অংশ।"
                        )
                    }
                }
            }
            Stage.IMAN -> {
                if (daysSince >= 30 && onTimeRatio(logs, today, 30) >= 0.9) {
                    return StageChange(
                        StageState(Stage.IHSAN, Prayer.entries.toSet(), today),
                        "আল্লাহ কবুল করুন! আপনি ধাপ ৪-এ। এবার সুন্নত ও নফলের দিকে এগোই।"
                    )
                }
            }
            Stage.IHSAN -> Unit
        }
        return null
    }

    fun nextPrayerToAdd(focus: Set<Prayer>): Prayer? = ADD_ORDER.firstOrNull { it !in focus }

    /** গত n দিনে (আজ বাদে) সময়মতো পড়া ওয়াক্তের অনুপাত; বিরতির ওয়াক্ত হিসাব থেকে বাদ */
    fun onTimeRatio(logs: Map<LocalDate, DayLog>, today: LocalDate, n: Int): Double {
        var total = 0; var ok = 0
        for (i in 1..n) {
            val d = logs[today.minusDays(i.toLong())]
            for (p in Prayer.entries) {
                val s = d?.status(p)
                if (s == PrayerStatus.EXCUSED) continue
                total++
                if (s?.onTime == true) ok++
            }
        }
        return if (total == 0) 0.0 else ok.toDouble() / total
    }

    /**
     * স্ট্রিক: পরপর কত দিন ফোকাস ওয়াক্তগুলো পড়া হয়েছে।
     * - কাযা করলেও দিন গোনা হয় (ফিরে আসাকে পুরস্কার)
     * - বিরতির ওয়াক্ত দিন ভাঙে না
     * - আজকের দিন অসম্পূর্ণ হলে বাদ (ভাঙে না), সম্পূর্ণ হলে গোনা হয়
     */
    fun streak(focus: Set<Prayer>, logs: Map<LocalDate, DayLog>, today: LocalDate): Int {
        fun complete(d: DayLog?): Boolean = focus.all {
            val s = d?.status(it)
            s?.prayed == true || s == PrayerStatus.EXCUSED
        }
        var count = if (complete(logs[today])) 1 else 0
        var day = today.minusDays(1)
        while (complete(logs[day])) {
            count++
            day = day.minusDays(1)
            if (count > 3650) break
        }
        return count
    }

    /**
     * এই ওয়াক্ত কি "অভ্যাসে পরিণত"? — গত ১৪ দিন টানা সময়মতো।
     * তখন N2/N3 বন্ধ থাকে; একবার ছুটলেই আবার চালু।
     */
    fun isHabitFormed(p: Prayer, logs: Map<LocalDate, DayLog>, today: LocalDate): Boolean =
        (1..14).all { i ->
            val s = logs[today.minusDays(i.toLong())]?.status(p)
            s?.onTime == true || s == PrayerStatus.EXCUSED
        }

    /** আজকের আগের দিনগুলোতে যে ওয়াক্ত লগ হয়নি বা "পড়িনি" — সেগুলোর পরামর্শমূলক কাযা তালিকা (গত n দিন) */
    fun recentMissed(logs: Map<LocalDate, DayLog>, today: LocalDate, n: Int, focus: Set<Prayer>): List<Pair<LocalDate, Prayer>> =
        (1..n).flatMap { i ->
            val date = today.minusDays(i.toLong())
            val d = logs[date]
            focus.filter { d?.status(it) == PrayerStatus.MISSED }.map { date to it }
        }
}
