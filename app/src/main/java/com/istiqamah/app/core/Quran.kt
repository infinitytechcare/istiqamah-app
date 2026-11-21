package com.istiqamah.app.core

import java.time.LocalDate

/**
 * v0.6 — দৈনিক কুরআন।
 * আয়াতের ক্রমিক: ১ (১:১) থেকে ৬২৩৬ (১১৪:৬); ৬২৩৭ = শেষের পরের জায়গা (খতম)।
 * পৃষ্ঠা = মাদানি মুসহাফের ৬০৪ পৃষ্ঠা।
 */
class QuranIndex(
    /** প্রতিটি সূরার প্রথম আয়াতের ক্রমিক (১১৪টি) */
    private val surahStarts: IntArray,
    /** প্রতিটি পৃষ্ঠা/পারা/রুকুর প্রথম আয়াতের ক্রমিক */
    private val pageStarts: IntArray,
    private val juzStarts: IntArray,
    private val rukuStarts: IntArray,
    val sajda: Set<Int> = emptySet(),
) {
    val total: Int = TOTAL
    val pageCount: Int get() = pageStarts.size
    val juzCount: Int get() = juzStarts.size

    /** starts-এ id কোন ঘরে (১-ভিত্তিক) */
    private fun slot(starts: IntArray, id: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= id) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    fun surahOf(id: Int): Int = slot(surahStarts, id.coerceIn(1, total))
    fun ayahNo(id: Int): Int = id.coerceIn(1, total) - surahStarts[surahOf(id) - 1] + 1
    fun pageOf(id: Int): Int = slot(pageStarts, id.coerceIn(1, total))
    fun juzOf(id: Int): Int = slot(juzStarts, id.coerceIn(1, total))
    fun idOf(surah: Int, ayah: Int): Int = surahStarts[surah - 1] + ayah - 1

    /** পৃষ্ঠার আয়াতগুলো (শেষটা বাদে-সীমানা) */
    fun pageRange(page: Int): IntRange {
        val p = page.coerceIn(1, pageCount)
        val end = if (p == pageCount) total else pageStarts[p] - 1
        return pageStarts[p - 1]..end
    }

    fun pageStart(page: Int): Int = pageStarts[page.coerceIn(1, pageCount) - 1]
    fun juzStart(juz: Int): Int = juzStarts[juz.coerceIn(1, juzCount) - 1]

    /** from থেকে to-এর আগ পর্যন্ত পড়লে কয় একক — একক-শুরুর সীমানা কয়টা পেরোল (শেষের পরে ৬২৩৭ = সীমানা) */
    fun units(from: Int, to: Int, unit: QuranUnit): Int {
        if (to <= from) return 0
        if (unit == QuranUnit.AYAH) return to - from
        val starts = when (unit) {
            QuranUnit.PAGE -> pageStarts
            QuranUnit.RUKU -> rukuStarts
            QuranUnit.JUZ -> juzStarts
            QuranUnit.AYAH -> return to - from
        }
        // (from, to] এর ভেতরে কয়টা শুরু; শেষে খতম হলে শেষ এককটাও গোনা
        val inside = starts.count { it in (from + 1)..to }
        return inside + if (to > total) 1 else 0
    }

    companion object {
        const val TOTAL = 6236
    }
}

enum class QuranUnit(val code: Int, val bn: String) {
    AYAH(0, "আয়াত"),
    PAGE(1, "পৃষ্ঠা"),
    RUKU(2, "রুকু"),
    JUZ(3, "পারা");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: PAGE
    }
}

data class QuranGoal(val unit: QuranUnit = QuranUnit.PAGE, val amount: Int = 1) {
    val bn: String get() = "দিনে ${Bn.num(amount)} ${unit.bn}"

    companion object {
        /** বাছাইয়ের তালিকা — ছোট থেকে বড়; "দিনে ২০ পৃষ্ঠা" = মাসে এক খতম */
        val PRESETS = listOf(
            QuranGoal(QuranUnit.AYAH, 5), QuranGoal(QuranUnit.AYAH, 10), QuranGoal(QuranUnit.PAGE, 1),
            QuranGoal(QuranUnit.RUKU, 1), QuranGoal(QuranUnit.PAGE, 2), QuranGoal(QuranUnit.PAGE, 4),
            QuranGoal(QuranUnit.PAGE, 10), QuranGoal(QuranUnit.PAGE, 20), QuranGoal(QuranUnit.JUZ, 1),
        )
    }
}

/** এক বসার পড়া: from থেকে to-এর আগ পর্যন্ত */
data class ReadEvent(val date: LocalDate, val from: Int, val to: Int)

object QuranProgress {
    fun unitsOn(date: LocalDate, events: List<ReadEvent>, index: QuranIndex, unit: QuranUnit): Int =
        events.filter { it.date == date }.sumOf { index.units(it.from, it.to, unit) }

    fun isDone(date: LocalDate, events: List<ReadEvent>, index: QuranIndex, goal: QuranGoal): Boolean =
        unitsOn(date, events, index, goal.unit) >= goal.amount

    /** টানা কত দিন লক্ষ্য পূরণ — আজ এখনো না হলে গতকাল থেকে গোনা */
    fun streak(events: List<ReadEvent>, index: QuranIndex, goal: QuranGoal, today: LocalDate): Int {
        var d = if (isDone(today, events, index, goal)) today else today.minusDays(1)
        var n = 0
        while (isDone(d, events, index, goal) && n < 3650) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    /** শেষ ১৪ দিনে দিনে গড়ে কয় পৃষ্ঠা */
    fun pagesPerDay(events: List<ReadEvent>, index: QuranIndex, today: LocalDate, days: Int = 14): Double {
        val from = today.minusDays(days.toLong() - 1)
        val pages = events.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }.sumOf { index.units(it.from, it.to, QuranUnit.PAGE) }
        return pages.toDouble() / days
    }

    /** এই গতিতে খতম হতে কত দিন (গতি শূন্য হলে null) */
    fun daysToKhatm(bookmark: Int, index: QuranIndex, perDay: Double): Int? {
        if (perDay <= 0.0) return null
        val remainingPages = index.pageCount - index.pageOf(bookmark) + 1
        return Math.ceil(remainingPages / perDay).toInt()
    }

    /** পড়ার পরে নতুন বুকমার্ক আর খতম হলো কি না */
    fun advance(to: Int): Pair<Int, Boolean> = if (to > QuranIndex.TOTAL) 1 to true else to.coerceAtLeast(1) to false
}
