package com.istiqamah.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * v0.5 — পুরুষ/মহিলা আলাদা পথ।
 * পুরুষ: মসজিদে জামাত — রিমাইন্ডার জামাতের সময় থেকে পিছিয়ে (ওযু করে রওনা দেওয়ার সময়)।
 * মহিলা: ঘরের ভেতরে আউয়াল ওয়াক্তে নামাজ উত্তম (আবু দাউদ ৫৭০, ৫৬৭; আহমাদ ২৭০৯০) — রিমাইন্ডার ওয়াক্ত শুরুতে;
 * হায়েজ/নিফাসের বিরতি মোড।
 */
enum class Gender(val code: Int, val bn: String) {
    UNSET(0, "বলা হয়নি"),
    MALE(1, "পুরুষ"),
    FEMALE(2, "মহিলা");

    /** কন্টেন্ট এই ইউজারকে দেখানো যায় কি না: "men" = জামাত/মসজিদ/জুমায় যাওয়া, "women" = মহিলাদের জন্য */
    fun allows(tags: List<String>): Boolean = when (this) {
        MALE -> "women" !in tags
        FEMALE -> "men" !in tags
        UNSET -> "women" !in tags
    }

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: UNSET
    }
}

/** মসজিদের জামাতের সময় কীভাবে ঠিক হয় */
enum class JamaatMode(val code: Int, val bn: String) {
    OFF(0, "জামাতের রিমাইন্ডার নেই"),
    FIXED(1, "নির্দিষ্ট সময়ে"),
    AFTER_START(2, "ওয়াক্ত শুরুর পরে"),
    BEFORE_END(3, "ওয়াক্ত শেষের আগে"); // ফজর: সূর্যোদয়ের X মিনিট আগে

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: OFF
    }
}

data class JamaatRule(val mode: JamaatMode, val minutes: Int = 0, val fixed: LocalTime? = null) {
    /** এই ওয়াক্তে জামাত কখন — ওয়াক্তের ভেতরে না পড়লে null */
    fun at(w: PrayerWindow): ZonedDateTime? {
        val t = when (mode) {
            JamaatMode.OFF -> return null
            JamaatMode.FIXED -> fixed?.let { w.start.toLocalDate().atTime(it).atZone(w.start.zone) } ?: return null
            JamaatMode.AFTER_START -> w.start.plusMinutes(minutes.toLong())
            JamaatMode.BEFORE_END -> w.end.minusMinutes(minutes.toLong())
        }
        return t.takeIf { !it.isBefore(w.start) && it.isBefore(w.end) }
    }

    fun describe(p: Prayer): String = when (mode) {
        JamaatMode.OFF -> "বন্ধ"
        JamaatMode.FIXED -> fixed?.let { Bn.time(it) } ?: "সময় দেওয়া হয়নি"
        JamaatMode.AFTER_START -> "আযানের ${Bn.num(minutes)} মিনিট পরে"
        JamaatMode.BEFORE_END -> if (p == Prayer.FAJR) "সূর্যোদয়ের ${Bn.num(minutes)} মিনিট আগে" else "ওয়াক্ত শেষের ${Bn.num(minutes)} মিনিট আগে"
    }
}

data class JamaatSettings(
    val enabled: Boolean = true,
    /** বাংলাদেশের সাধারণ মসজিদ: ফজর সূর্যোদয়ের ৪০ মিনিট আগে, যোহর ১:৩০, আসর আযানের ২০ মিনিট পর, মাগরিব ৫, ইশা ৩০ */
    val rules: Map<Prayer, JamaatRule> = DEFAULT_RULES,
    /** অফিসে আলাদা জামাত (অফিসের সময়ের ভেতরে পড়লে এটাই ধরা হয়) */
    val officeDhuhr: LocalTime? = LocalTime.of(13, 15),
    val officeAsr: LocalTime? = null,
    /** জামাতের কত মিনিট আগে ডাক — ওযু করে রওনা দেওয়ার সময় (পুরনো, সব ওয়াক্তে এক; [leads] না থাকলে) */
    val leadMin: Int = 15,
    /** v0.15: ওয়াক্ত ধরে আলাদা — আসরে অল্প, ফজরে/যোহরে বেশি */
    val leads: Map<Prayer, Int> = DEFAULT_LEADS,
) {
    fun rule(p: Prayer): JamaatRule = rules[p] ?: DEFAULT_RULES.getValue(p)
    fun lead(p: Prayer): Int = leads[p] ?: leadMin

    companion object {
        val DEFAULT_RULES = mapOf(
            Prayer.FAJR to JamaatRule(JamaatMode.BEFORE_END, 40),
            Prayer.DHUHR to JamaatRule(JamaatMode.FIXED, fixed = LocalTime.of(13, 30)),
            Prayer.ASR to JamaatRule(JamaatMode.AFTER_START, 20),
            Prayer.MAGHRIB to JamaatRule(JamaatMode.AFTER_START, 5),
            Prayer.ISHA to JamaatRule(JamaatMode.AFTER_START, 30),
        )

        /**
         * জামাতের কত আগে ডাক শুরু। আজকাল ওয়াক্ত শুরুর কিছু পরে আযান, তার ১০–১৫ মিনিট পরে জামাত —
         * তাই জামাতের সময় ধরেই হিসাব; প্রস্তুতির সময় ওয়াক্ত ভেদে আলাদা।
         */
        val DEFAULT_LEADS = mapOf(
            Prayer.FAJR to 20,
            Prayer.DHUHR to 15,
            Prayer.ASR to 5,
            Prayer.MAGHRIB to 10,
            Prayer.ISHA to 15,
        )
    }
}

/** কোন জামাত — মসজিদ না অফিস */
data class JamaatTime(val at: ZonedDateTime, val office: Boolean)

object Jamaat {
    /**
     * প্রতিটা (তারিখ, ওয়াক্ত)-এর জামাতের সময় — শুধু পুরুষের জন্য।
     * অফিসের জামাত অফিসের সময়ের ভেতরে পড়লে সেটা, নইলে মসজিদের নিয়ম।
     */
    fun times(
        windowsByDate: Map<LocalDate, List<PrayerWindow>>,
        gender: Gender,
        settings: JamaatSettings,
        schedule: WorkSchedule,
    ): Map<Pair<LocalDate, Prayer>, JamaatTime> {
        if (gender != Gender.MALE || !settings.enabled) return emptyMap()
        val out = HashMap<Pair<LocalDate, Prayer>, JamaatTime>()
        for ((d, ws) in windowsByDate) for (w in ws) {
            at(w, settings, schedule)?.let { out[d to w.prayer] = it }
        }
        return out
    }

    fun at(w: PrayerWindow, settings: JamaatSettings, schedule: WorkSchedule): JamaatTime? {
        val office = when (w.prayer) {
            Prayer.DHUHR -> settings.officeDhuhr
            Prayer.ASR -> settings.officeAsr
            else -> null
        }?.let { w.start.toLocalDate().atTime(it).atZone(w.start.zone) }
            ?.takeIf { schedule.isWorkTime(it) && !it.isBefore(w.start) && it.isBefore(w.end) }
        if (office != null) return JamaatTime(office, office = true)
        return settings.rule(w.prayer).at(w)?.let { JamaatTime(it, office = false) }
    }
}

/**
 * মহিলাদের হায়েজ/নিফাসের বিরতি। এই সময় নামাজ মাফ — রিমাইন্ডার বন্ধ, দিনগুলো "বিরতি" হিসেবে, স্ট্রিক ভাঙে না।
 * নোটিফিকেশনে কখনো কারণ লেখা হয় না, শুধু "বিরতি"।
 */
object Pause {
    /** কয় দিন পর থেকে দিনে একবার মৃদু প্রশ্ন: বিরতি শেষ? */
    const val CHECK_AFTER_DAYS = 3L

    fun isPaused(date: LocalDate, from: LocalDate?): Boolean = from != null && !date.isBefore(from)

    fun dayNumber(today: LocalDate, from: LocalDate): Long = today.toEpochDay() - from.toEpochDay() + 1
}
