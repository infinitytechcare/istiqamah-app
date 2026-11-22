package com.istiqamah.mac

import com.istiqamah.app.core.Amal
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.ContentPicker
import com.istiqamah.app.core.ExtraReminders
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Jamaat
import com.istiqamah.app.core.JamaatSettings
import com.istiqamah.app.core.JamaatTime
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Planner
import com.istiqamah.app.core.PlannerInput
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerSettings
import com.istiqamah.app.core.PrayerTimesCalculator
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.core.WorkSchedule
import com.istiqamah.app.data.Cities
import com.istiqamah.app.data.City
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// =====================================================================
// ম্যাকের ইস্তিকামাহ — হিসাবের অংশ (ফোনের core হুবহু ব্যবহার করে)
// কোনো UI বা ফাইল নেই, তাই আলাদা করে টেস্ট করা যায়।
// =====================================================================

data class MacSettings(
    val city: String = "ঢাকা",
    val lat: Double = 23.8103,
    val lng: Double = 90.4125,
    val method: CalcMethod = CalcMethod.KARACHI,
    val madhab: AsrMadhab = AsrMadhab.HANAFI,
    val adjust: List<Int> = listOf(0, 0, 0, 0, 0),
    val gender: Gender = Gender.UNSET,
    val jamaat: JamaatSettings = JamaatSettings(),
    val work: WorkSchedule = WorkSchedule(),
    val intensity: Intensity = Intensity.MEDIUM,
    /** ওয়াক্ত/জামাতের ডাকে কোণে ছোট জানালা */
    val callWindow: Boolean = true,
    /** শেষ সতর্কতায় পুরো স্ক্রিন */
    val fullScreenLast: Boolean = true,
    val sound: Boolean = true,
    val launchAtLogin: Boolean = true,
    /** (মহিলা) বিরতি শুরুর তারিখ; null = বিরতি নেই — ফোনের মতোই গোপন, কোথাও কারণ লেখা হয় না */
    val pausedFrom: LocalDate? = null,
)

/** ম্যাকে ডাক কীভাবে দেখাবে */
enum class MacAlert { BANNER, CALL, FULLSCREEN }

data class MacReminder(val notif: PlannedNotif, val alert: MacAlert, val title: String, val body: String)

object MacEngine {
    /** ফোনের অ্যাপের ৬৪ জেলা — একই তালিকা */
    val CITIES: List<City> get() = Cities.all

    /** ঘুম থেকে জাগার পর এর চেয়ে পুরনো ডাক আর দেখাই না */
    const val STALE_MIN = 15L
    /** "১০ মিনিট পরে" */
    const val SNOOZE_MIN = 10L

    fun location(s: MacSettings, zone: ZoneId = ZoneId.systemDefault()) = Location(s.lat, s.lng, zone)

    /** গতকাল, আজ ও আগামীকালের ওয়াক্ত-সীমা (মাঝরাতের পরেও গতকালের ইশা চলে) */
    fun windowsByDate(s: MacSettings, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<PrayerWindow>> {
        val loc = location(s, zone)
        val ps = PrayerSettings(s.method, s.madhab, s.adjust)
        val days = (-1..2).map { today.plusDays(it.toLong()) }.associateWith { PrayerTimesCalculator.compute(it, loc, ps) }
        return (-1..1).map { today.plusDays(it.toLong()) }.associateWith { d ->
            PrayerTimesCalculator.windows(days.getValue(d), days.getValue(d.plusDays(1)))
        }
    }

    fun jamaat(s: MacSettings, windows: Map<LocalDate, List<PrayerWindow>>): Map<Pair<LocalDate, Prayer>, JamaatTime> =
        Jamaat.times(windows, s.gender, s.jamaat, s.work)

    /** ফোনের একই পরিকল্পনা — শুধু নামাজের ডাক (ম্যাকে অন্য রিমাইন্ডার নয়) */
    fun plan(
        s: MacSettings, now: ZonedDateTime, logged: Set<Pair<LocalDate, Prayer>>,
        going: Map<Pair<LocalDate, Prayer>, ZonedDateTime> = emptyMap(),
    ): List<PlannedNotif> {
        val ws = windowsByDate(s, now.toLocalDate(), now.zone)
        val input = PlannerInput(
            now = now, windowsByDate = ws, intensity = s.intensity,
            focus = Prayer.entries.toSet(), habitFormed = emptySet(), logged = logged,
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false),
            jamaat = jamaat(s, ws), leadMin = s.jamaat.leadMin.toLong(),
            leads = Prayer.entries.associateWith { s.jamaat.lead(it).toLong() },
            goingUntil = going,
            pausedFrom = s.pausedFrom.takeIf { s.gender == Gender.FEMALE },
        )
        return Planner.plan(input).filter { it.kind.isPrayer && it.prayer != null && it.kind != NotifKind.SILENT_REPEAT }
    }

    /**
     * (from, to] সময়ের মধ্যে যে ডাকগুলো বাজার কথা — ঘুম থেকে জাগলে পুরনো ডাকের ঢল নয়:
     * [STALE_MIN]-এর বেশি পুরনো বাদ, আর একই ওয়াক্তের একাধিক থাকলে শুধু শেষেরটা।
     */
    fun due(
        s: MacSettings, from: ZonedDateTime, to: ZonedDateTime, logged: Set<Pair<LocalDate, Prayer>>,
        going: Map<Pair<LocalDate, Prayer>, ZonedDateTime> = emptyMap(),
    ): List<PlannedNotif> {
        val fresh = plan(s, from, logged, going).filter { !it.fireAt.isAfter(to) && it.fireAt.isAfter(to.minusMinutes(STALE_MIN)) }
        return fresh.groupBy { it.date to it.prayer }.values.map { l -> l.maxBy { it.fireAt } }.sortedBy { it.fireAt }
    }

    /** "এখনই যাচ্ছি" চাপার পর কতক্ষণ চুপ, তারপর একবার খোঁজ */
    const val GOING_MIN = 12L

    fun alertFor(s: MacSettings, kind: NotifKind): MacAlert = when {
        // কড়া মাত্রা: নামাজের সব ডাক পুরো পর্দায়
        s.intensity == Intensity.STRONG && kind.isPrayer -> MacAlert.FULLSCREEN
        else -> alertForKind(s, kind)
    }

    private fun alertForKind(s: MacSettings, kind: NotifKind): MacAlert = when (kind) {
        NotifKind.N3_LAST_CALL -> if (s.fullScreenLast) MacAlert.FULLSCREEN else if (s.callWindow) MacAlert.CALL else MacAlert.BANNER
        NotifKind.N1_INVITE, NotifKind.HOME_NUDGE_3 -> if (s.callWindow) MacAlert.CALL else MacAlert.BANNER
        else -> MacAlert.BANNER
    }

    fun reminder(s: MacSettings, n: PlannedNotif, windows: Map<LocalDate, List<PrayerWindow>>, jam: Map<Pair<LocalDate, Prayer>, JamaatTime>): MacReminder {
        val p = n.prayer!!
        val w = windows[n.date]?.firstOrNull { it.prayer == p }
        val j = jam[n.date to p]
        val end = w?.end?.let { "ওয়াক্ত শেষ ${Bn.time(it.toLocalTime())}" } ?: ""
        val (title, body) = when (n.kind) {
            NotifKind.N1_INVITE ->
                if (j != null) "${p.bn}ের জামাত ${Bn.time(j.at.toLocalTime())}" + (if (j.office) " (অফিসে)" else "") to "ওযু করে রওনা দেওয়ার সময়। $end"
                else "${p.bn}ের সময় হয়েছে" to "আল্লাহ ডাকছেন — কাজ একটু রেখে ওযু করে নিন। $end"
            NotifKind.N1_LOSS -> "${p.bn} এখনো পড়া হয়নি" to "দুনিয়ার কাজ অপেক্ষা করতে পারে, নামাজ নয়। $end"
            NotifKind.N2_VIRTUE -> "${p.bn} — এখনো সময় আছে" to "আউয়াল ওয়াক্তে পড়ার সুযোগটা হাতছাড়া করবেন না। $end"
            NotifKind.JAMAAT_MISSED -> "${p.bn}ের জামাত শুরু হয়ে গেছে" to "জামাত ছুটলেও নামাজ ছুটতে দেবেন না — এখনই পড়ে নিন। $end"
            NotifKind.N3_LAST_CALL -> "${p.bn}ের ওয়াক্ত শেষ হতে ২০ মিনিট" to "এখনই না পড়লে কাযা হয়ে যাবে। দরজা এখনো খোলা — উঠে পড়ুন।"
            NotifKind.STRONG_CALL ->
                if (j != null && n.fireAt.isBefore(j.at)) "${p.bn}ের জামাত ${Bn.time(j.at.toLocalTime())}" to "কাজ রাখুন, ওযু করে রওনা দিন। $end"
                else "${p.bn} এখনো পড়া হয়নি" to "কাজ একটু থামান — আগে আল্লাহর ডাক। $end"
            NotifKind.WUDU_CHECK -> "ওযু হয়েছে? এবার জায়নামাজে দাঁড়ান" to "\"আল্লাহু আকবার\" বলে শুরু করুন — বাকিটা আপনা-আপনি হবে। $end"
            else -> "${p.bn} — মনে করিয়ে দিচ্ছি" to "নামাজের সময় চলছে। $end"
        }
        return MacReminder(n, alertFor(s, n.kind), title, body.trim())
    }

    /** ডাকের সাথে একটা আয়াত/হাদিস — ফোনের একই কন্টেন্ট ও একই বাছাই নিয়ম */
    fun pickContent(
        items: List<ContentItem>, kind: NotifKind, prayer: Prayer, gender: Gender,
        seen: Map<String, LocalDate>, today: LocalDate, salt: Int = 0,
    ): ContentItem? {
        val gain = kind == NotifKind.N1_INVITE || kind == NotifKind.N2_VIRTUE || kind == NotifKind.HOME_NUDGE_1
        val pool = items.filter { gender.allows(it.tags) }
        val tags = ContentPicker.tagsFor(Amal.SALAH, prayer, gain)
        return ContentPicker.pickMajorFirst(pool, tags, seen, today, salt) ?: ContentPicker.pickAny(pool, tags, seen, today, salt)
    }

    /** "২৩ মিনিট", "১ ঘণ্টা ৫ মিনিট" */
    fun countdown(d: Duration): String {
        val m = d.toMinutes().coerceAtLeast(0)
        return when {
            m < 1 -> "এখনই"
            m < 60 -> "${Bn.num(m)} মিনিট"
            else -> "${Bn.num(m / 60)} ঘণ্টা" + if (m % 60 > 0) " ${Bn.num(m % 60)} মিনিট" else ""
        }
    }

    /** মেনু বারের সংক্ষেপ: "আসর চলছে · শেষ ৫:১২" বা "পরের: মাগরিব ৫:১৪ (২৩ মিনিট)" */
    fun summary(windows: Map<LocalDate, List<PrayerWindow>>, now: ZonedDateTime, logged: Set<Pair<LocalDate, Prayer>>): String {
        val all = windows.toSortedMap().values.flatten()
        val cur = all.firstOrNull { it.contains(now) }
        if (cur != null && (cur.start.toLocalDate() to cur.prayer) !in logged) {
            return "${cur.prayer.bn} চলছে · বাকি ${countdown(Duration.between(now, cur.end))}"
        }
        val next = all.firstOrNull { it.start.isAfter(now) } ?: return ""
        return "পরের: ${next.prayer.bn} ${Bn.time(next.start.toLocalTime())} (${countdown(Duration.between(now, next.start))})"
    }

    /** এখন চলমান ওয়াক্ত (চিহ্ন দেওয়া হোক বা না হোক) */
    fun current(windows: Map<LocalDate, List<PrayerWindow>>, now: ZonedDateTime): PrayerWindow? =
        windows.toSortedMap().values.flatten().firstOrNull { it.contains(now) }
}
