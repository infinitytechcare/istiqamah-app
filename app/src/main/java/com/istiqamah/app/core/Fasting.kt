package com.istiqamah.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/**
 * v0.7 — রোজা ও রমজান।
 * হিজরি তারিখ: java-র উম্মুল কুরা ক্যালেন্ডার + বাংলাদেশের চাঁদ দেখার জন্য সংশোধন (সেটিংস → হিজরি তারিখ সংশোধন)।
 */
data class HijriDay(val year: Int, val month: Int, val day: Int) {
    val isRamadan: Boolean get() = month == 9
    val monthBn: String get() = MONTHS[month - 1]
    val bn: String get() = "${Bn.num(day)} $monthBn ${Bn.num(year)}"

    companion object {
        val MONTHS = listOf("মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জুমাদাল উলা", "জুমাদাল আখিরাহ", "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ")
    }
}

object Hijri {
    fun of(d: LocalDate, adjustDays: Int = 0): HijriDay? = try {
        val h = HijrahDate.from(d.plusDays(adjustDays.toLong()))
        HijriDay(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    } catch (e: Exception) {
        null // ক্যালেন্ডারের সীমার বাইরে
    }
}

/** নফল রোজার কারণ */
enum class NaflFast(val code: Int, val bn: String) {
    MON_THU(0, "সোম/বৃহস্পতিবারের সুন্নত রোজা"),
    AYYAM_BID(1, "আইয়ামে বীয (চাঁদের ১৩–১৫)"),
    ARAFAH(2, "আরাফার রোজা — দুই বছরের গুনাহ মাফ"),
    ASHURA(3, "আশুরার রোজা — এক বছরের গুনাহ মাফ"),
    SHAWWAL(4, "শাওয়ালের ছয় রোজা");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c }
    }
}

enum class FastStatus(val code: Int, val bn: String) {
    FASTED(1, "রোজা রেখেছি"),
    MISSED(2, "রাখিনি"),
    EXCUSED(3, "অসুস্থ/সফর/বিরতি — পরে কাযা");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c }
    }
}

object Fasting {
    /** সাহরির শেষ: ফজরের ওয়াক্ত শুরু; সাবধানতার জন্য তার ১০ মিনিট আগে শেষ ডাক */
    const val SUHOOR_END_WARN_MIN = 10L
    const val IFTAR_BEFORE_MIN = 10L
    const val SHAWWAL_TARGET = 6

    /** রোজা নিষিদ্ধ: ঈদুল ফিতর, ঈদুল আযহা ও তাশরিকের তিন দিন */
    fun forbidden(h: HijriDay?): Boolean = h != null && ((h.month == 10 && h.day == 1) || (h.month == 12 && h.day in 10..13))

    /** এই তারিখে (দিনের বেলা) কোন কোন নফল রোজা রাখা যায় */
    fun naflReasons(date: LocalDate, h: HijriDay?, shawwalDone: Int = 0): List<NaflFast> {
        if (forbidden(h) || h?.isRamadan == true) return emptyList()
        val out = ArrayList<NaflFast>()
        if (date.dayOfWeek == DayOfWeek.MONDAY || date.dayOfWeek == DayOfWeek.THURSDAY) out += NaflFast.MON_THU
        if (h != null) {
            if (h.day in 13..15) out += NaflFast.AYYAM_BID
            if (h.month == 12 && h.day == 9) out += NaflFast.ARAFAH
            if (h.month == 1 && h.day in 9..10) out += NaflFast.ASHURA
            if (h.month == 10 && h.day >= 2 && shawwalDone < SHAWWAL_TARGET) out += NaflFast.SHAWWAL
        }
        return out
    }

    /**
     * আজ সন্ধ্যার রাতটা কি শেষ দশকের বেজোড় রাত? ইসলামি দিন মাগরিবে শুরু হয়, তাই
     * রমজানের ২০ তারিখের সন্ধ্যা = ২১তম রাত।
     */
    fun isOddQadrNightTonight(h: HijriDay?): Boolean {
        if (h == null || !h.isRamadan) return false
        val night = h.day + 1
        return night >= 21 && night % 2 == 1
    }

    /** রমজানের বাকি দিনে খতম করতে দিনে কয় পৃষ্ঠা */
    fun khatmPagesPerDay(currentPage: Int, pageCount: Int, daysLeft: Int): Int {
        val remaining = (pageCount - currentPage + 1).coerceAtLeast(0)
        return if (daysLeft <= 0) remaining else Math.ceil(remaining / daysLeft.toDouble()).toInt()
    }

    /** রমজান শেষ হতে কয় দিন বাকি (আজ ধরে) — ৩০ দিন ধরে হিসাব, চাঁদ দেখায় এক দিন কম হতে পারে */
    fun ramadanDaysLeft(h: HijriDay?): Int = if (h?.isRamadan == true) 30 - h.day + 1 else 0
}
