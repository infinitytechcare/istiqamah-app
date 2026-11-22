package com.istiqamah.app.sync

import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.JamaatMode
import com.istiqamah.app.core.JamaatRule
import com.istiqamah.app.core.JamaatSettings
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.SyncCrypto
import com.istiqamah.app.core.WorkSchedule
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// =====================================================================
// ফোন ↔ ম্যাক সিঙ্কের তার (ফোন ও ম্যাক দুই অ্যাপই এই একই ফাইল ব্যবহার করে)
//
// ফোন প্রশ্ন পাঠায়, ম্যাক উত্তর দেয় — একটা TCP সংযোগে একটা করে:
//   "IST1" + দৈর্ঘ্য(৪ বাইট) + AES-GCM(JSON)
// প্রশ্ন: ফোনের শেষ ৬০ দিনের নামাজের চিহ্ন + নামাজের সময়ের সেটিংস
// উত্তর: ম্যাকের শেষ ৬০ দিনের চিহ্ন
// এর বাইরে কিছু যায় না — গুনাহ, যাকাত, দেনা, ওসিয়ত, মুহাসাবা কখনো না।
// =====================================================================

/** ফোন থেকে ম্যাকে যাওয়া সেটিংস — নামাজের সময় আর ডাকের জন্য যা লাগে, এর বেশি নয় */
data class SyncedSettings(
    val city: String,
    val lat: Double,
    val lng: Double,
    val method: CalcMethod,
    val madhab: AsrMadhab,
    val adjust: List<Int>,
    val gender: Gender,
    val intensity: Intensity,
    val jamaat: JamaatSettings,
    val work: WorkSchedule,
    val pausedFrom: LocalDate?,
)

object SyncWire {
    const val PORT = 47630
    const val SERVICE = "_istiqamah._tcp"
    const val WINDOW_DAYS = 60L
    private const val MAGIC = 0x49535431 // "IST1"
    private const val MAX_FRAME = 1 shl 20
    /** দুই যন্ত্রের ঘড়ির পার্থক্য এর বেশি হলে প্রশ্ন বাতিল (পুরনো বার্তা আবার পাঠানো ঠেকাতে) */
    const val MAX_SKEW_MS = 15 * 60 * 1000L

    // ---------- ফ্রেম ----------
    fun write(out: DataOutputStream, key: ByteArray, json: JSONObject, aad: String) {
        val sealed = SyncCrypto.seal(key, json.toString().toByteArray(Charsets.UTF_8), aad)
        out.writeInt(MAGIC)
        out.writeInt(sealed.size)
        out.write(sealed)
        out.flush()
    }

    fun read(inp: DataInputStream, key: ByteArray, aad: String): JSONObject? {
        if (inp.readInt() != MAGIC) return null
        val n = inp.readInt()
        if (n <= 0 || n > MAX_FRAME) return null
        val buf = ByteArray(n)
        inp.readFully(buf)
        val plain = SyncCrypto.open(key, buf, aad) ?: return null
        return runCatching { JSONObject(String(plain, Charsets.UTF_8)) }.getOrNull()
    }

    /** ফোনের দিক: একটা প্রশ্ন পাঠিয়ে উত্তর নেওয়া; না পারলে null */
    fun exchange(host: String, port: Int, key: ByteArray, request: JSONObject, connectMs: Int = 1500, readMs: Int = 5000): JSONObject? =
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), connectMs)
                s.soTimeout = readMs
                write(DataOutputStream(s.getOutputStream().buffered()), key, request, "req")
                read(DataInputStream(s.getInputStream().buffered()), key, "res")
            }
        }.getOrNull()

    // ---------- ঘটনা ----------
    fun eventsJson(list: Iterable<LogEvent>): JSONArray = JSONArray().also { a ->
        list.forEach { e ->
            a.put(JSONObject().put("d", e.date.toString()).put("p", e.prayer.ordinal)
                .put("s", e.status?.code ?: 0).put("ts", e.ts).put("dev", e.device))
        }
    }

    fun parseEvents(a: JSONArray?): List<LogEvent> {
        if (a == null) return emptyList()
        return (0 until minOf(a.length(), 5000)).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                val p = o.getInt("p")
                val code = o.optInt("s", 0)
                val st = if (code == 0) null else PrayerStatus.fromCode(code) ?: return@runCatching null
                LogEvent(LocalDate.parse(o.getString("d")), Prayer.entries[p], st, o.getLong("ts"), o.optString("dev", "?").take(12))
            }.getOrNull()
        }
    }

    // ---------- সেটিংস ----------
    private fun t(x: LocalTime?): Any = x?.toString() ?: JSONObject.NULL
    private fun JSONObject.time(k: String): LocalTime? =
        if (!has(k) || isNull(k)) null else runCatching { LocalTime.parse(getString(k)) }.getOrNull()

    fun jamaatJson(j: JamaatSettings): JSONObject = JSONObject()
        .put("enabled", j.enabled)
        .put("officeDhuhr", t(j.officeDhuhr)).put("officeAsr", t(j.officeAsr))
        .put("leadMin", j.leadMin)
        .put("leads", JSONObject().also { l -> Prayer.entries.forEach { p -> l.put(p.name, j.lead(p)) } })
        .put("rules", JSONObject().also { r ->
            Prayer.entries.forEach { p ->
                val x = j.rule(p)
                r.put(p.name, JSONObject().put("mode", x.mode.code).put("min", x.minutes).put("fixed", t(x.fixed)))
            }
        })

    fun parseJamaat(j: JSONObject, def: JamaatSettings = JamaatSettings()): JamaatSettings {
        val rules = j.optJSONObject("rules")
        return JamaatSettings(
            enabled = j.optBoolean("enabled", def.enabled),
            rules = Prayer.entries.associateWith { p ->
                val r = rules?.optJSONObject(p.name)
                if (r == null) def.rule(p)
                else JamaatRule(JamaatMode.fromCode(r.optInt("mode")), r.optInt("min").coerceIn(0, 240), r.time("fixed"))
            },
            officeDhuhr = if (j.has("officeDhuhr")) j.time("officeDhuhr") else def.officeDhuhr,
            officeAsr = if (j.has("officeAsr")) j.time("officeAsr") else def.officeAsr,
            leadMin = j.optInt("leadMin", def.leadMin).coerceIn(0, 60),
            leads = j.optJSONObject("leads")?.let { l -> Prayer.entries.associateWith { p -> l.optInt(p.name, def.lead(p)).coerceIn(0, 60) } } ?: def.leads,
        )
    }

    fun workJson(w: WorkSchedule): JSONObject = JSONObject()
        .put("enabled", w.enabled)
        .put("days", JSONArray(w.days.map { it.value }.sorted()))
        .put("start", w.start.toString()).put("end", w.end.toString())

    fun parseWork(w: JSONObject, def: WorkSchedule = WorkSchedule()): WorkSchedule = WorkSchedule(
        enabled = w.optBoolean("enabled", def.enabled),
        days = w.optJSONArray("days")?.let { a -> (0 until a.length()).mapNotNull { i -> runCatching { DayOfWeek.of(a.getInt(i)) }.getOrNull() }.toSet() } ?: def.days,
        start = w.time("start") ?: def.start,
        end = w.time("end") ?: def.end,
    )

    fun settingsJson(s: SyncedSettings): JSONObject = JSONObject()
        .put("city", s.city).put("lat", s.lat).put("lng", s.lng)
        .put("method", s.method.name).put("madhab", s.madhab.name)
        .put("adjust", JSONArray(s.adjust))
        .put("gender", s.gender.code).put("intensity", s.intensity.code)
        .put("jamaat", jamaatJson(s.jamaat))
        .put("work", workJson(s.work))
        .put("pausedFrom", s.pausedFrom?.toString() ?: JSONObject.NULL)

    fun parseSettings(o: JSONObject?): SyncedSettings? = o?.let {
        runCatching {
            val lat = o.getDouble("lat").takeIf { it in -90.0..90.0 } ?: return null
            val lng = o.getDouble("lng").takeIf { it in -180.0..180.0 } ?: return null
            val adj = o.getJSONArray("adjust").let { a -> (0 until a.length()).map { a.getInt(it).coerceIn(-30, 30) } }
            SyncedSettings(
                city = o.optString("city", "").take(40),
                lat = lat, lng = lng,
                method = CalcMethod.valueOf(o.getString("method")),
                madhab = AsrMadhab.valueOf(o.getString("madhab")),
                adjust = if (adj.size == 5) adj else listOf(0, 0, 0, 0, 0),
                gender = Gender.entries.firstOrNull { it.code == o.optInt("gender") } ?: Gender.UNSET,
                intensity = Intensity.fromCode(o.optInt("intensity", Intensity.MEDIUM.code)),
                jamaat = parseJamaat(o.getJSONObject("jamaat")),
                work = parseWork(o.getJSONObject("work")),
                pausedFrom = if (o.isNull("pausedFrom")) null else runCatching { LocalDate.parse(o.getString("pausedFrom")) }.getOrNull(),
            )
        }.getOrNull()
    }

    // ---------- প্রশ্ন ও উত্তর ----------
    fun request(deviceId: String, name: String, events: List<LogEvent>, settings: SyncedSettings?, now: Long = System.currentTimeMillis()): JSONObject =
        JSONObject().put("v", 1).put("ts", now).put("dev", "phone").put("id", deviceId).put("name", name.take(40))
            .put("events", eventsJson(events))
            .put("settings", settings?.let { settingsJson(it) } ?: JSONObject.NULL)

    fun response(name: String, events: List<LogEvent>, now: Long = System.currentTimeMillis()): JSONObject =
        JSONObject().put("v", 1).put("ts", now).put("dev", "mac").put("name", name.take(40)).put("events", eventsJson(events))
}
