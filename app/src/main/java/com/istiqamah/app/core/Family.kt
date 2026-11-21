package com.istiqamah.app.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// =====================================================================
// v0.11 — পরিবার: স্বামী-স্ত্রীর হক, সন্তানের তারবিয়াহ, পারিবারিক হালাকা
// "তোমরা প্রত্যেকে দায়িত্বশীল, আর প্রত্যেককে তার দায়িত্ব সম্পর্কে জিজ্ঞেস করা হবে" (বুখারি ৮৯৩)
// =====================================================================

enum class Marital(val code: Int, val bn: String) {
    UNSET(0, "বলা হয়নি"), SINGLE(1, "অবিবাহিত"), MARRIED(2, "বিবাহিত");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: UNSET
    }
}

/** সন্তান — নাম আর জন্মের মাস (বয়স বের করতে); এনক্রিপ্ট করে শুধু ফোনে */
data class Child(val id: Long, val name: String, val born: YearMonth)

data class ChildStage(val fromAge: Int, val toAge: Int, val title: String, val items: List<String>)

data class HalaqaSession(
    val title: String,
    val ayah: AyahRef,
    val hadith: String,
    /** পরিবারে আলোচনার প্রশ্ন */
    val question: String,
    /** একসাথে মুখস্থ করার কুরআনি দোয়া */
    val dua: AyahRef,
)

object Family {
    /** স্বামী-স্ত্রী উভয়ের জন্য কুরআন-হাদিস */
    val SPOUSE_AYAT = listOf(AyahRef(30, 21), AyahRef(4, 19), AyahRef(25, 74))
    val SPOUSE_HADITH = listOf("h_tirmidhi_3895", "h_muslim_1469", "h_bukhari_55", "h_bukhari_676", "h_abudawud_1308")
    val FAMILY_HADITH = listOf("h_bukhari_893", "h_bukhari_5997", "h_bukhari_2587", "h_muslim_1631")

    /** স্বামীর জন্য ছোট কাজ */
    val HUSBAND_ACTS = listOf(
        "ঘরে ঢুকে সালাম আর হাসিমুখ",
        "ঘরের একটা কাজে হাত লাগান — নবী ﷺ ঘরের কাজ করতেন (বুখারি ৬৭৬)",
        "স্ত্রীর মুখে এক লোকমা খাবার তুলে দিন — এটাও সদকা (বুখারি ৫৬)",
        "তার একটা ভালো গুণের কথা মুখে বলুন (মুসলিম ১৪৬৯)",
        "তার বাবা-মা ও পরিবারের খোঁজ নিন",
        "একসাথে দুই রাকাত বা কয়েকটা আয়াত পড়ুন",
        "রাগ হলে চুপ — পরে শান্ত হয়ে কথা",
    )

    /** স্ত্রীর জন্য ছোট কাজ */
    val WIFE_ACTS = listOf(
        "স্বামী ঘরে এলে হাসিমুখে সালাম",
        "তার একটা চেষ্টা বা কাজের জন্য ধন্যবাদ বলুন",
        "তার বাবা-মা ও পরিবারের সাথে সুন্দর ব্যবহার",
        "একসাথে দুই রাকাত বা কয়েকটা আয়াত পড়ুন",
        "তার অগোচরে তার জন্য দোয়া করুন (মুসলিম ২৭৩৩)",
        "তার একটা ভালো গুণের কথা মুখে বলুন",
        "রাগ হলে চুপ — পরে শান্ত হয়ে কথা",
    )

    fun spouseActs(g: Gender): List<String> = if (g == Gender.FEMALE) WIFE_ACTS else HUSBAND_ACTS

    fun spouseActFor(g: Gender, d: LocalDate): String {
        val l = spouseActs(g)
        return l[Math.floorMod(d.toEpochDay(), l.size.toLong()).toInt()]
    }

    val CHILD_STAGES = listOf(
        ChildStage(0, 2, "শিশু (০–২ বছর)", listOf(
            "সুন্দর, অর্থবহ নাম (মুসলিম ২১৩২)",
            "সপ্তম দিনে আকিকা (তিরমিযি ১৫২২)",
            "বেশি বেশি আদর আর দোয়া — যে দয়া করে না, সে দয়া পায় না (বুখারি ৫৯৯৭)",
            "ঘরে কুরআন তিলাওয়াত শোনান",
        )),
        ChildStage(3, 6, "ছোটবেলা (৩–৬ বছর)", listOf(
            "কালিমা আর 'বিসমিল্লাহ'",
            "খাওয়া, ঘুম, টয়লেটের ছোট দোয়া",
            "সালাম দেওয়া শেখানো",
            "ছোট সূরা: ফাতিহা, ইখলাস, ফালাক, নাস",
            "নবী-রাসূলদের গল্প",
            "আপনার পাশে দাঁড়িয়ে নামাজের অনুকরণ",
        )),
        ChildStage(7, 9, "নামাজ শেখা (৭–৯ বছর)", listOf(
            "সাত বছরে নামাজের আদেশ — ভালোবেসে, সাথে নিয়ে (আবু দাউদ ৪৯৫)",
            "ওযু হাতে-কলমে শেখানো",
            "প্রতিদিন অন্তত এক ওয়াক্ত একসাথে পড়া",
            "ছেলেকে মসজিদে সাথে নেওয়া",
            "তিন সময়ে অনুমতি নিয়ে ঘরে ঢোকা (২৪:৫৮)",
            "সত্য বলার প্রশংসা — ভুল স্বীকার করলে শাস্তি নয়",
        )),
        ChildStage(10, 13, "দৃঢ় হওয়া (১০–১৩ বছর)", listOf(
            "দশ বছরে নামাজে আরও দৃঢ়তা — শাসন হোক ভালোবাসায়, কষ্ট দিয়ে নয় (আবু দাউদ ৪৯৫)",
            "ভাই-বোনের বিছানা আলাদা (আবু দাউদ ৪৯৫)",
            "রমজানে কয়েকটা রোজার অনুশীলন",
            "বালেগ হওয়ার মাসআলা (পবিত্রতা, গোসল) — ছেলেকে বাবা, মেয়েকে মা",
            "বন্ধু আর মোবাইল নিয়ে খোলাখুলি কথা",
        )),
        ChildStage(14, 17, "কৈশোর (১৪–১৭ বছর)", listOf(
            "সব ফরজ এখন তার নিজের দায়িত্ব — পাশে থাকুন, বন্ধুর মতো",
            "ভালো বন্ধু বাছাই — মানুষ তার বন্ধুর পথে চলে (আবু দাউদ ৪৮৩৩)",
            "লজ্জাশীলতা ও পর্দা",
            "দায়িত্ব দিন: ঘরের কাজ, ছোট ভাইবোনের দেখাশোনা",
            "হালাল উপার্জন আর ভবিষ্যৎ নিয়ে আলাপ",
        )),
        ChildStage(18, 200, "প্রাপ্তবয়স্ক (১৮+)", listOf(
            "পরামর্শ দিন, চাপিয়ে দেবেন না",
            "বিয়ের সময় হলে দ্বীনদারি দেখে (বুখারি ৫০৯০)",
            "তার জন্য দোয়া চালিয়ে যান — নেক সন্তানের দোয়া বাবা-মার কবরেও পৌঁছায় (মুসলিম ১৬৩১)",
        )),
    )

    /** পূর্ণ বছরে বয়স */
    fun age(c: Child, today: LocalDate): Int = ChronoUnit.YEARS.between(c.born, YearMonth.from(today)).toInt().coerceAtLeast(0)

    fun stageFor(age: Int): ChildStage = CHILD_STAGES.first { age in it.fromAge..it.toAge }

    /** নামাজের হিসাব রাখা হয় ৭ বছর থেকে */
    const val SALAH_FROM_AGE = 7

    fun prays(c: Child, today: LocalDate) = age(c, today) >= SALAH_FROM_AGE

    /** শেষ [n] দিনে গড়ে দিনে কয় ওয়াক্ত (যে দিনগুলোতে লেখা আছে) */
    fun salahAverage(counts: Map<LocalDate, Int>, today: LocalDate, n: Int): Double? {
        val v = (0 until n).mapNotNull { counts[today.minusDays(it.toLong())] }
        return v.takeIf { it.isNotEmpty() }?.average()
    }

    val HALAQA = listOf(
        HalaqaSession("নামাজ — ঘরের খুঁটি", AyahRef(20, 132), "h_bukhari_528",
            "আমাদের ঘরে কোন ওয়াক্তটা সবচেয়ে বেশি ছুটে যায়? কীভাবে একসাথে পড়া যায়?", AyahRef(14, 40)),
        HalaqaSession("একে অপরের প্রতি দয়া", AyahRef(30, 21), "h_bukhari_5997",
            "এ সপ্তাহে কে কাকে খুশি করেছে? আগামী সপ্তাহে কাকে খুশি করব?", AyahRef(25, 74)),
        HalaqaSession("সত্য কথা", AyahRef(33, 70, 71), "h_bukhari_6094",
            "কখন মিথ্যা বলতে ইচ্ছা করে? তখন কী করলে সত্য বলা সহজ হয়?", AyahRef(20, 114)),
        HalaqaSession("বাবা-মায়ের সম্মান", AyahRef(17, 23, 24), "h_bukhari_5971",
            "দাদা-দাদি, নানা-নানির জন্য এ সপ্তাহে আমরা কী করতে পারি?", AyahRef(17, 24)),
        HalaqaSession("আল্লাহ সব দেখেন", AyahRef(50, 18), "h_bukhari_50",
            "কেউ না দেখলেও এ সপ্তাহে কোন ভালো কাজটা করব?", AyahRef(2, 201)),
        HalaqaSession("কৃতজ্ঞতা", AyahRef(14, 7), "h_muslim_2999",
            "আজকের তিনটা নিয়ামত কী, যার জন্য আলহামদুলিল্লাহ বলব?", AyahRef(14, 40)),
        HalaqaSession("জবানের হেফাজত", AyahRef(49, 12), "h_bukhari_6018",
            "কারও পেছনে কথা উঠলে আমরা কী বলব?", AyahRef(25, 74)),
        HalaqaSession("জান্নাতের আশা", AyahRef(32, 17), "h_bukhari_3244",
            "জান্নাতে গিয়ে প্রথমে কী চাইবে? সেখানে যেতে আজ কোন কাজটা করবে?", AyahRef(2, 201)),
    )

    /** সপ্তাহ অনুযায়ী হালাকা — প্রতি সপ্তাহে পরেরটা */
    fun halaqaFor(d: LocalDate): HalaqaSession {
        val weeks = Math.floorDiv(d.toEpochDay() + 3, 7L) // ১৯৭০-০১-০১ বৃহস্পতিবার → সোমবার থেকে সপ্তাহ
        return HALAQA[Math.floorMod(weeks, HALAQA.size.toLong()).toInt()]
    }

    /** গত ৪ সপ্তাহে কত সপ্তাহ হালাকা হয়েছে → শতাংশ */
    fun halaqaPercent(doneWeeks: Set<String>, today: LocalDate): Int =
        (0 until 4).count { Ihsan.weekKey(today.minusWeeks(it.toLong())) in doneWeeks } * 25
}
