package com.istiqamah.mac

import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.JamaatSettings
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.QuranScript
import com.istiqamah.app.core.SyncLog
import com.istiqamah.app.core.SyncPair
import com.istiqamah.app.sync.SyncWire
import com.istiqamah.app.core.WorkSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// =====================================================================
// ম্যাকের সংরক্ষণ: ~/Library/Application Support/Istiqamah
//   settings.json — শহর, হিসাব, জামাত, অফিস
//   events.json   — নামাজের চিহ্নগুলো (SyncLog-এর ঘটনা, ফোনের সাথে মেলানোর জন্য প্রস্তুত)
// এখানে কখনো গুনাহ, যাকাত, দেনা বা ওসিয়তের কিছু থাকে না।
// =====================================================================

/** এই ম্যাকের জোড়ার তথ্য; phoneId == null মানে এখনো কোনো ফোন জোড়া লাগেনি */
data class MacPair(
    val macId: String,
    val key: ByteArray,
    val phoneId: String? = null,
    val phoneName: String? = null,
    val lastSyncAt: Long = 0L,
) {
    val paired: Boolean get() = phoneId != null
    override fun equals(other: Any?) = other is MacPair && macId == other.macId && key.contentEquals(other.key) &&
        phoneId == other.phoneId && phoneName == other.phoneName && lastSyncAt == other.lastSyncAt
    override fun hashCode() = macId.hashCode()
}

class MacStore(private val dir: File = defaultDir()) {
    private val settingsFile = File(dir, "settings.json")
    private val eventsFile = File(dir, "events.json")

    init { dir.mkdirs() }

    fun loadSettings(): MacSettings = runCatching { parseSettings(JSONObject(settingsFile.readText())) }.getOrDefault(MacSettings())

    fun saveSettings(s: MacSettings) = atomicWrite(settingsFile, settingsJson(s).toString(2))

    fun loadEvents(): List<LogEvent> = runCatching {
        val a = JSONArray(eventsFile.readText())
        (0 until a.length()).mapNotNull { parseEvent(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun saveEvents(events: List<LogEvent>) {
        // ৪০০ দিনের বেশি পুরনো ঘটনা রাখি না; বাকিগুলোর প্রতিটি ওয়াক্তের শুধু শেষ ঘটনা
        val cutoff = LocalDate.now().minusDays(400)
        val keep = SyncLog.compact(events.filter { !it.date.isBefore(cutoff) })
        atomicWrite(eventsFile, JSONArray(keep.map { eventJson(it) }).toString())
    }

    // ---------- জোড়া (sync.json): এই ম্যাকের আইডি, গোপন চাবি, জোড়া লাগা ফোন ----------
    private val syncFile = File(dir, "sync.json")

    fun loadPair(): MacPair {
        val o = runCatching { JSONObject(syncFile.readText()) }.getOrNull()
        val id = o?.optString("id", "")?.takeIf { it.matches(Regex("[a-z0-9]{4,16}")) }
        val key = SyncPair.keyFromString(o?.optString("key", ""))
        if (id == null || key == null) return MacPair(SyncPair.newId(), SyncPair.newKey()).also { savePair(it) }
        return MacPair(
            id, key,
            phoneId = o.optString("phoneId", "").ifBlank { null },
            phoneName = o.optString("phoneName", "").ifBlank { null },
            lastSyncAt = o.optLong("lastSyncAt", 0L),
        )
    }

    fun savePair(p: MacPair) {
        atomicWrite(syncFile, JSONObject()
            .put("id", p.macId).put("key", SyncPair.keyToString(p.key))
            .put("phoneId", p.phoneId ?: "").put("phoneName", p.phoneName ?: "")
            .put("lastSyncAt", p.lastSyncAt).toString())
        // চাবিটা শুধু এই ইউজার পড়তে পারবে
        runCatching {
            syncFile.setReadable(false, false); syncFile.setReadable(true, true)
            syncFile.setWritable(false, false); syncFile.setWritable(true, true)
        }
    }

    private fun atomicWrite(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
    }

    companion object {
        const val DEVICE = "mac"

        fun defaultDir(): File {
            val home = System.getProperty("user.home")
            val mac = File(home, "Library/Application Support")
            return if (mac.isDirectory) File(mac, "Istiqamah") else File(home, ".istiqamah")
        }

        // ---------- ঘটনা ----------
        fun eventJson(e: LogEvent) = JSONObject()
            .put("d", e.date.toString()).put("p", e.prayer.ordinal)
            .put("s", e.status?.code ?: 0).put("ts", e.ts).put("dev", e.device)

        fun parseEvent(o: JSONObject): LogEvent? = runCatching {
            val p = o.getInt("p")
            if (p !in Prayer.entries.indices) return null
            LogEvent(
                date = LocalDate.parse(o.getString("d")),
                prayer = Prayer.fromOrdinal(p),
                status = PrayerStatus.fromCode(o.optInt("s", 0)),
                ts = o.getLong("ts"),
                device = o.optString("dev", DEVICE),
            )
        }.getOrNull()

        // ---------- সেটিংস ----------
        private fun t(x: LocalTime?) = x?.toString() ?: JSONObject.NULL

        fun settingsJson(s: MacSettings) = JSONObject()
            .put("v", 1)
            .put("city", s.city).put("lat", s.lat).put("lng", s.lng)
            .put("method", s.method.name).put("madhab", s.madhab.name)
            .put("adjust", JSONArray(s.adjust))
            .put("gender", s.gender.code)
            .put("intensity", s.intensity.code)
            .put("jamaat", SyncWire.jamaatJson(s.jamaat))
            .put("work", SyncWire.workJson(s.work))
            .put("callWindow", s.callWindow).put("fullScreenLast", s.fullScreenLast)
            .put("sound", s.sound).put("launchAtLogin", s.launchAtLogin)
            .put("pausedFrom", s.pausedFrom?.toString() ?: JSONObject.NULL)
            .put("standHourly", s.standHourly)

        private fun JSONObject.time(k: String, def: LocalTime?): LocalTime? =
            if (!has(k)) def else if (isNull(k)) null else runCatching { LocalTime.parse(getString(k)) }.getOrDefault(def)

        fun parseSettings(o: JSONObject): MacSettings {
            val d = MacSettings()
            val adj = o.optJSONArray("adjust")?.let { a -> (0 until a.length()).map { a.optInt(it).coerceIn(-30, 30) } }
                ?.takeIf { it.size == 5 } ?: d.adjust
            val j = o.optJSONObject("jamaat")
            val w = o.optJSONObject("work")
            return d.copy(
                city = o.optString("city", d.city),
                lat = o.optDouble("lat", d.lat).takeIf { it in -90.0..90.0 } ?: d.lat,
                lng = o.optDouble("lng", d.lng).takeIf { it in -180.0..180.0 } ?: d.lng,
                method = runCatching { CalcMethod.valueOf(o.getString("method")) }.getOrDefault(d.method),
                madhab = runCatching { AsrMadhab.valueOf(o.getString("madhab")) }.getOrDefault(d.madhab),
                adjust = adj,
                gender = Gender.entries.firstOrNull { it.code == o.optInt("gender", 0) } ?: Gender.UNSET,
                intensity = Intensity.fromCode(o.optInt("intensity", d.intensity.code)),
                jamaat = if (j == null) d.jamaat else SyncWire.parseJamaat(j, d.jamaat),
                work = if (w == null) d.work else SyncWire.parseWork(w, d.work),
                callWindow = o.optBoolean("callWindow", d.callWindow),
                fullScreenLast = o.optBoolean("fullScreenLast", d.fullScreenLast),
                sound = o.optBoolean("sound", d.sound),
                launchAtLogin = o.optBoolean("launchAtLogin", d.launchAtLogin),
                standHourly = o.optBoolean("standHourly", d.standHourly),
                pausedFrom = if (o.isNull("pausedFrom") || !o.has("pausedFrom")) null else runCatching { LocalDate.parse(o.getString("pausedFrom")) }.getOrNull(),
            )
        }

        // ---------- কন্টেন্ট (ফোনের content.json হুবহু) ----------
        fun parseContent(text: String): List<ContentItem> {
            val arr = JSONObject(text).getJSONArray("items")
            fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k).ifBlank { null }
            fun JSONObject.int(k: String): Int? = if (isNull(k) || !has(k)) null else getInt(k)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val tags = o.getJSONArray("tags").let { a -> (0 until a.length()).map { a.getString(it) } }
                val ref = o.optJSONObject("ref")
                ContentItem(
                    id = o.getString("id"), tags = tags, type = o.getString("type"), refBn = o.getString("ref_bn"),
                    surah = ref?.int("surah"), ayahFrom = ref?.int("ayah_from"), ayahTo = ref?.int("ayah_to"),
                    arabic = QuranScript.forFont(o.optString("arabic", "")),
                    bn = o.getString("bn"), bnSource = o.getString("bn_source"), shortBn = o.getString("short_bn"),
                    tone = o.getString("tone"), pairWith = o.str("pair_with"), grading = o.str("grading"),
                    reviewedBy = o.str("reviewed_by"), link = o.str("link"),
                )
            }
        }
    }
}
