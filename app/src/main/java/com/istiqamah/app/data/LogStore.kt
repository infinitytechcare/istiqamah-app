package com.istiqamah.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.SinEvent
import com.istiqamah.app.core.SinGoal
import com.istiqamah.app.core.TriggerTag
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * নামাজের লগ, কাযার হিসাব আর কোন কন্টেন্ট কবে দেখানো হয়েছে — সব SQLite-এ, শুধু ফোনে।
 * স্কিমা বদলালে DB_VERSION বাড়িয়ে onUpgrade-এ migration লিখতে হবে (ডেটা মুছবেন না)।
 */
class LogStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "istiqamah.db", null, DB_VERSION) {

    companion object {
        private const val DB_VERSION = 2

        @Volatile private var instance: LogStore? = null
        fun get(context: Context): LogStore =
            instance ?: synchronized(this) { instance ?: LogStore(context).also { instance = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE prayer_log(
                date TEXT NOT NULL,
                prayer INTEGER NOT NULL,
                status INTEGER NOT NULL,
                logged_at INTEGER NOT NULL,
                PRIMARY KEY(date, prayer))"""
        )
        db.execSQL("CREATE TABLE qaza_debt(prayer INTEGER PRIMARY KEY, remaining INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE content_seen(content_id TEXT PRIMARY KEY, shown_on TEXT NOT NULL)")
        createV2(db)
    }

    /** v0.2.0: আযকার, মুহাসাবা, গুনাহ ছাড়ার ট্র্যাকার। গোপন লেখা (title, note) [Vault] দিয়ে এনক্রিপ্ট করা। */
    private fun createV2(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS azkar_progress(date TEXT NOT NULL, item_id TEXT NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(date, item_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS azkar_done(date TEXT NOT NULL, set_code INTEGER NOT NULL, PRIMARY KEY(date, set_code))")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS muhasaba(
                date TEXT PRIMARY KEY,
                good TEXT NOT NULL,
                slip TEXT NOT NULL,
                tomorrow TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS sin_goal(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                started_on TEXT NOT NULL,
                active INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS sin_event(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                goal_id INTEGER NOT NULL,
                at TEXT NOT NULL,
                tags TEXT NOT NULL)"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // পুরনো ডেটা মুছবেন না — শুধু নতুন টেবিল যোগ
        if (oldVersion < 2) createV2(db)
    }

    // ---------- নামাজের লগ ----------

    fun setStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        val db = writableDatabase
        if (status == null) {
            db.delete("prayer_log", "date=? AND prayer=?", arrayOf(date.toString(), prayer.ordinal.toString()))
            return
        }
        val cv = ContentValues().apply {
            put("date", date.toString())
            put("prayer", prayer.ordinal)
            put("status", status.code)
            put("logged_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("prayer_log", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun day(date: LocalDate): DayLog = range(date, date)[date] ?: DayLog(date, emptyMap())

    /** from..to (দুই দিকসহ) সব দিনের লগ; যে দিনে কিছু নেই সেটা map-এ থাকে না */
    fun range(from: LocalDate, to: LocalDate): Map<LocalDate, DayLog> {
        val acc = HashMap<LocalDate, MutableMap<Prayer, PrayerStatus>>()
        readableDatabase.rawQuery(
            "SELECT date, prayer, status FROM prayer_log WHERE date BETWEEN ? AND ?",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val d = LocalDate.parse(c.getString(0))
                val p = Prayer.fromOrdinal(c.getInt(1))
                val s = PrayerStatus.fromCode(c.getInt(2)) ?: continue
                acc.getOrPut(d) { HashMap() }[p] = s
            }
        }
        return acc.mapValues { (d, m) -> DayLog(d, m) }
    }

    // ---------- কাযা ----------

    fun qaza(prayer: Prayer): Int =
        readableDatabase.rawQuery("SELECT remaining FROM qaza_debt WHERE prayer=?", arrayOf(prayer.ordinal.toString())).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun setQaza(prayer: Prayer, remaining: Int) {
        val cv = ContentValues().apply { put("prayer", prayer.ordinal); put("remaining", remaining.coerceAtLeast(0)) }
        writableDatabase.insertWithOnConflict("qaza_debt", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- কন্টেন্ট কবে দেখানো হয়েছে ----------

    fun seenMap(): Map<String, LocalDate> {
        val m = HashMap<String, LocalDate>()
        readableDatabase.rawQuery("SELECT content_id, shown_on FROM content_seen", null).use { c ->
            while (c.moveToNext()) runCatching { m[c.getString(0)] = LocalDate.parse(c.getString(1)) }
        }
        return m
    }

    fun markSeen(id: String, on: LocalDate) {
        val cv = ContentValues().apply { put("content_id", id); put("shown_on", on.toString()) }
        writableDatabase.insertWithOnConflict("content_seen", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ---------- আযকার ----------

    fun azkarCounts(date: LocalDate): Map<String, Int> {
        val m = HashMap<String, Int>()
        readableDatabase.rawQuery("SELECT item_id, count FROM azkar_progress WHERE date=?", arrayOf(date.toString())).use { c ->
            while (c.moveToNext()) m[c.getString(0)] = c.getInt(1)
        }
        return m
    }

    fun setAzkarCount(date: LocalDate, itemId: String, count: Int) {
        val cv = ContentValues().apply { put("date", date.toString()); put("item_id", itemId); put("count", count) }
        writableDatabase.insertWithOnConflict("azkar_progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun markAzkarDone(date: LocalDate, set: AzkarSet) {
        val cv = ContentValues().apply { put("date", date.toString()); put("set_code", set.code) }
        writableDatabase.insertWithOnConflict("azkar_done", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun azkarDone(from: LocalDate, to: LocalDate): Set<Pair<LocalDate, AzkarSet>> {
        val out = HashSet<Pair<LocalDate, AzkarSet>>()
        readableDatabase.rawQuery("SELECT date, set_code FROM azkar_done WHERE date BETWEEN ? AND ?", arrayOf(from.toString(), to.toString())).use { c ->
            while (c.moveToNext()) {
                val s = AzkarSet.fromCode(c.getInt(1)) ?: continue
                out += LocalDate.parse(c.getString(0)) to s
            }
        }
        return out
    }

    // ---------- মুহাসাবা ----------

    data class MuhasabaEntry(val date: LocalDate, val good: String, val slip: String, val tomorrow: String)

    fun saveMuhasaba(e: MuhasabaEntry) {
        val cv = ContentValues().apply {
            put("date", e.date.toString())
            put("good", Vault.encrypt(e.good))
            put("slip", Vault.encrypt(e.slip))
            put("tomorrow", Vault.encrypt(e.tomorrow))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("muhasaba", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun muhasaba(date: LocalDate): MuhasabaEntry? =
        readableDatabase.rawQuery("SELECT good, slip, tomorrow FROM muhasaba WHERE date=?", arrayOf(date.toString())).use { c ->
            if (c.moveToFirst()) MuhasabaEntry(date, Vault.decrypt(c.getString(0)), Vault.decrypt(c.getString(1)), Vault.decrypt(c.getString(2))) else null
        }

    /** কোন কোন দিন মুহাসাবা হয়েছে (লেখা না পড়ে — শুধু তারিখ) */
    fun muhasabaDates(from: LocalDate, to: LocalDate): Set<LocalDate> {
        val out = HashSet<LocalDate>()
        readableDatabase.rawQuery("SELECT date FROM muhasaba WHERE date BETWEEN ? AND ?", arrayOf(from.toString(), to.toString())).use { c ->
            while (c.moveToNext()) out += LocalDate.parse(c.getString(0))
        }
        return out
    }

    // ---------- গুনাহ ছাড়ার ট্র্যাকার ----------

    fun sinGoals(includeArchived: Boolean = false): List<SinGoal> {
        val out = ArrayList<SinGoal>()
        val where = if (includeArchived) "" else " WHERE active=1"
        readableDatabase.rawQuery("SELECT id, title, started_on, active FROM sin_goal$where ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                out += SinGoal(c.getLong(0), Vault.decrypt(c.getString(1)), LocalDate.parse(c.getString(2)), c.getInt(3) == 1)
            }
        }
        return out
    }

    fun addSinGoal(title: String, startedOn: LocalDate): Long {
        val cv = ContentValues().apply {
            put("title", Vault.encrypt(title.trim()))
            put("started_on", startedOn.toString())
            put("active", 1)
        }
        return writableDatabase.insert("sin_goal", null, cv)
    }

    fun archiveSinGoal(id: Long) {
        val cv = ContentValues().apply { put("active", 0) }
        writableDatabase.update("sin_goal", cv, "id=?", arrayOf(id.toString()))
    }

    fun addSinEvent(goalId: Long, at: LocalDateTime, tags: Set<TriggerTag>) {
        val cv = ContentValues().apply {
            put("goal_id", goalId)
            put("at", at.toString())
            put("tags", tags.joinToString(",") { it.code.toString() })
        }
        writableDatabase.insert("sin_event", null, cv)
    }

    fun sinEvents(): List<SinEvent> {
        val out = ArrayList<SinEvent>()
        readableDatabase.rawQuery("SELECT goal_id, at, tags FROM sin_event ORDER BY at", null).use { c ->
            while (c.moveToNext()) {
                val tags = c.getString(2).split(',').mapNotNull { it.toIntOrNull()?.let(TriggerTag::fromCode) }.toSet()
                runCatching { out += SinEvent(c.getLong(0), LocalDateTime.parse(c.getString(1)), tags) }
            }
        }
        return out
    }
}
