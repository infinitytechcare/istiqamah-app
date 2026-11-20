package com.istiqamah.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * v0.4 — "ঘরে-একা মোড"।
 *
 * অফিস/মসজিদ/মানুষের ভিড়ে নামাজ সহজ, কারণ চারপাশ মনে করিয়ে দেয়। ঘরে একা বা ছুটির দিনে সেই
 * বাইরের ইশারা থাকে না — তখনই অলসতা জেতে। তাই যে ওয়াক্তগুলো ঘরে পড়েন, সেগুলোতে অ্যাপ নিজেই
 * সেই "চারপাশের" কাজটা করে: ফুল-স্ক্রিন ডাক, ছোট প্রথম ধাপ (শুধু ওযু), নাছোড় রিমাইন্ডার।
 */

/** অফিস/কাজের রুটিন — এর বাইরের ওয়াক্ত = ঘরে */
data class WorkSchedule(
    val enabled: Boolean = true,
    /** বাংলাদেশে সাধারণ: রবি–বৃহস্পতি */
    val days: Set<DayOfWeek> = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
    val start: LocalTime = LocalTime.of(9, 0),
    val end: LocalTime = LocalTime.of(17, 0),
) {
    fun isWorkTime(at: ZonedDateTime): Boolean {
        if (!enabled || at.dayOfWeek !in days) return false
        val t = at.toLocalTime()
        // রাতের শিফট (শেষ < শুরু) — যেমন ২২:০০–০৬:০০
        return if (end > start) !t.isBefore(start) && t.isBefore(end) else !t.isBefore(start) || t.isBefore(end)
    }
}

/** কোনো একটা দিনের জন্য ইউজারের হাতে বদল: "আজ বাসায় আছি" / "আজ বাইরে" */
enum class DayOverride(val code: Int, val bn: String) {
    AUTO(0, "রুটিন অনুযায়ী"),
    HOME(1, "আজ সারাদিন ঘরে"),
    OUT(2, "আজ বাইরে/মসজিদের কাছে");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: AUTO
    }
}

/** ঘরে মোডে কী কী চালু */
data class HomeMode(
    val enabled: Boolean = true,
    val schedule: WorkSchedule = WorkSchedule(),
    val overrides: Map<LocalDate, DayOverride> = emptyMap(),
)

object Situation {
    /** নাছোড় রিমাইন্ডার: ক্ষতির নোটিফিকেশনের পর প্রতি ১৫ মিনিটে, সর্বোচ্চ ৩টি */
    const val NUDGE_EVERY_MIN = 15L
    const val MAX_NUDGES = 3
    /** "ওযু করতে যাচ্ছি" চাপলে কত মিনিট পরে খোঁজ নেবে */
    const val WUDU_CHECK_MIN = 12L

    /** এই ওয়াক্তটা কি ঘরে পড়ার কথা? */
    fun isHome(prayerStart: ZonedDateTime, mode: HomeMode): Boolean {
        if (!mode.enabled) return false
        return when (mode.overrides[prayerStart.toLocalDate()] ?: DayOverride.AUTO) {
            DayOverride.HOME -> true
            DayOverride.OUT -> false
            DayOverride.AUTO -> !mode.schedule.isWorkTime(prayerStart)
        }
    }

    /** আজ-কালের কোন (তারিখ, ওয়াক্ত) ঘরে মোডে — Planner-এ যায় */
    fun homePrayers(windowsByDate: Map<LocalDate, List<PrayerWindow>>, mode: HomeMode): Set<Pair<LocalDate, Prayer>> =
        windowsByDate.flatMap { (d, ws) -> ws.filter { isHome(it.start, mode) }.map { d to it.prayer } }.toSet()

    /** আজকের অবস্থা এক লাইনে — হোম স্ক্রিনের চিপে */
    fun todayLabel(date: LocalDate, mode: HomeMode): String {
        if (!mode.enabled) return "ঘরে মোড বন্ধ"
        return when (mode.overrides[date] ?: DayOverride.AUTO) {
            DayOverride.HOME -> "আজ ঘরে — সব ওয়াক্তে জোরালো ডাক"
            DayOverride.OUT -> "আজ বাইরে — স্বাভাবিক রিমাইন্ডার"
            DayOverride.AUTO ->
                if (!mode.schedule.enabled || date.dayOfWeek !in mode.schedule.days) "আজ ছুটির দিন — সব ওয়াক্তে জোরালো ডাক"
                else "অফিসের দিন — অফিসের বাইরের ওয়াক্তে জোরালো ডাক"
        }
    }

    /** ঘরে মোডের নোটিফিকেশনে ছোট্ট প্রথম ধাপ — একবারে পুরো নামাজ নয়, শুধু পরের এক পা */
    val MICRO_STEPS = listOf(
        "শুধু একটা কাজ: এখনই উঠে ওযুর জায়গা পর্যন্ত যান।",
        "জায়নামাজটা বিছিয়ে দিন — এটুকুই, বাকিটা সহজ হয়ে যাবে।",
        "ফোনটা রেখে দাঁড়ান। চার রাকাতে লাগে মাত্র ৫–৭ মিনিট।",
        "ওযুর পানি গায়ে লাগলেই অলসতার একটা গিঁট খুলে যায়।",
    )

    fun microStep(salt: Int): String = MICRO_STEPS[Math.floorMod(salt, MICRO_STEPS.size)]
}

/**
 * সাদাকার বাক্স — নিজের ওপর নিজে ঠিক করা নিয়ম: প্রতিটা ছুটে যাওয়া/কাযা ওয়াক্তে নির্দিষ্ট টাকা দান।
 * এটা কোনো শরয়ী কাফফারা নয়; কাযা আদায় ফরজই থাকে। ভিত্তি: "মন্দের পরপরই ভালো কাজ করো" (তিরমিজি ১৯৮৭)।
 */
object Sadaqah {
    data class Status(val misses: Int, val pledged: Int, val paid: Int) {
        val due: Int get() = (pledged - paid).coerceAtLeast(0)
    }

    /** from থেকে আজ পর্যন্ত কাযা/পড়িনি গুনে মোট নিয়ত করা টাকা */
    fun status(logs: Map<LocalDate, DayLog>, from: LocalDate, today: LocalDate, perMiss: Int, paid: Int): Status {
        val misses = logs.filterKeys { !it.isBefore(from) && !it.isAfter(today) }.values
            .sumOf { log -> log.statuses.values.count { it == PrayerStatus.MISSED || it == PrayerStatus.QAZA } }
        return Status(misses, misses * perMiss.coerceAtLeast(0), paid.coerceAtLeast(0))
    }
}

/** জবাবদিহি বন্ধুকে পাঠানোর দৈনিক হিসাব — শুধু নামাজ, কোনো গোপন তথ্য নয় */
object Report {
    fun daily(date: LocalDate, log: DayLog?, partnerName: String): String {
        val lines = Prayer.entries.map { p ->
            val s = log?.status(p)
            val mark = when {
                s == null -> "—"
                s == PrayerStatus.EXCUSED -> "বিরতি"
                s.onTime -> "✓ ${s.bn}"
                s.prayed -> "◐ ${s.bn}"
                else -> "✗"
            }
            "${p.bn}: $mark"
        }
        val counted = Prayer.entries.count { log?.status(it) != PrayerStatus.EXCUSED }
        val onTime = Prayer.entries.count { log?.status(it)?.onTime == true }
        val hi = partnerName.trim().ifEmpty { "ভাই" }
        return buildString {
            append("আসসালামু আলাইকুম $hi,\n")
            append("আজকের নামাজের হিসাব (${Bn.date(date)}):\n")
            lines.forEach { append(it).append('\n') }
            append("সময়মতো ${Bn.num(onTime)}/${Bn.num(counted)}\n")
            append(
                if (onTime == counted && counted > 0) "আলহামদুলিল্লাহ। দোয়া করবেন যেন অবিচল থাকি।"
                else "কাল আরও ভালো করার চেষ্টা করব — একটু খোঁজ নিয়েন, দোয়া করবেন।"
            )
        }
    }
}

/**
 * "নিশ্চয়ই নামাজ কঠিন — কিন্তু বিনয়ীদের জন্য নয়" (২:৪৫–৪৬)। কুরআন-হাদিস থেকে নামাজ সহজ করার চাবি।
 * প্রতিটা চাবি = শিরোনাম + বাস্তব কাজ + content.json-এর আইটেম আইডি (lint_content.py আইডিগুলো যাচাই করে)।
 */
data class EaseKey(val title: String, val practice: String, val contentIds: List<String>)

object EaseKeys {
    val ALL = listOf(
        EaseKey(
            "১. রবের সাথে সাক্ষাতের দৃঢ় বিশ্বাস (খুশু)",
            "২:৪৫–৪৬ নিজেই সমাধান বলে দেয়: নামাজ কঠিন, কিন্তু তাদের জন্য নয় যারা মনে রাখে — আমাকে রবের সামনে দাঁড়াতে হবে। প্রতিবার নামাজের আগে ৬০ সেকেন্ড শুধু এটুকু ভাবুন।",
            listOf("q_2_45_46", "q_23_1_2"),
        ),
        EaseKey(
            "২. কঠিন সময়ে নামাজ দিয়েই সাহায্য চাওয়া",
            "মন খারাপ, ক্লান্ত, চাপ? নামাজকে কাজের তালিকার আরেকটা বোঝা না ভেবে সমস্যার সমাধান ভাবুন — আগে দুই রাকাত, তারপর বাকিটা।",
            listOf("q_2_153", "q_70_22_23"),
        ),
        EaseKey(
            "৩. নামাজ বোঝা নয় — বিশ্রাম",
            "নবীজি ﷺ নামাজ থেকে মুক্তি চাইতেন না, নামাজ দিয়ে মুক্তি চাইতেন। \"পড়তে হবে\" নয়, \"পড়ে একটু শান্তি নিই\" — এভাবে বলুন নিজেকে।",
            listOf("h_abudawud_4985", "h_nasai_3939", "q_13_28"),
        ),
        EaseKey(
            "৪. বিদায়ী নামাজ",
            "ভাবুন, এটাই হয়তো জীবনের শেষ নামাজ। শেষ নামাজ কেউ অলসতায় ছাড়ে না, তাড়াহুড়োও করে না।",
            listOf("h_ibnmajah_4171"),
        ),
        EaseKey(
            "৫. ঘরে একা — কিন্তু তিনি দেখছেন",
            "অফিসে মানুষ দেখে বলে পড়ি, ঘরে কেউ দেখে না বলে ছাড়ি — অথচ আসল দর্শক সব জায়গায় একই। একা নামাজের জন্যই আলাদা পুরস্কারের হাদিস আছে।",
            listOf("q_26_218_219", "q_96_14", "h_bukhari_50", "h_abudawud_1203", "q_67_12"),
        ),
        EaseKey(
            "৬. অলসতা = শয়তানের গিঁট, খোলার উপায় তিন ধাপে",
            "যিকির → ওযু → নামাজ। পুরো নামাজের কথা ভাববেন না, শুধু প্রথম ধাপ: \"আল্লাহু আকবার\" বলে উঠে ওযুর দিকে যান। আর অলসতা থেকে আশ্রয়ের দোয়াটা মুখস্থ করুন।",
            listOf("h_bukhari_1142", "h_bukhari_6369", "q_4_142"),
        ),
        EaseKey(
            "৭. ঘরের মানুষকে সাথে নিন",
            "ঘরে যারা আছেন তাদের নিয়ে জামাত করুন, বা কাউকে বলুন \"আযান হলে আমাকে ডেকো\"। নবীজি ﷺ ঘরের কাজ করতেন, কিন্তু নামাজের সময় সব রেখে উঠে যেতেন।",
            listOf("q_20_132", "h_bukhari_676", "h_bukhari_645"),
        ),
        EaseKey(
            "৮. নিজের শক্তিতে নয় — আল্লাহর কাছে চেয়ে",
            "ইবরাহিম (আ.) নবী হয়েও দোয়া করেছেন: আমাকে নামাজ কায়েমকারী বানাও। প্রতিদিন এই দোয়া আর মুআযকে শেখানো দোয়াটা পড়ুন।",
            listOf("q_14_40", "h_abudawud_1522"),
        ),
    )
}

/** ঘরে নামাজের পরিবেশ — চেকলিস্ট (Prefs-এ টিক থাকে) */
object HomeSetup {
    val ITEMS = listOf(
        "জায়নামাজ সবসময় চোখে পড়ার জায়গায় বিছিয়ে রাখি",
        "ঘরে নামাজের একটা নির্দিষ্ট কোণা ঠিক করেছি",
        "ঘুমানোর আগে ওযু করে শুই (ফজরে ওঠা সহজ হয়)",
        "আযানের সময় হাতে ফোন/টিভি থাকলে — আগে নামাজ, পরে বাকিটা",
        "ঘরের কাউকে বলেছি: নামাজের সময় আমাকে ডেকো",
        "ছুটির দিনে অন্তত এক ওয়াক্ত মসজিদে যাই",
        "নামাজের রিমাইন্ডারের শব্দ হিসেবে আযান/পছন্দের শব্দ সেট করেছি",
    )
}
