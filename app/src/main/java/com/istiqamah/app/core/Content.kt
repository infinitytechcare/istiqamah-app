package com.istiqamah.app.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * একটা আয়াত বা হাদিস। assets/content.json থেকে আসে।
 * reviewedBy ফাঁকা থাকলে অ্যাপে "রিভিউ বাকি" চিহ্ন দেখায়।
 */
data class ContentItem(
    val id: String,
    val tags: List<String>,
    /** "ayah" বা "hadith" */
    val type: String,
    /** দেখানোর মতো সূত্র, যেমন "সূরা আল-মাউন ১০৭:৪–৫" বা "সহিহ মুসলিম ৮২" */
    val refBn: String,
    val surah: Int? = null,
    val ayahFrom: Int? = null,
    val ayahTo: Int? = null,
    val arabic: String = "",
    val bn: String,
    val bnSource: String,
    /** নোটিফিকেশনের জন্য এক লাইন (৮০ অক্ষরের মধ্যে) */
    val shortBn: String,
    /** "gentle" | "encourage" | "serious" | "hope" */
    val tone: String,
    /** এই আইটেমের পরে অবশ্যই দেখাতে হবে এমন ট্যাগ (যেমন সতর্কতার পরে hope_mercy) */
    val pairWith: String? = null,
    /** হাদিসের মান, যেমন "সহিহ" — আয়াতের জন্য null */
    val grading: String? = null,
    val reviewedBy: String? = null,
    /** সূত্র যাচাইয়ের লিংক (quran.com / sunnah.com) */
    val link: String? = null,
) {
    val isReviewed: Boolean get() = !reviewedBy.isNullOrBlank()
}

object ContentPicker {
    /** একই আইটেম আবার দেখানোর আগে কমপক্ষে এত দিন */
    const val REPEAT_GAP_DAYS = 30L

    /**
     * ট্যাগ মিলিয়ে একটা আইটেম বাছাই।
     * - গত ৩০ দিনে না দেখানো আইটেমগুলো থেকে তারিখ-ভিত্তিক (দিনে স্থির) বাছাই
     * - সব দেখা হয়ে গেলে যেটা সবচেয়ে আগে দেখানো হয়েছিল
     * @param salt একই দিনে একই ট্যাগে ভিন্ন আইটেম পেতে (যেমন ওয়াক্ত অনুযায়ী)
     */
    fun pick(
        items: List<ContentItem>,
        tag: String,
        lastShown: Map<String, LocalDate>,
        today: LocalDate,
        salt: Int = 0,
    ): ContentItem? {
        val tagged = items.filter { tag in it.tags }
        if (tagged.isEmpty()) return null
        val fresh = tagged.filter { item ->
            val last = lastShown[item.id] ?: return@filter true
            ChronoUnit.DAYS.between(last, today) >= REPEAT_GAP_DAYS
        }
        if (fresh.isNotEmpty()) {
            val idx = Math.floorMod(today.toEpochDay() * 31 + salt * 7, fresh.size.toLong()).toInt()
            return fresh.sortedBy { it.id }[idx]
        }
        return tagged.minByOrNull { lastShown[it.id] ?: LocalDate.MIN }
    }

    /** আইটেম + (দরকার হলে) তার জোড়া আশার বার্তা */
    fun pickWithPair(
        items: List<ContentItem>,
        tag: String,
        lastShown: Map<String, LocalDate>,
        today: LocalDate,
        salt: Int = 0,
    ): Pair<ContentItem, ContentItem?>? {
        val main = pick(items, tag, lastShown, today, salt) ?: return null
        val pair = main.pairWith?.let { pick(items, it, lastShown, today, salt + 1) }
        return main to pair
    }
}

/** বাংলা সংখ্যা ও সময় */
object Bn {
    private val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

    fun num(n: Number): String = num(n.toString())
    fun num(s: String): String = buildString { s.forEach { c -> append(if (c in '0'..'9') digits[c - '0'] else c) } }

    /** "ভোর ৪:৫৩" ধরনের সময় */
    fun time(t: java.time.LocalTime): String {
        val h = t.hour
        val part = when (h) {
            in 3..5 -> "ভোর"
            in 6..11 -> "সকাল"
            in 12..14 -> "দুপুর"
            in 15..16 -> "বিকাল"
            17 -> if (t.minute < 45) "বিকাল" else "সন্ধ্যা"
            18 -> "সন্ধ্যা"
            else -> "রাত"
        }
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "$part ${num(h12)}:${num(String.format(java.util.Locale.US, "%02d", t.minute))}"
    }

    /** ৯০ → "১ ঘণ্টা ৩০ মিনিট" */
    fun duration(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return when {
            h == 0L -> "${num(m)} মিনিট"
            m == 0L -> "${num(h)} ঘণ্টা"
            else -> "${num(h)} ঘণ্টা ${num(m)} মিনিট"
        }
    }

    private val months = listOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
    private val hijriMonths = listOf("মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জুমাদাল উলা", "জুমাদাল আখিরাহ", "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ")
    private val weekdays = listOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")

    fun date(d: LocalDate): String = "${weekdays[d.dayOfWeek.value - 1]}, ${num(d.dayOfMonth)} ${months[d.monthValue - 1]}"
    fun monthYear(d: LocalDate): String = "${months[d.monthValue - 1]} ${num(d.year)}"

    /** হিজরি তারিখ; adjustDays দিয়ে বাংলাদেশের চাঁদ দেখার সাথে মেলানো যায় */
    fun hijri(d: LocalDate, adjustDays: Int = 0): String = try {
        val h = java.time.chrono.HijrahDate.from(d.plusDays(adjustDays.toLong()))
        val day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
        val month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR)
        val year = h.get(java.time.temporal.ChronoField.YEAR)
        "${num(day)} ${hijriMonths[month - 1]} ${num(year)} হিজরি"
    } catch (e: Exception) {
        ""
    }
}
