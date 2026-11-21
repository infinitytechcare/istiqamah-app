package com.istiqamah.app.core

import java.time.LocalDate

// =====================================================================
// v0.12 — ইলম ও দাওয়াহ
//   ইলম: মুখস্থ সূরার রিভিশন (ধাপে ধাপে বাড়ানো বিরতি), প্রতিদিন একটু শেখার হিসাব
//   দাওয়াহ: যাদের নামাজের দাওয়াত দিতে চাই তাদের তালিকা (এনক্রিপ্টেড), দাওয়াতের আদব
// =====================================================================

/** মুখস্থের একটি অংশ — [to] null হলে পুরো সূরা */
data class HifzItem(val key: String, val title: String, val surah: Int, val from: Int = 1, val to: Int? = null)

/** কোনো অংশ মুখস্থ: কোন ধাপে আছে আর শেষ কবে রিভিশন হয়েছে */
data class HifzState(val level: Int, val last: LocalDate)

enum class IlmSource(val code: Int, val bn: String) {
    TAFSIR(1, "কুরআনের অর্থ/তাফসির"),
    HADITH(2, "হাদিস"),
    BOOK(3, "দ্বীনি বই"),
    LECTURE(4, "আলোচনা/লেকচার"),
    MAJLIS(5, "আলেমের মজলিস"),
    HIFZ(6, "মুখস্থ");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c }
    }
}

/** দাওয়াতের অবস্থা — চাপ নয়, ধাপে ধাপে */
enum class DawahStage(val code: Int, val bn: String) {
    DUA(0, "দোয়া করছি"),
    TALKED(1, "নরম করে বলেছি"),
    TOGETHER(2, "একসাথে নামাজে গিয়েছি"),
    PRAYING(3, "নিয়মিত হচ্ছে, আলহামদুলিল্লাহ");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: DUA
    }
}

data class DawahPerson(val id: Long, val name: String, val stage: DawahStage, val note: String, val last: LocalDate?)

object Ilm {
    /** রিভিশনের বিরতি (দিন) — ঠিক মনে থাকলে পরের ধাপ, ভুলে গেলে আবার শুরু */
    val INTERVALS = listOf(1, 3, 7, 14, 30, 60)

    val HIFZ: List<HifzItem> = buildList {
        add(HifzItem("fatiha", "সূরা আল-ফাতিহা", 1))
        add(HifzItem("kursi", "আয়াতুল কুরসি (২:২৫৫)", 2, 255, 255))
        add(HifzItem("baqarah_end", "সূরা বাকারার শেষ দুই আয়াত", 2, 285, 286))
        add(HifzItem("kahf_10", "সূরা কাহফের প্রথম ১০ আয়াত", 18, 1, 10))
        add(HifzItem("yasin", "সূরা ইয়াসিন", 36))
        add(HifzItem("mulk", "সূরা আল-মুলক", 67))
        // আমপারা: ১১৪ থেকে উল্টো — ছোট থেকে বড়
        (114 downTo 78).forEach { add(HifzItem("s$it", "সূরা নং ${Bn.num(it)}", it)) }
    }

    fun byKey(key: String): HifzItem? = HIFZ.firstOrNull { it.key == key }

    fun interval(level: Int): Int = INTERVALS[level.coerceIn(0, INTERVALS.lastIndex)]

    fun nextDue(s: HifzState): LocalDate = s.last.plusDays(interval(s.level).toLong())

    fun isDue(s: HifzState, today: LocalDate): Boolean = !nextDue(s).isAfter(today)

    /** রিভিশনের ফল: মনে ছিল → পরের ধাপ, ভুলে গেছি → প্রথম ধাপ */
    fun review(s: HifzState, remembered: Boolean, today: LocalDate): HifzState =
        HifzState(if (remembered) (s.level + 1).coerceAtMost(INTERVALS.lastIndex) else 0, today)

    /** আজ রিভিশন বাকি — সবচেয়ে পুরনোটা আগে */
    fun dueToday(states: Map<String, HifzState>, today: LocalDate): List<String> =
        states.filter { isDue(it.value, today) }.entries.sortedBy { nextDue(it.value) }.map { it.key }

    /** টানা কত দিন শেখা হয়েছে (আজ না হলে গতকাল থেকে) */
    fun streak(days: Set<LocalDate>, today: LocalDate): Int {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    val ILM_AYAT = listOf(AyahRef(20, 114), AyahRef(39, 9), AyahRef(58, 11))
    val ILM_HADITH = listOf("h_ibnmajah_224", "h_muslim_2699", "h_bukhari_71", "h_bukhari_5027")

    val DAWAH_AYAT = listOf(AyahRef(41, 33), AyahRef(16, 125), AyahRef(20, 44), AyahRef(28, 56))
    val DAWAH_HADITH = listOf("h_muslim_1893", "h_bukhari_2942", "h_bukhari_3461")

    /** দাওয়াতের আদব */
    val DAWAH_TIPS = listOf(
        "আগে নিজে — আপনার নামাজই সবচেয়ে বড় দাওয়াত",
        "নরম কথা — মুসা (আ.)-কে ফিরআউনের কাছেও নরম কথা বলতে বলা হয়েছিল (২০:৪৪)",
        "একান্তে বলুন, সবার সামনে নয় — লজ্জা দেবেন না",
        "তর্ক নয়, ভালোবাসা; একবারে সব নয়, এক ওয়াক্ত দিয়ে শুরু",
        "সাথে নিয়ে যান — 'চলো একসাথে মসজিদে যাই' বলা অনেক সহজ",
        "হিদায়াত আল্লাহর হাতে (২৮:৫৬) — দোয়া কখনো ছাড়বেন না",
        "যা নিশ্চিত জানেন শুধু তা-ই বলুন; নবী ﷺ-এর নামে ভুল কথা ভয়ংকর (বুখারি ৩৪৬১)",
    )

    /** শেয়ারের লেখা: আয়াত/হাদিসের অর্থ আর সূত্র */
    fun shareText(bn: String, ref: String): String = "$bn\n— $ref"
}
