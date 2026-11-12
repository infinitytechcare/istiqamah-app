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

/** নোটিফিকেশনের জোড়া: করলে লাভ + না করলে ক্ষতি */
data class GainLoss(val gain: ContentItem?, val loss: ContentItem?)

/** কোন আমলে কোন ট্যাগ আর কী লেবেল — নোটিফিকেশনের লেখা এখান থেকে */
enum class Amal(
    val gainGeneral: String?, val lossGeneral: String?,
    val gainLabel: String, val lossLabel: String,
) {
    SALAH("salah_virtue", "salah_warning", "পড়লে লাভ", "না পড়লে ক্ষতি"),
    DHIKR("gain_dhikr", "loss_dhikr", "যিকিরের লাভ", "আল্লাহকে ভুলে থাকার ক্ষতি"),
    MUHASABA("gain_muhasaba", "loss_muhasaba", "হিসাব নিলে লাভ", "গাফেল থাকার ক্ষতি"),
    JUMUAH("gain_jumuah", "loss_jumuah", "জুমার লাভ", "জুমা ছাড়ার ক্ষতি");

    companion object {
        /** ওয়াক্ত-নির্দিষ্ট ট্যাগ, যেমন gain_fajr / loss_isha (যোহর-মাগরিবে নেই, সাধারণটাই) */
        fun specificFor(p: Prayer): Pair<String, String>? = when (p) {
            Prayer.FAJR -> "gain_fajr" to "loss_fajr"
            Prayer.ASR -> "gain_asr" to "loss_asr"
            Prayer.ISHA -> "gain_isha" to "loss_isha"
            else -> null
        }
    }
}

/**
 * জোড়াটাকে নোটিফিকেশনের লেখায় সাজানো। lossFirst = শেষ সতর্কতার সময় (আগে ক্ষতি, তারপর লাভ)।
 * @return (এক লাইনের সংক্ষেপ, পূর্ণ লেখা)
 */
fun GainLoss.format(amal: Amal, lossFirst: Boolean = false): Pair<String, String> {
    val g = gain?.let { "✅ ${amal.gainLabel}:\n${it.shortBn}\n— ${it.refBn}" }
    val l = loss?.let { "⚠️ ${amal.lossLabel}:\n${it.shortBn}\n— ${it.refBn}" }
    val parts = if (lossFirst) listOfNotNull(l, g) else listOfNotNull(g, l)
    val short = if (lossFirst) (loss ?: gain) else (gain ?: loss)
    val label = if (short === loss) amal.lossLabel else amal.gainLabel
    return (short?.let { "$label: ${it.shortBn}" } ?: "") to parts.joinToString("\n\n")
}

/**
 * একটা মাত্র পুরস্কার বা ক্ষতির নোটিফিকেশনের লেখা। (এক লাইনের সংক্ষেপ, পূর্ণ লেখা)
 * ক্ষতির শেষে সবসময় আশার একটা লাইন — ভয় যেন হতাশায় না গড়ায়।
 */
fun ContentItem.formatSingle(amal: Amal, gain: Boolean, hopeLine: String? = null): Pair<String, String> {
    val label = if (gain) "✅ ${amal.gainLabel}" else "⚠️ ${amal.lossLabel}"
    val body = "$label:\n$shortBn\n— $refBn" + (hopeLine?.let { "\n\n$it" } ?: "")
    return "${if (gain) amal.gainLabel else amal.lossLabel}: $shortBn" to body
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

    /** একাধিক ট্যাগের যেকোনোটা মিললেই প্রার্থী; exclude-এর আইডি বাদ */
    fun pickAny(
        items: List<ContentItem>,
        tags: Collection<String>,
        lastShown: Map<String, LocalDate>,
        today: LocalDate,
        salt: Int = 0,
        exclude: Set<String> = emptySet(),
    ): ContentItem? {
        val pool = items.filter { it.id !in exclude && it.tags.any { t -> t in tags } }
        // pick() এক ট্যাগ চায় — পুলের জন্য একটা কৃত্রিম ট্যাগ বসিয়ে একই নিয়মে বাছাই
        val tagged = pool.map { it.copy(tags = it.tags + POOL_TAG) }
        return pick(tagged, POOL_TAG, lastShown, today, salt)?.let { chosen -> pool.first { it.id == chosen.id } }
    }

    private const val POOL_TAG = "__pool__"

    /**
     * আগে "major" (সবচেয়ে বড় পুরস্কার/ক্ষতি) — যদি ৩০ দিনে না-দেখানো কোনোটা থাকে; নইলে পুরো পুল থেকে।
     */
    fun pickMajorFirst(
        items: List<ContentItem>,
        tags: Collection<String>,
        lastShown: Map<String, LocalDate>,
        today: LocalDate,
        salt: Int = 0,
        exclude: Set<String> = emptySet(),
    ): ContentItem? {
        val freshMajor = items.filter { item ->
            "major" in item.tags && item.id !in exclude && item.tags.any { it in tags } &&
                (lastShown[item.id]?.let { ChronoUnit.DAYS.between(it, today) >= REPEAT_GAP_DAYS } ?: true)
        }
        if (freshMajor.isNotEmpty()) return pickAny(freshMajor, tags, lastShown, today, salt)
        return pickAny(items, tags, lastShown, today, salt, exclude)
    }

    /** একটা আমলের পুরস্কার বা ক্ষতির ট্যাগ (সাধারণ + দরকার হলে ওয়াক্ত-নির্দিষ্ট) */
    fun tagsFor(amal: Amal, prayer: Prayer?, gain: Boolean): List<String> {
        val specific = prayer?.let { Amal.specificFor(it) }?.let { if (gain) it.first else it.second }
        return listOfNotNull(if (gain) amal.gainGeneral else amal.lossGeneral, specific)
    }

    /**
     * লাভ-ক্ষতির জোড়া: একটা "করলে লাভ", একটা "না করলে ক্ষতি" — কখনো একই আইটেম নয়।
     * নির্দিষ্ট ট্যাগ (যেমন gain_fajr) থাকলে preferSpecific দিনে শুধু সেগুলো থেকে, নইলে সাধারণ+নির্দিষ্ট মিলিয়ে।
     */
    fun gainLoss(
        items: List<ContentItem>,
        gainGeneral: String?, gainSpecific: String?,
        lossGeneral: String?, lossSpecific: String?,
        lastShown: Map<String, LocalDate>,
        today: LocalDate,
        salt: Int = 0,
        preferSpecific: Boolean = false,
    ): GainLoss {
        fun tags(general: String?, specific: String?): List<String> {
            val hasSpecific = specific != null && items.any { specific in it.tags }
            return if (preferSpecific && hasSpecific) listOf(specific!!) else listOfNotNull(general, specific)
        }
        val gain = pickAny(items, tags(gainGeneral, gainSpecific), lastShown, today, salt)
        val loss = pickAny(items, tags(lossGeneral, lossSpecific), lastShown, today, salt + 1, exclude = setOfNotNull(gain?.id))
        return GainLoss(gain, loss)
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
