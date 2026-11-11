package com.istiqamah.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import java.time.LocalDate

/**
 * নামাজের লগ, কাযার হিসাব আর কোন কন্টেন্ট কবে দেখানো হয়েছে — সব SQLite-এ, শুধু ফোনে।
 * স্কিমা বদলালে DB_VERSION বাড়িয়ে onUpgrade-এ migration লিখতে হবে (ডেটা মুছবেন না)।
 */
class LogStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "istiqamah.db", null, DB_VERSION) {

    companion object {
        private const val DB_VERSION = 1

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 — এখনো কোনো migration নেই
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
}
