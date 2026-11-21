package com.istiqamah.app.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

// =====================================================================
// v0.10 — আখলাক ও হুকুকুল ইবাদ (ইহসান — মানুষের সাথে)
// বান্দার হক আল্লাহ নিজে মাফ করেন না, যার হক তাকে ফিরিয়ে দিতে হয় — "দেউলিয়া" হাদিস (মুসলিম ২৫৮১)।
// ছয়টি ক্ষেত্র: বাবা-মা, আত্মীয়, প্রতিবেশী, জবান, ঋণ ও আমানত, কাজে সততা।
// আয়াত quran.json থেকে, হাদিস content.json থেকে (আইডি ধরে)।
// =====================================================================

data class HaqqArea(
    val key: String,
    val title: String,
    val subtitle: String,
    val ayat: List<AyahRef>,
    val hadith: List<String>,
    /** ছোট, আজই করা যায় এমন কাজ */
    val actions: List<String>,
    /** সাপ্তাহিক আত্মজিজ্ঞাসা */
    val check: String,
)

/** সাপ্তাহিক উত্তর: ০ = না, ১ = কিছুটা, ২ = হ্যাঁ */
enum class CheckAnswer(val score: Int, val bn: String) {
    NO(0, "না"), PARTLY(1, "কিছুটা"), YES(2, "হ্যাঁ");

    companion object {
        fun fromScore(s: Int) = entries.firstOrNull { it.score == s }
    }
}

/** দেনা/পাওনা/আমানত — নাম ও অঙ্ক এনক্রিপ্ট করে ফোনে, নোটিফিকেশনে কখনো নয় */
enum class DebtKind(val code: Int, val bn: String, val remind: Boolean) {
    I_OWE(1, "আমার দেনা", true),
    OWED_TO_ME(2, "আমার পাওনা", false),
    AMANAH(3, "আমার কাছে আমানত", true);

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: I_OWE
    }
}

data class DebtEntry(
    val id: Long,
    val kind: DebtKind,
    val who: String,
    val amount: Long,
    val note: String,
    val due: LocalDate?,
    val done: Boolean,
)

object Akhlaq {
    val AREAS: List<HaqqArea> = listOf(
        HaqqArea(
            "parents", "বাবা-মা", "আল্লাহর ইবাদতের পরেই তাঁদের হক",
            listOf(AyahRef(17, 23, 24), AyahRef(31, 14)),
            listOf("h_bukhari_5971", "h_muslim_2551"),
            listOf(
                "আজ বাবা-মাকে ফোন করুন বা দেখা করে খোঁজ নিন",
                "তাঁদের জন্য দোয়া: রব্বির হামহুমা কামা রব্বায়ানি সগিরা (১৭:২৪)",
                "তাঁদের সাথে নরম গলায় কথা — বিরক্তিতে 'উফ' পর্যন্ত নয় (১৭:২৩)",
                "তাঁদের একটা কাজ আজ নিজে করে দিন",
                "তাঁরা না থাকলে: দোয়া, তাঁদের নামে সদকা, তাঁদের বন্ধুদের সম্মান (মুসলিম ১৬৩১)",
            ),
            "এ সপ্তাহে বাবা-মার খোঁজ নিয়েছি, তাঁদের সাথে নরম ব্যবহার করেছি",
        ),
        HaqqArea(
            "kin", "আত্মীয়তা", "সম্পর্ক জোড়া রাখা — রিজিক ও আয়ুতে বরকত",
            listOf(AyahRef(4, 1), AyahRef(47, 22, 23)),
            listOf("h_bukhari_5986", "h_bukhari_5991", "h_bukhari_5984"),
            listOf(
                "যে আত্মীয়ের সাথে অনেকদিন কথা হয়নি, আজ তাকে ফোন করুন",
                "যে সম্পর্ক ছিন্ন করেছে, আপনি আগে যোগাযোগ করুন (বুখারি ৫৯৯১)",
                "অসুস্থ আত্মীয়কে দেখতে যান বা খোঁজ নিন",
                "অভাবী আত্মীয়কে সাহায্য — এতে সদকা আর আত্মীয়তা দুই সওয়াব (তিরমিযি ৬৫৮)",
            ),
            "এ সপ্তাহে অন্তত একজন আত্মীয়ের খোঁজ নিয়েছি",
        ),
        HaqqArea(
            "neighbor", "প্রতিবেশী", "জিবরাইল (আ.) বারবার যাদের কথা বলেছেন",
            listOf(AyahRef(4, 36)),
            listOf("h_bukhari_6014", "h_bukhari_6016"),
            listOf(
                "প্রতিবেশীকে সালাম দিন, খোঁজ নিন",
                "রান্না হলে একটু পাঠান — তরকারির ঝোল একটু বাড়িয়ে নিন (মুসলিম ২৬২৫)",
                "শব্দ, ময়লা, গাড়ি রাখা — প্রতিবেশীর কষ্ট হয় এমন কিছু নয়",
                "বিপদে বা অসুস্থতায় প্রতিবেশীর পাশে দাঁড়ান",
            ),
            "এ সপ্তাহে প্রতিবেশীকে কষ্ট দিইনি, ভালো ব্যবহার করেছি",
        ),
        HaqqArea(
            "tongue", "জবান", "ভালো কথা বলো, নইলে চুপ থাকো",
            listOf(AyahRef(49, 12), AyahRef(33, 70, 71)),
            listOf("h_bukhari_6018", "h_muslim_2589", "h_bukhari_6094", "h_bukhari_6474"),
            listOf(
                "কারও অনুপস্থিতিতে তার কথা উঠলে শুধু ভালোটা বলুন, নইলে চুপ",
                "গীবত শুরু হলে নরম করে কথা ঘুরিয়ে দিন",
                "মজা করেও মিথ্যা নয়",
                "রাগ হলে চুপ থাকুন — নবী ﷺ বলেছেন: রাগ কোরো না (বুখারি ৬১১৬)",
                "আজ তিনজনকে আন্তরিক ধন্যবাদ বা প্রশংসা করুন",
            ),
            "এ সপ্তাহে গীবত, মিথ্যা আর কটু কথা থেকে বেঁচেছি",
        ),
        HaqqArea(
            "debt", "ঋণ ও আমানত", "শহীদেরও মাফ হয় না ঋণ",
            listOf(AyahRef(4, 58), AyahRef(2, 280)),
            listOf("h_muslim_1886", "h_bukhari_2387", "h_bukhari_33"),
            listOf(
                "সব ঋণ লিখে রাখুন — কাকে, কত, কবে (২:২৮২)",
                "শোধের একটা তারিখ ঠিক করুন, অল্প হলেও সেদিন শোধ করুন",
                "কারও আমানত — টাকা, জিনিস, গোপন কথা — যত্নে রাখুন, চাইলেই ফেরত দিন",
                "দেওয়া কথা রাখুন — ওয়াদা ভাঙা মুনাফিকের চিহ্ন (বুখারি ৩৩)",
                "অভাবী দেনাদারকে সময় দিন, পারলে মাফ করুন (২:২৮০)",
            ),
            "এ সপ্তাহে দেওয়া কথা ও আমানত রক্ষা করেছি, ঋণ শোধে টালবাহানা করিনি",
        ),
        HaqqArea(
            "work", "কাজে সততা", "চাকরি-ব্যবসাও আমানত",
            listOf(AyahRef(83, 1, 3), AyahRef(8, 27)),
            listOf("h_muslim_102", "h_bukhari_2079"),
            listOf(
                "অফিসের সময় পুরোটা কাজে দিন — এটাও আমানত",
                "পণ্য বা কাজের ত্রুটি গোপন করবেন না",
                "অফিসের জিনিস নিজের কাজে নয়",
                "ঘুষ বা অন্যায় সুবিধা নয় — যে দেয় আর যে নেয়, দুজনই অভিশপ্ত (আবু দাউদ ৩৫৮০)",
                "যে কাজ হাতে নিয়েছেন, সুন্দর করে শেষ করুন",
            ),
            "এ সপ্তাহে কাজে ফাঁকি, প্রতারণা বা অন্যায় সুবিধা নিইনি",
        ),
    )

    /** শুরুর হাদিস: সর্বোত্তম চরিত্র, মিযানে ভারী, দেউলিয়া */
    val INTRO_HADITH = listOf("h_bukhari_6035", "h_abudawud_4799", "h_muslim_2581")

    fun byKey(key: String): HaqqArea? = AREAS.firstOrNull { it.key == key }

    /** সব কাজ মিলিয়ে একটা তালিকা — প্রতিদিন পরেরটা */
    val ALL_ACTIONS: List<Pair<HaqqArea, String>> get() = AREAS.flatMap { a -> a.actions.map { a to it } }

    fun actionFor(d: LocalDate): Pair<HaqqArea, String> {
        // ক্ষেত্রগুলো পালা করে আসে (দিন ১ বাবা-মা, দিন ২ আত্মীয় …), প্রতি চক্রে ক্ষেত্রের পরের কাজ
        val day = d.toEpochDay()
        val area = AREAS[Math.floorMod(day, AREAS.size.toLong()).toInt()]
        val round = Math.floorDiv(day, AREAS.size.toLong())
        return area to area.actions[Math.floorMod(round, area.actions.size.toLong()).toInt()]
    }

    /** একটি সপ্তাহের উত্তর থেকে শতাংশ (উত্তর না দেওয়া ক্ষেত্র বাদ) */
    fun weekPercent(answers: Map<String, Int>): Int? {
        val v = answers.filterKeys { k -> AREAS.any { it.key == k } }.values
        if (v.isEmpty()) return null
        return v.sum() * 100 / (2 * v.size)
    }

    /** কয়েক সপ্তাহের গড় (যে সপ্তাহে উত্তর আছে শুধু সেগুলো) */
    fun average(weeks: List<Map<String, Int>>): Int? = weeks.mapNotNull { weekPercent(it) }.takeIf { it.isNotEmpty() }?.average()?.toInt()

    // ---------- দেনা-পাওনা ----------

    /** যেসব খোলা দেনা/আমানতের তারিখ আছে — রিমাইন্ডারের জন্য শুধু তারিখ (নাম-অঙ্ক নয়) */
    fun dueDates(entries: List<DebtEntry>): Set<LocalDate> =
        entries.filter { !it.done && it.kind.remind }.mapNotNull { it.due }.toSet()

    fun overdue(entries: List<DebtEntry>, today: LocalDate): List<DebtEntry> =
        entries.filter { !it.done && it.kind.remind && it.due != null && it.due.isBefore(today) }

    fun openTotal(entries: List<DebtEntry>, kind: DebtKind): Long = entries.filter { !it.done && it.kind == kind }.sumOf { it.amount }

    /** "৩ দিন বাকি" / "আজ" / "২ দিন পেরিয়েছে" */
    fun dueLabel(due: LocalDate, today: LocalDate): String {
        val n = ChronoUnit.DAYS.between(today, due)
        return when {
            n == 0L -> "আজ"
            n > 0 -> "${Bn.num(n)} দিন বাকি"
            else -> "${Bn.num(-n)} দিন পেরিয়েছে"
        }
    }

    /** রিমাইন্ডার: তারিখের ৩ দিন আগে আর সেদিন */
    const val DEBT_REMIND_BEFORE = 3L
}
