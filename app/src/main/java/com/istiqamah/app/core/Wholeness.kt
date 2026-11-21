package com.istiqamah.app.core

import kotlin.math.roundToInt

// =====================================================================
// v0.9 — সব আমল মিলিয়ে মাপ ("আমলের পূর্ণতা")
// ধাপ (তাওবা → ইসলাম → ঈমান → ইহসান) আগের মতোই নামাজ দিয়ে ওঠে — কিয়ামতে প্রথম হিসাব নামাজের,
// আর নিয়ম বদলালে কারও অর্জিত ধাপ নড়ে যেত। এটা তার পাশে পুরো ছবি: গত ৩০ দিনে কোন আমল কতটা,
// আর সবচেয়ে দুর্বল জায়গায় পরের ছোট পদক্ষেপ।
// =====================================================================

enum class AmalArea(val bn: String, val route: String?, val nextStep: String) {
    SALAH("সময়মতো নামাজ", null, "পরের ওয়াক্তটা আযানের ১৫ মিনিটের মধ্যে পড়ুন"),
    SUNNAH("১২ রাকাত সুন্নাত", "ihsan", "আজ ফজরের আগের দুই রাকাত দিয়ে শুরু করুন"),
    QURAN("কুরআন", "reader", "আজ অন্তত এক পৃষ্ঠা পড়ুন"),
    AZKAR("সকাল-সন্ধ্যার যিকির", "azkar:MORNING", "আজ সকাল বা সন্ধ্যার যিকিরটা পড়ুন"),
    MUHASABA("রাতের মুহাসাবা", "muhasaba", "আজ রাতে ২ মিনিটের মুহাসাবা করুন"),
    FASTING("নফল রোজা", "fasting", "এ মাসে আইয়ামে বীযের ৩টি রোজা রাখার নিয়ত করুন"),
    SADAQAH("সদকা", "zakat", "এ সপ্তাহে গোপনে কিছু সদকা করুন — অল্প হলেও"),
    IMAN("ঈমানের পাঠ", "iman", "ঈমানের পরের পাঠটা পড়ে কুইজ দিন"),
    AKHLAQ("আখলাক ও বান্দার হক", "akhlaq", "এ সপ্তাহের আত্মজিজ্ঞাসা করুন, আর আজকের একটি ভালো আচরণ"),
    FAMILY("পারিবারিক হালাকা", "family", "এ সপ্তাহে পরিবার নিয়ে ১৫ মিনিটের হালাকা করুন"),
}

data class AreaScore(val area: AmalArea, val percent: Int, val detail: String)

data class WholenessInput(
    val days: Int = 30,
    /** গত [days] দিনে লক্ষ্যের ওয়াক্তগুলোর মধ্যে সময়মতো পড়ার অনুপাত (০..১) */
    val salahOnTime: Double,
    /** কুরআন চালু থাকলে লক্ষ্য পূরণের দিন; বন্ধ থাকলে null — তখন মাপে আসে না */
    val quranDays: Int?,
    /** সকাল বা সন্ধ্যার যিকির হয়েছে এমন দিন */
    val azkarDays: Int,
    val muhasabaDays: Int,
    /** নফল রোজা (রমজানের বাইরে) — রোজার ফিচার চালু না থাকলে null */
    val naflFasts: Int?,
    /** গত ৪ সপ্তাহের কত সপ্তাহে সদকা লেখা আছে — সদকার ফিচার ব্যবহার না করলে null */
    val sadaqahWeeks: Int?,
    val imanLessons: Int,
    /** ১২ রাকাত সুন্নাতের শতাংশ (গত [days] দিন) — ট্র্যাকার ব্যবহার না করলে null */
    val sunnahPercent: Int? = null,
    /** গত ৪ সপ্তাহের আত্মজিজ্ঞাসার গড় — উত্তর না দিলে null */
    val akhlaqPercent: Int? = null,
    /** গত ৪ সপ্তাহে হালাকার শতাংশ — হালাকার দিন ঠিক না করলে null */
    val familyPercent: Int? = null,
)

data class WholenessResult(val areas: List<AreaScore>, val overall: Int, val weakest: AreaScore?)

object Wholeness {
    /** একটি মাসে এর বেশি নফল রোজা লাগবে না পূর্ণ নম্বরের জন্য (আইয়ামে বীয) */
    const val FAST_TARGET = 3
    const val SADAQAH_WEEKS = 4
    /** মোট নম্বরে নামাজের ভাগ — বাকিটা অন্য আমলগুলোর মধ্যে সমান ভাগ */
    const val SALAH_WEIGHT = 0.4

    private fun pct(n: Int, of: Int): Int = if (of <= 0) 0 else (n.coerceAtLeast(0) * 100 / of).coerceIn(0, 100)

    fun of(i: WholenessInput): WholenessResult {
        val d = i.days
        val list = buildList {
            add(AreaScore(AmalArea.SALAH, (i.salahOnTime.coerceIn(0.0, 1.0) * 100).roundToInt(), "গত ${Bn.num(d)} দিন"))
            i.sunnahPercent?.let { add(AreaScore(AmalArea.SUNNAH, it.coerceIn(0, 100), "${Bn.num(it.coerceIn(0, 100))}% রাকাত")) }
            i.quranDays?.let { add(AreaScore(AmalArea.QURAN, pct(it, d), "${Bn.num(it)}/${Bn.num(d)} দিন")) }
            add(AreaScore(AmalArea.AZKAR, pct(i.azkarDays, d), "${Bn.num(i.azkarDays)}/${Bn.num(d)} দিন"))
            add(AreaScore(AmalArea.MUHASABA, pct(i.muhasabaDays, d), "${Bn.num(i.muhasabaDays)}/${Bn.num(d)} দিন"))
            i.naflFasts?.let { add(AreaScore(AmalArea.FASTING, pct(it, FAST_TARGET), "${Bn.num(it)}টি রোজা")) }
            i.sadaqahWeeks?.let { add(AreaScore(AmalArea.SADAQAH, pct(it, SADAQAH_WEEKS), "${Bn.num(it)}/${Bn.num(SADAQAH_WEEKS)} সপ্তাহ")) }
            i.familyPercent?.let { add(AreaScore(AmalArea.FAMILY, it.coerceIn(0, 100), "${Bn.num(it.coerceIn(0, 100) / 25)}/৪ সপ্তাহ")) }
            i.akhlaqPercent?.let { add(AreaScore(AmalArea.AKHLAQ, it.coerceIn(0, 100), "আত্মজিজ্ঞাসা ${Bn.num(it.coerceIn(0, 100))}%")) }
            add(AreaScore(AmalArea.IMAN, pct(i.imanLessons, Iman.LESSONS.size), "${Bn.num(i.imanLessons)}/${Bn.num(Iman.LESSONS.size)} পাঠ"))
        }
        val salah = list.first()
        val others = list.drop(1)
        val overall = (salah.percent * SALAH_WEIGHT + others.map { it.percent }.average() * (1 - SALAH_WEIGHT)).roundToInt()
        // নামাজ অর্ধেকের নিচে হলে সেটাই আগে; নইলে অন্যগুলোর মধ্যে সবচেয়ে কম (সমান হলে তালিকার আগেরটা)
        val weakest = if (salah.percent < 50) salah else others.filter { it.percent < 100 }.minByOrNull { it.percent }
        return WholenessResult(list, overall, weakest)
    }

    /** "চমৎকার", "ভালো" … — এক শব্দে অবস্থা */
    fun label(overall: Int): String = when {
        overall >= 85 -> "চমৎকার"
        overall >= 65 -> "ভালো"
        overall >= 40 -> "চলছে"
        else -> "শুরু"
    }
}
