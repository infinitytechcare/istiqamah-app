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
import com.istiqamah.app.core.ReadEvent
import com.istiqamah.app.core.FastStatus
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
        private const val DB_VERSION = 6

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
        createV3(db)
        createV4(db)
        createV5(db)
        createV6(db)
    }

    /** v6 (০.১৪): ম্যাকের সাথে সিঙ্ক — কোন যন্ত্রে চিহ্ন দেওয়া, আর মুছে ফেলার স্মৃতি */
    private fun createV6(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE prayer_log ADD COLUMN src TEXT NOT NULL DEFAULT 'phone'")
        db.execSQL("CREATE TABLE IF NOT EXISTS prayer_tomb(date TEXT NOT NULL, prayer INTEGER NOT NULL, deleted_at INTEGER NOT NULL, src TEXT NOT NULL, PRIMARY KEY(date, prayer))")
    }

    /** v0.9.5: সুন্নাত/নফল নামাজ (১২ রাকাত, বিতর, তাহাজ্জুদ, দোহা) আর নামাজের পর খুশুর মাপ */
    private fun createV5(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sunnah_log(date TEXT NOT NULL, slot INTEGER NOT NULL, PRIMARY KEY(date, slot))")
        db.execSQL("CREATE TABLE IF NOT EXISTS khushu_log(date TEXT NOT NULL, prayer INTEGER NOT NULL, score INTEGER NOT NULL, PRIMARY KEY(date, prayer))")
    }

    /** v0.7: রোজার লগ — প্রতিদিন একটা অবস্থা */
    private fun createV4(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS fast_log(date TEXT PRIMARY KEY, status INTEGER NOT NULL)")
    }

    /** v0.6: কুরআন পড়ার লগ — প্রতি বসায় কোন আয়াত থেকে কোন আয়াতের আগ পর্যন্ত */
    private fun createV3(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS quran_read(id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, from_id INTEGER NOT NULL, to_id INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS quran_read_date ON quran_read(date)")
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
        if (oldVersion < 3) createV3(db)
        if (oldVersion < 4) createV4(db)
        if (oldVersion < 5) createV5(db)
        if (oldVersion < 6) createV6(db)
    }

    // ---------- নামাজের লগ ----------

    /**
     * চিহ্ন দেওয়া বা মোছা। [ts]/[src] শুধু ম্যাক থেকে আসা চিহ্ন বসানোর সময় আলাদা হয় —
     * নিজের চিহ্ন হলে এখনকার সময় আর "phone"।
     */
    fun setStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?, ts: Long = System.currentTimeMillis(), src: String = "phone") {
        val db = writableDatabase
        val args = arrayOf(date.toString(), prayer.ordinal.toString())
        db.beginTransaction()
        try {
            if (status == null) {
                db.delete("prayer_log", "date=? AND prayer=?", args)
                db.insertWithOnConflict("prayer_tomb", null, ContentValues().apply {
                    put("date", date.toString()); put("prayer", prayer.ordinal); put("deleted_at", ts); put("src", src)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            } else {
                db.delete("prayer_tomb", "date=? AND prayer=?", args)
                db.insertWithOnConflict("prayer_log", null, ContentValues().apply {
                    put("date", date.toString()); put("prayer", prayer.ordinal); put("status", status.code)
                    put("logged_at", ts); put("src", src)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** সিঙ্কের জন্য [from] থেকে সব চিহ্ন ও মুছে ফেলা — ঘটনা হিসেবে (শুধু নামাজ, আর কিছু নয়) */
    fun syncEvents(from: LocalDate): List<com.istiqamah.app.core.LogEvent> {
        val out = ArrayList<com.istiqamah.app.core.LogEvent>()
        readableDatabase.rawQuery("SELECT date, prayer, status, logged_at, src FROM prayer_log WHERE date >= ?", arrayOf(from.toString())).use { c ->
            while (c.moveToNext()) {
                val st = PrayerStatus.fromCode(c.getInt(2)) ?: continue
                out += com.istiqamah.app.core.LogEvent(LocalDate.parse(c.getString(0)), Prayer.fromOrdinal(c.getInt(1)), st, c.getLong(3), c.getString(4))
            }
        }
        readableDatabase.rawQuery("SELECT date, prayer, deleted_at, src FROM prayer_tomb WHERE date >= ?", arrayOf(from.toString())).use { c ->
            while (c.moveToNext()) {
                out += com.istiqamah.app.core.LogEvent(LocalDate.parse(c.getString(0)), Prayer.fromOrdinal(c.getInt(1)), null, c.getLong(2), c.getString(3))
            }
        }
        return out
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

    // ---------- দৈনিক কুরআন ----------

    fun addRead(date: LocalDate, from: Int, to: Int) {
        if (to <= from) return
        writableDatabase.insert("quran_read", null, ContentValues().apply {
            put("date", date.toString()); put("from_id", from); put("to_id", to)
        })
    }

    fun reads(from: LocalDate, to: LocalDate): List<ReadEvent> {
        val out = ArrayList<ReadEvent>()
        readableDatabase.rawQuery(
            "SELECT date, from_id, to_id FROM quran_read WHERE date BETWEEN ? AND ? ORDER BY id", arrayOf(from.toString(), to.toString()),
        ).use { c -> while (c.moveToNext()) out += ReadEvent(LocalDate.parse(c.getString(0)), c.getInt(1), c.getInt(2)) }
        return out
    }

    // ---------- রোজা ----------

    fun setFast(date: LocalDate, status: FastStatus?) {
        val db = writableDatabase
        if (status == null) {
            db.delete("fast_log", "date=?", arrayOf(date.toString()))
            return
        }
        db.insertWithOnConflict("fast_log", null, ContentValues().apply {
            put("date", date.toString()); put("status", status.code)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun fast(date: LocalDate): FastStatus? =
        readableDatabase.rawQuery("SELECT status FROM fast_log WHERE date=?", arrayOf(date.toString())).use { c ->
            if (c.moveToFirst()) FastStatus.fromCode(c.getInt(0)) else null
        }

    fun fasts(from: LocalDate, to: LocalDate): Map<LocalDate, FastStatus> {
        val out = HashMap<LocalDate, FastStatus>()
        readableDatabase.rawQuery("SELECT date, status FROM fast_log WHERE date BETWEEN ? AND ?", arrayOf(from.toString(), to.toString())).use { c ->
            while (c.moveToNext()) FastStatus.fromCode(c.getInt(1))?.let { out[LocalDate.parse(c.getString(0))] = it }
        }
        return out
    }

    // ---------- সুন্নাত ও খুশু (v0.9.5) ----------

    fun setSunnah(date: LocalDate, slot: com.istiqamah.app.core.SunnahSlot, done: Boolean) {
        val db = writableDatabase
        if (done) db.insertWithOnConflict("sunnah_log", null, ContentValues().apply {
            put("date", date.toString()); put("slot", slot.code)
        }, SQLiteDatabase.CONFLICT_IGNORE)
        else db.delete("sunnah_log", "date=? AND slot=?", arrayOf(date.toString(), slot.code.toString()))
    }

    fun sunnah(from: LocalDate, to: LocalDate): Map<LocalDate, Set<com.istiqamah.app.core.SunnahSlot>> {
        val out = HashMap<LocalDate, MutableSet<com.istiqamah.app.core.SunnahSlot>>()
        readableDatabase.rawQuery("SELECT date, slot FROM sunnah_log WHERE date BETWEEN ? AND ?", arrayOf(from.toString(), to.toString())).use { c ->
            while (c.moveToNext()) com.istiqamah.app.core.SunnahSlot.fromCode(c.getInt(1))?.let {
                out.getOrPut(LocalDate.parse(c.getString(0))) { HashSet() }.add(it)
            }
        }
        return out
    }

    fun setKhushu(date: LocalDate, prayer: Prayer, score: Int) {
        writableDatabase.insertWithOnConflict("khushu_log", null, ContentValues().apply {
            put("date", date.toString()); put("prayer", prayer.ordinal); put("score", score.coerceIn(1, 3))
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun khushu(from: LocalDate, to: LocalDate): Map<LocalDate, Map<Prayer, Int>> {
        val out = HashMap<LocalDate, MutableMap<Prayer, Int>>()
        readableDatabase.rawQuery("SELECT date, prayer, score FROM khushu_log WHERE date BETWEEN ? AND ?", arrayOf(from.toString(), to.toString())).use { c ->
            while (c.moveToNext()) Prayer.entries.getOrNull(c.getInt(1))?.let {
                out.getOrPut(LocalDate.parse(c.getString(0))) { HashMap() }[it] = c.getInt(2)
            }
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
