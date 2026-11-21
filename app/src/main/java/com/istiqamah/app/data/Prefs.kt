package com.istiqamah.app.data

import android.content.Context
import android.content.SharedPreferences
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerSettings
import com.istiqamah.app.core.Stage
import com.istiqamah.app.core.StageState
import com.istiqamah.app.core.DayOverride
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Hijri
import com.istiqamah.app.core.HijriDay
import com.istiqamah.app.core.NaflFast
import com.istiqamah.app.core.QuranGoal
import com.istiqamah.app.core.QuranIndex
import com.istiqamah.app.core.QuranUnit
import com.istiqamah.app.core.JamaatMode
import com.istiqamah.app.core.JamaatRule
import com.istiqamah.app.core.JamaatSettings
import com.istiqamah.app.core.HomeMode
import com.istiqamah.app.core.WorkSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** সব সেটিং এক জায়গায়। শুধু ফোনে থাকে। */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("istiqamah", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    // ---------- লোকেশন ----------
    var placeName: String
        get() = sp.getString("place", "") ?: ""
        set(v) = sp.edit().putString("place", v).apply()

    var location: Location?
        get() {
            if (!sp.contains("lat")) return null
            val zone = runCatching { ZoneId.of(sp.getString("zone", null) ?: "") }.getOrElse { ZoneId.systemDefault() }
            return Location(sp.getFloat("lat", 0f).toDouble(), sp.getFloat("lng", 0f).toDouble(), zone)
        }
        set(v) {
            val e = sp.edit()
            if (v == null) e.remove("lat").remove("lng").remove("zone")
            else e.putFloat("lat", v.latitude.toFloat()).putFloat("lng", v.longitude.toFloat()).putString("zone", v.zone.id)
            e.apply()
        }

    // ---------- হিসাব ----------
    var method: CalcMethod
        get() = CalcMethod.entries.getOrElse(sp.getInt("method", 0)) { CalcMethod.KARACHI }
        set(v) = sp.edit().putInt("method", v.ordinal).apply()

    var madhab: AsrMadhab
        get() = AsrMadhab.entries.getOrElse(sp.getInt("madhab", 0)) { AsrMadhab.HANAFI }
        set(v) = sp.edit().putInt("madhab", v.ordinal).apply()

    fun adjust(p: Prayer): Int = sp.getInt("adj_${p.ordinal}", 0)
    fun setAdjust(p: Prayer, minutes: Int) = sp.edit().putInt("adj_${p.ordinal}", minutes.coerceIn(-30, 30)).apply()

    val prayerSettings: PrayerSettings
        get() = PrayerSettings(method, madhab, Prayer.entries.map { adjust(it) })

    var hijriAdjust: Int
        get() = sp.getInt("hijri_adj", -1) // বাংলাদেশে সাধারণত উম্মুল কুরা থেকে ১ দিন পিছিয়ে
        set(v) = sp.edit().putInt("hijri_adj", v.coerceIn(-2, 2)).apply()

    // ---------- নোটিফিকেশন ----------
    var intensity: Intensity
        get() = Intensity.fromCode(sp.getInt("intensity", Intensity.MEDIUM.code))
        set(v) = sp.edit().putInt("intensity", v.code).apply()

    /** null = দৈনিক আয়াতের নোটিফিকেশন বন্ধ */
    var dailyAyahAt: LocalTime?
        get() = if (sp.getBoolean("ayah_on", true)) LocalTime.of(sp.getInt("ayah_h", 9), sp.getInt("ayah_m", 0)) else null
        set(v) {
            if (v == null) sp.edit().putBoolean("ayah_on", false).apply()
            else sp.edit().putBoolean("ayah_on", true).putInt("ayah_h", v.hour).putInt("ayah_m", v.minute).apply()
        }

    /** বর্তমানে শিডিউল করা অ্যালার্মের requestCode — নতুন করে শিডিউলের আগে বাতিল করতে */
    var scheduledIds: Set<String>
        get() = sp.getStringSet("scheduled", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("scheduled", v).apply()

    // ---------- ধাপ ----------
    var stageState: StageState
        get() {
            val stage = Stage.fromNumber(sp.getInt("stage", 1))
            val focus = (sp.getString("focus", "") ?: "").split(',').mapNotNull { it.toIntOrNull()?.let(Prayer::fromOrdinal) }.toSet()
            val since = runCatching { LocalDate.parse(sp.getString("stage_since", "")) }.getOrElse { LocalDate.now() }
            return StageState(stage, focus.ifEmpty { setOf(Prayer.MAGHRIB) }, since)
        }
        set(v) = sp.edit()
            .putInt("stage", v.stage.number)
            .putString("focus", v.focus.map { it.ordinal }.sorted().joinToString(","))
            .putString("stage_since", v.since.toString())
            .apply()

    /** ধাপ ওঠার বার্তা — হোমে একবার দেখিয়ে মুছে ফেলা হয় */
    var pendingCelebration: String?
        get() = sp.getString("celebrate", null)
        set(v) = sp.edit().putString("celebrate", v).apply()

    var lastStageCheck: LocalDate?
        get() = runCatching { LocalDate.parse(sp.getString("stage_check", "")) }.getOrNull()
        set(v) = sp.edit().putString("stage_check", v?.toString()).apply()

    /** অনবোর্ডিং-এ বলা "দিনে গড়ে কয় ওয়াক্ত" — পরে তুলনার জন্য */
    var baselineDaily: Int
        get() = sp.getInt("baseline", 0)
        set(v) = sp.edit().putInt("baseline", v).apply()

    /** আজকের আয়াত — দিনে একবার বাছাই, হোম কার্ড ও নোটিফিকেশন একই আয়াত দেখায় */
    var dailyPick: Pair<LocalDate, String>?
        get() {
            val d = runCatching { LocalDate.parse(sp.getString("daily_date", "")) }.getOrNull() ?: return null
            val id = sp.getString("daily_id", null) ?: return null
            return d to id
        }
        set(v) = sp.edit().putString("daily_date", v?.first?.toString()).putString("daily_id", v?.second).apply()

    // ---------- v0.2: নামাজের বাইরের রিমাইন্ডার ----------
    var azkarReminders: Boolean
        get() = sp.getBoolean("azkar_on", true)
        set(v) = sp.edit().putBoolean("azkar_on", v).apply()

    /** null = মুহাসাবার রিমাইন্ডার বন্ধ */
    var muhasabaAt: LocalTime?
        get() = if (sp.getBoolean("muh_on", true)) LocalTime.of(sp.getInt("muh_h", 22), sp.getInt("muh_m", 0)) else null
        set(v) {
            if (v == null) sp.edit().putBoolean("muh_on", false).apply()
            else sp.edit().putBoolean("muh_on", true).putInt("muh_h", v.hour).putInt("muh_m", v.minute).apply()
        }

    var jumuahReminders: Boolean
        get() = sp.getBoolean("jumuah_on", true)
        set(v) = sp.edit().putBoolean("jumuah_on", v).apply()

    var weeklyReminders: Boolean
        get() = sp.getBoolean("weekly_on", true)
        set(v) = sp.edit().putBoolean("weekly_on", v).apply()

    val extras: com.istiqamah.app.core.ExtraReminders
        get() = com.istiqamah.app.core.ExtraReminders(
            dailyAyahAt, azkarReminders, muhasabaAt, jumuahReminders, weeklyReminders,
            routine = routine, qailulaBeforeDhuhr = qailulaBeforeDhuhr,
            dailyReport = dailyReport && partnerName.isNotBlank(),
            quran = quranOn, quranAt = quranAt,
            fasting = fastingOn, suhoorLeadMin = suhoorLead, nafl = naflFasts,
        )

    // ---------- v0.3: সুন্নত দিনলিপি ----------
    var routine: Set<NotifKind>
        get() {
            val raw = sp.getStringSet("routine", null) ?: return NotifKind.ROUTINE_DEFAULT
            return raw.mapNotNull { n -> NotifKind.entries.firstOrNull { it.name == n && it.isRoutine } }.toSet()
        }
        set(v) = sp.edit().putStringSet("routine", v.map { it.name }.toSet()).apply()

    var qailulaBeforeDhuhr: Boolean
        get() = sp.getBoolean("qailula_before", false)
        set(v) = sp.edit().putBoolean("qailula_before", v).apply()

    // ---------- v0.3: নোটিফিকেশনের শব্দ ----------
    var soundMode: SoundMode
        get() = SoundMode.entries.getOrElse(sp.getInt("sound_mode", 0)) { SoundMode.APP_CHIME }
        set(v) = sp.edit().putInt("sound_mode", v.ordinal).apply()

    /** নিজের বেছে নেওয়া রিংটোনের URI (SoundMode.CUSTOM) */
    var customSound: String?
        get() = sp.getString("custom_sound", null)
        set(v) = sp.edit().putString("custom_sound", v).apply()

    var customSoundName: String
        get() = sp.getString("custom_sound_name", "") ?: ""
        set(v) = sp.edit().putString("custom_sound_name", v).apply()

    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", true)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    /** আয়াত, আযকার, সুন্নত দিনলিপিতেও শব্দ (না হলে শুধু নামাজের রিমাইন্ডারে) */
    var soundForOthers: Boolean
        get() = sp.getBoolean("sound_others", true)
        set(v) = sp.edit().putBoolean("sound_others", v).apply()

    /**
     * Android 8+ এ চ্যানেল তৈরির পর তার শব্দ বদলানো যায় না — তাই শব্দ বদলালে নতুন সংস্করণের চ্যানেল বানাই।
     */
    var channelVersion: Int
        get() = sp.getInt("channel_ver", 1)
        set(v) = sp.edit().putInt("channel_ver", v).apply()

    /** গোপন অংশে (গুনাহ ট্র্যাকার, মুহাসাবার ইতিহাস) ঢুকতে ফোনের লক */
    var lockPrivate: Boolean
        get() = sp.getBoolean("lock_private", true)
        set(v) = sp.edit().putBoolean("lock_private", v).apply()

    /** এ সপ্তাহের লক্ষ্য — সাপ্তাহিক পর্যালোচনায় বাছাই করা, পরের শুক্রবার পর্যন্ত */
    var weeklyGoal: Pair<LocalDate, String>?
        get() {
            val d = runCatching { LocalDate.parse(sp.getString("wgoal_date", "")) }.getOrNull() ?: return null
            val g = sp.getString("wgoal", null) ?: return null
            return d to g
        }
        set(v) = sp.edit().putString("wgoal_date", v?.first?.toString()).putString("wgoal", v?.second).apply()

    // ---------- v0.7: রোজা ও রমজান ----------
    var fastingOn: Boolean
        get() = sp.getBoolean("fasting_on", true)
        set(v) = sp.edit().putBoolean("fasting_on", v).apply()

    /** ফজরের কত মিনিট আগে সাহরির ডাক */
    var suhoorLead: Int
        get() = sp.getInt("suhoor_lead", 45)
        set(v) = sp.edit().putInt("suhoor_lead", v.coerceIn(15, 120)).apply()

    var naflFasts: Set<NaflFast>
        get() = sp.getStringSet("nafl", null)?.mapNotNull { it.toIntOrNull()?.let(NaflFast::fromCode) }?.toSet() ?: NaflFast.entries.toSet()
        set(v) = sp.edit().putStringSet("nafl", v.map { it.code.toString() }.toSet()).apply()

    /** বাকি কাযা রোজা */
    var qazaFasts: Int
        get() = sp.getInt("qaza_fasts", 0)
        set(v) = sp.edit().putInt("qaza_fasts", v.coerceAtLeast(0)).apply()

    fun hijri(d: LocalDate): HijriDay? = Hijri.of(d, hijriAdjust)

    // ---------- v0.6: দৈনিক কুরআন ----------
    var quranOn: Boolean
        get() = sp.getBoolean("quran_on", true)
        set(v) = sp.edit().putBoolean("quran_on", v).apply()

    var quranGoal: QuranGoal
        get() = QuranGoal(QuranUnit.fromCode(sp.getInt("quran_unit", QuranUnit.PAGE.code)), sp.getInt("quran_amount", 1))
        set(v) = sp.edit().putInt("quran_unit", v.unit.code).putInt("quran_amount", v.amount).apply()

    /** null = ফজরের পরে (সূর্যোদয়ের ১৫ মিনিট আগে) */
    var quranAt: LocalTime?
        get() = sp.getInt("quran_at", -1).takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) }
        set(v) = sp.edit().putInt("quran_at", v?.let { it.hour * 60 + it.minute } ?: -1).apply()

    /** খতমের পরের আয়াত (১–৬২৩৬) */
    var quranBookmark: Int
        get() = sp.getInt("quran_bm", 1).coerceIn(1, QuranIndex.TOTAL)
        set(v) = sp.edit().putInt("quran_bm", v.coerceIn(1, QuranIndex.TOTAL)).apply()

    /** "আল-বাকারা ২:১৪২ · পৃষ্ঠা ২২" — নোটিফিকেশনে পুরো কুরআন লোড না করে দেখানোর জন্য */
    var quranBookmarkLabel: String
        get() = sp.getString("quran_bm_label", "আল-ফাতিহা ১:১ · পৃষ্ঠা ১") ?: ""
        set(v) = sp.edit().putString("quran_bm_label", v).apply()

    var quranKhatm: Int
        get() = sp.getInt("quran_khatm", 0)
        set(v) = sp.edit().putInt("quran_khatm", v).apply()

    /** কোন দিনগুলোতে লক্ষ্য পূরণ হয়েছে (শেষ ১০ দিন রাখি) — রিমাইন্ডার বন্ধ করার জন্য */
    var quranDoneDates: Set<LocalDate>
        get() = (sp.getStringSet("quran_done", emptySet()) ?: emptySet()).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("quran_done", v.sortedDescending().take(10).map { it.toString() }.toSet()).apply()

    var reciter: Int
        get() = sp.getInt("reciter", 0)
        set(v) = sp.edit().putInt("reciter", v).apply()

    var quranShowBn: Boolean
        get() = sp.getBoolean("quran_bn", true)
        set(v) = sp.edit().putBoolean("quran_bn", v).apply()

    /** মুখস্থের জন্য প্রতিটি আয়াত কতবার বাজবে */
    var quranRepeat: Int
        get() = sp.getInt("quran_repeat", 1)
        set(v) = sp.edit().putInt("quran_repeat", v.coerceIn(1, 20)).apply()

    // ---------- v0.5: পুরুষ/মহিলা, জামাত, বিরতি, নীরব রিমাইন্ডার ----------
    var gender: Gender
        get() = Gender.fromCode(sp.getInt("gender", 0))
        set(v) = sp.edit().putInt("gender", v.code).apply()

    var jamaatOn: Boolean
        get() = sp.getBoolean("jam_on", true)
        set(v) = sp.edit().putBoolean("jam_on", v).apply()

    fun jamaatRule(p: Prayer): JamaatRule {
        val def = JamaatSettings.DEFAULT_RULES.getValue(p)
        if (!sp.contains("jam_mode_${p.name}")) return def
        val fixed = sp.getInt("jam_fixed_${p.name}", -1).takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) }
        return JamaatRule(JamaatMode.fromCode(sp.getInt("jam_mode_${p.name}", 0)), sp.getInt("jam_min_${p.name}", 0), fixed)
    }

    fun setJamaatRule(p: Prayer, r: JamaatRule) = sp.edit()
        .putInt("jam_mode_${p.name}", r.mode.code)
        .putInt("jam_min_${p.name}", r.minutes)
        .putInt("jam_fixed_${p.name}", r.fixed?.let { it.hour * 60 + it.minute } ?: -1)
        .apply()

    /** অফিসের জামাত (null = নেই) */
    var officeDhuhr: LocalTime?
        get() = sp.getInt("office_dhuhr", 13 * 60 + 15).takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) }
        set(v) = sp.edit().putInt("office_dhuhr", v?.let { it.hour * 60 + it.minute } ?: -1).apply()

    var officeAsr: LocalTime?
        get() = sp.getInt("office_asr", -1).takeIf { it >= 0 }?.let { LocalTime.of(it / 60, it % 60) }
        set(v) = sp.edit().putInt("office_asr", v?.let { it.hour * 60 + it.minute } ?: -1).apply()

    /** জামাতের কত মিনিট আগে ডাক */
    var jamaatLead: Int
        get() = sp.getInt("jam_lead", 15)
        set(v) = sp.edit().putInt("jam_lead", v.coerceIn(0, 60)).apply()

    val jamaatSettings: JamaatSettings
        get() = JamaatSettings(jamaatOn, Prayer.entries.associateWith { jamaatRule(it) }, officeDhuhr, officeAsr, jamaatLead)

    /** (মহিলা) বিরতি শুরুর তারিখ; null = নেই */
    var pausedFrom: LocalDate?
        get() = if (gender == Gender.FEMALE) runCatching { LocalDate.parse(sp.getString("paused_from", "")) }.getOrNull() else null
        set(v) = sp.edit().putString("paused_from", v?.toString() ?: "").apply()

    /** চেক-ইন না করা পর্যন্ত প্রতি ১০ মিনিটে নীরব রিমাইন্ডার */
    var silentRepeat: Boolean
        get() = sp.getBoolean("silent_repeat", false)
        set(v) = sp.edit().putBoolean("silent_repeat", v).apply()

    // ---------- v0.4: ঘরে-একা মোড ----------
    var homeModeOn: Boolean
        get() = sp.getBoolean("home_on", true)
        set(v) = sp.edit().putBoolean("home_on", v).apply()

    var workEnabled: Boolean
        get() = sp.getBoolean("work_on", true)
        set(v) = sp.edit().putBoolean("work_on", v).apply()

    var workDays: Set<DayOfWeek>
        get() = sp.getStringSet("work_days", null)?.mapNotNull { n -> DayOfWeek.entries.firstOrNull { it.name == n } }?.toSet()
            ?: WorkSchedule().days
        set(v) = sp.edit().putStringSet("work_days", v.map { it.name }.toSet()).apply()

    var workStart: LocalTime
        get() = LocalTime.of(sp.getInt("work_sh", 9), sp.getInt("work_sm", 0))
        set(v) = sp.edit().putInt("work_sh", v.hour).putInt("work_sm", v.minute).apply()

    var workEnd: LocalTime
        get() = LocalTime.of(sp.getInt("work_eh", 17), sp.getInt("work_em", 0))
        set(v) = sp.edit().putInt("work_eh", v.hour).putInt("work_em", v.minute).apply()

    /** "আজ ঘরে/বাইরে" — শুধু সাম্প্রতিক তারিখগুলো রাখি ("2026-11-20:1") */
    fun dayOverride(d: LocalDate): DayOverride = overrides[d] ?: DayOverride.AUTO

    fun setDayOverride(d: LocalDate, o: DayOverride) {
        val keep = overrides.filterKeys { !it.isBefore(d.minusDays(3)) && it != d }.toMutableMap()
        if (o != DayOverride.AUTO) keep[d] = o
        sp.edit().putStringSet("day_override", keep.map { (k, v) -> "$k:${v.code}" }.toSet()).apply()
    }

    private val overrides: Map<LocalDate, DayOverride>
        get() = (sp.getStringSet("day_override", emptySet()) ?: emptySet()).mapNotNull { e ->
            val d = runCatching { LocalDate.parse(e.substringBefore(':')) }.getOrNull() ?: return@mapNotNull null
            d to DayOverride.fromCode(e.substringAfter(':').toIntOrNull() ?: 0)
        }.toMap()

    val homeMode: HomeMode
        get() = HomeMode(homeModeOn, WorkSchedule(workEnabled, workDays, workStart, workEnd), overrides)

    /** ঘরে-একা ওয়াক্তের শুরু ও শেষ সতর্কতায় অ্যালার্ম-ঘড়ির মতো পুরো পর্দার ডাক */
    var fullScreenCall: Boolean
        get() = sp.getBoolean("full_screen", true)
        set(v) = sp.edit().putBoolean("full_screen", v).apply()

    /** নিজের লেখা ওয়াদা ও "কেন" — ঘরে-একা নোটিফিকেশন আর ডাকের পর্দায় দেখায় */
    var pledge: String
        get() = sp.getString("pledge", "") ?: ""
        set(v) = sp.edit().putString("pledge", v.trim().take(200)).apply()

    var pledgeWhy: String
        get() = sp.getString("pledge_why", "") ?: ""
        set(v) = sp.edit().putString("pledge_why", v.trim().take(200)).apply()

    /** জবাবদিহি বন্ধু — শুধু নাম; পাঠানো হয় ইউজারের নিজের হাতে (শেয়ার) */
    var partnerName: String
        get() = sp.getString("partner", "") ?: ""
        set(v) = sp.edit().putString("partner", v.trim().take(40)).apply()

    var dailyReport: Boolean
        get() = sp.getBoolean("daily_report", true)
        set(v) = sp.edit().putBoolean("daily_report", v).apply()

    /** সাদাকার বাক্স: প্রতি কাযা/ছুটে যাওয়া ওয়াক্তে কত টাকা (০ = বন্ধ) */
    var sadaqahPerMiss: Int
        get() = sp.getInt("sadaqah_per", 0)
        set(v) = sp.edit().putInt("sadaqah_per", v.coerceIn(0, 100_000)).apply()

    var sadaqahFrom: LocalDate?
        get() = runCatching { LocalDate.parse(sp.getString("sadaqah_from", "")) }.getOrNull()
        set(v) = sp.edit().putString("sadaqah_from", v?.toString()).apply()

    var sadaqahPaid: Int
        get() = sp.getInt("sadaqah_paid", 0)
        set(v) = sp.edit().putInt("sadaqah_paid", v.coerceAtLeast(0)).apply()

    /** "ওযু করতে যাচ্ছি" চাপার পর এই সময় পর্যন্ত নাছোড় ডাক চুপ (একই দিনের একই ওয়াক্ত) */
    fun wuduUntil(d: LocalDate, p: Prayer): Long =
        if (sp.getString("wudu_key", "") == "$d:${p.ordinal}") sp.getLong("wudu_until", 0L) else 0L

    fun setWuduUntil(d: LocalDate, p: Prayer, millis: Long) =
        sp.edit().putString("wudu_key", "$d:${p.ordinal}").putLong("wudu_until", millis).apply()

    /** ঘরের পরিবেশের চেকলিস্টে কোনগুলোতে টিক */
    var homeSetupDone: Set<Int>
        get() = (sp.getStringSet("home_setup", emptySet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = sp.edit().putStringSet("home_setup", v.map { it.toString() }.toSet()).apply()

    var batteryGuideSeen: Boolean
        get() = sp.getBoolean("battery_seen", false)
        set(v) = sp.edit().putBoolean("battery_seen", v).apply()
}

/** নোটিফিকেশনের শব্দ কোথা থেকে */
enum class SoundMode(val bn: String) {
    APP_CHIME("অ্যাপের মৃদু \"টুম\" শব্দ"),
    PHONE_DEFAULT("ফোনের ডিফল্ট নোটিফিকেশন শব্দ"),
    CUSTOM("নিজের পছন্দের রিংটোন"),
    SILENT("নীরব (শুধু ভাইব্রেশন)"),
}
