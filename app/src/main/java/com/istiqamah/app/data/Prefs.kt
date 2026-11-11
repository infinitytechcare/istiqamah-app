package com.istiqamah.app.data

import android.content.Context
import android.content.SharedPreferences
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerSettings
import com.istiqamah.app.core.Stage
import com.istiqamah.app.core.StageState
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

    var batteryGuideSeen: Boolean
        get() = sp.getBoolean("battery_seen", false)
        set(v) = sp.edit().putBoolean("battery_seen", v).apply()
}
