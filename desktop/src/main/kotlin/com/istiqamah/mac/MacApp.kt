package com.istiqamah.mac

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.ContentPicker
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.JamaatTime
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.core.SyncLog
import com.istiqamah.app.core.PairInfo
import com.istiqamah.app.core.SyncPair
import com.istiqamah.app.sync.SyncWire
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZonedDateTime

/** পর্দায় দেখানো একটা ডাক */
data class ActiveCall(val id: Long, val reminder: MacReminder, val content: ContentItem?)

/**
 * ম্যাক অ্যাপের পুরো অবস্থা: সেটিংস, চিহ্ন, চলমান ডাক।
 * প্রতি ১৫ সেকেন্ডে [tick] — কোন ডাক বাজার সময় হলো দেখে।
 */
class MacApp(private val store: MacStore = MacStore()) {
    var settings by mutableStateOf(store.loadSettings()); private set
    private var events: List<LogEvent> = store.loadEvents()
    var logs by mutableStateOf(SyncLog.dayLogs(events)); private set
    var windows by mutableStateOf<Map<LocalDate, List<PrayerWindow>>>(emptyMap()); private set
    var jamaat by mutableStateOf<Map<Pair<LocalDate, Prayer>, JamaatTime>>(emptyMap()); private set
    var summary by mutableStateOf(""); private set
    var now by mutableStateOf(ZonedDateTime.now()); private set
    /** এখন চলমান ওয়াক্ত চিহ্নহীন — মেনু বারের আইকনে ছোট বিন্দু */
    var pending by mutableStateOf(false); private set

    var showToday by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showPair by mutableStateOf(false)

    // ---------- ফোনের সাথে সিঙ্ক ----------
    var pair by mutableStateOf(store.loadPair()); private set
    var syncPort by mutableStateOf(0); private set
    private var bonjour: Process? = null
    private val server = SyncServer(key = { pair.key }, handle = { req -> onUiThread { onSyncRequest(req) } })
    val calls = mutableStateListOf<ActiveCall>()
    var fullScreen by mutableStateOf<ActiveCall?>(null)

    val content: List<ContentItem> = runCatching {
        MacApp::class.java.classLoader.getResourceAsStream("content.json")!!.bufferedReader(Charsets.UTF_8).use { MacStore.parseContent(it.readText()) }
    }.getOrDefault(emptyList())

    private val seen = HashMap<String, LocalDate>()
    private val fired = HashSet<String>()
    private val snoozed = ArrayList<Pair<ZonedDateTime, PlannedNotif>>()
    /** "এখনই যাচ্ছি" / "১০ মিনিট পরে"-র পর এই সময় পর্যন্ত কড়া ডাক চুপ */
    private val going = HashMap<Pair<LocalDate, Prayer>, ZonedDateTime>()
    private var lastTick: ZonedDateTime? = null
    private var nextId = 1L
    /** শেষবার উঠে দাঁড়ানোর ডাক বা দূরে থাকার পর */
    private var lastStand: ZonedDateTime = ZonedDateTime.now()

    val logged: Set<Pair<LocalDate, Prayer>>
        get() = logs.values.flatMap { l -> l.statuses.keys.map { l.date to it } }.toSet()

    init {
        MacSystem.setLaunchAtLogin(settings.launchAtLogin)
        refresh(ZonedDateTime.now())
        startSync()
    }

    private fun startSync() {
        Thread { MacNet.hostName(); MacNet.localAddresses() }.apply { isDaemon = true }.start() // আগেভাগে পড়ে রাখি
        syncPort = server.start()
        bonjour = MacNet.advertise(pair.macId, syncPort)
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { bonjour?.destroy() } })
    }

    /** UI-র থ্রেডে চালিয়ে ফল ফেরত (সার্ভারের থ্রেড থেকে ডাকা হয়) */
    private fun <T> onUiThread(f: () -> T): T? {
        if (java.awt.EventQueue.isDispatchThread()) return f()
        var out: T? = null
        runCatching { java.awt.EventQueue.invokeAndWait { out = f() } }
        return out
    }

    fun pairInfo(): PairInfo = MacNet.pairInfo(pair, syncPort)

    /** নতুন চাবি — আগের জোড়া ফোন আর সিঙ্ক করতে পারবে না, আবার স্ক্যান করতে হবে */
    fun resetPair() {
        pair = MacPair(pair.macId, SyncPair.newKey())
        runCatching { store.savePair(pair) }
    }

    /** ফোনের প্রশ্নের উত্তর: চিহ্ন মেলানো, ফোনের সেটিংস বসানো, ম্যাকের চিহ্ন ফেরত */
    fun onSyncRequest(req: JSONObject): JSONObject? {
        val nowMs = System.currentTimeMillis()
        if (req.optInt("v") != 1 || req.optString("dev") != "phone") return null
        if (kotlin.math.abs(nowMs - req.optLong("ts")) > SyncWire.MAX_SKEW_MS) return null
        val phoneId = req.optString("id", "").take(32).ifBlank { return null }
        val from = LocalDate.now().minusDays(SyncWire.WINDOW_DAYS)
        val remote = SyncWire.parseEvents(req.optJSONArray("events")).filter { !it.date.isBefore(from) }
        val apply = SyncLog.toApply(events, remote)
        if (apply.isNotEmpty()) {
            events = SyncLog.compact(SyncLog.merge(events, apply))
            runCatching { store.saveEvents(events) }
            logs = SyncLog.dayLogs(events)
            // ফোনে পড়া হয়ে গেলে ম্যাকের খোলা ডাক বন্ধ
            for (e in apply.filter { it.status != null }) {
                calls.removeAll { it.reminder.notif.date == e.date && it.reminder.notif.prayer == e.prayer }
                if (fullScreen?.reminder?.notif?.let { it.date == e.date && it.prayer == e.prayer } == true) fullScreen = null
                snoozed.removeAll { it.second.date == e.date && it.second.prayer == e.prayer }
            }
        }
        SyncWire.parseSettings(req.optJSONObject("settings"))?.let { ps ->
            val next = settings.withPhone(ps)
            if (next != settings) {
                settings = next
                runCatching { store.saveSettings(settings) }
            }
        }
        pair = pair.copy(phoneId = phoneId, phoneName = req.optString("name", "ফোন").take(40), lastSyncAt = nowMs)
        runCatching { store.savePair(pair) }
        refresh(ZonedDateTime.now())
        return SyncWire.response(MacNet.hostName(), events.filter { !it.date.isBefore(from) })
    }

    private fun refresh(t: ZonedDateTime) {
        now = t
        windows = MacEngine.windowsByDate(settings, t.toLocalDate(), t.zone)
        jamaat = MacEngine.jamaat(settings, windows)
        summary = MacEngine.summary(windows, t, logged)
        val cur = MacEngine.current(windows, t)
        pending = cur != null && (cur.start.toLocalDate() to cur.prayer) !in logged &&
            !(settings.pausedFrom != null && settings.gender == com.istiqamah.app.core.Gender.FEMALE)
    }

    fun tick(t: ZonedDateTime = ZonedDateTime.now()) {
        val from = lastTick ?: t.minusSeconds(30)
        lastTick = t
        refresh(t)
        val logged = logged
        going.entries.removeIf { it.value.isBefore(t.minusHours(6)) }
        for (n in MacEngine.due(settings, from, t, logged, going)) {
            val key = "${n.date}|${n.prayer}|${n.kind}"
            if (fired.add(key)) fire(n)
        }
        // "১০ মিনিট পরে" — সময় হলে আবার, তবে ততক্ষণে পড়া হয়ে গেলে নয়
        val ready = snoozed.filter { !it.first.isAfter(t) }
        snoozed.removeAll(ready.toSet())
        ready.map { it.second }.filter { (it.date to it.prayer) !in logged }.forEach { fire(it, forceCall = true) }
        if (fired.size > 500) fired.clear()
        checkStand(t, logged)
    }

    /** v1.3: এক ঘণ্টা একটানা কাজে থাকলে উঠে দাঁড়ানোর ছোট ডাক */
    private fun checkStand(t: ZonedDateTime, logged: Set<Pair<LocalDate, Prayer>>) {
        if (!settings.standHourly) return
        val idle = MacSystem.idleSeconds()
        if (idle >= MacEngine.AWAY_SEC) { lastStand = t; return } // উঠে গিয়েছিলেন — নতুন করে গোনা
        val busy = calls.isNotEmpty() || fullScreen != null
        if (MacEngine.standDue(settings, lastStand, t, idle, busy || MacEngine.nearPrayerCall(settings, t, logged))) {
            lastStand = t
            MacSystem.banner("🧍 একটু উঠে দাঁড়ান", "এক ঘণ্টা বসে আছেন — ২–৩ মিনিট হাঁটুন বা স্ট্রেচ করুন। তোমার শরীরেরও তোমার ওপর হক আছে (বুখারি ১৯৭৫)।", settings.sound)
        }
    }

    private fun fire(n: PlannedNotif, forceCall: Boolean = false) {
        val p = n.prayer ?: return
        val r = MacEngine.reminder(settings, n, windows, jamaat).let {
            if (forceCall && it.alert == MacAlert.BANNER) it.copy(alert = MacAlert.CALL) else it
        }
        val today = now.toLocalDate()
        val item = MacEngine.pickContent(content, n.kind, p, settings.gender, seen, today, salt = today.dayOfYear + p.ordinal)
        item?.let { seen[it.id] = today }
        when (r.alert) {
            MacAlert.BANNER -> MacSystem.banner(r.title, item?.shortBn?.let { "$it — ${item.refBn}" } ?: r.body, settings.sound)
            MacAlert.CALL -> {
                calls.removeAll { it.reminder.notif.date == n.date && it.reminder.notif.prayer == p }
                calls.add(ActiveCall(nextId++, r, item))
                if (settings.sound) MacSystem.chime()
            }
            MacAlert.FULLSCREEN -> {
                calls.removeAll { it.reminder.notif.date == n.date && it.reminder.notif.prayer == p }
                fullScreen = ActiveCall(nextId++, r, item)
                if (settings.sound) MacSystem.chime(strong = true)
            }
        }
    }

    fun dismiss(c: ActiveCall) {
        calls.removeAll { it.id == c.id }
        if (fullScreen?.id == c.id) fullScreen = null
    }

    fun snooze(c: ActiveCall) {
        dismiss(c)
        val n = c.reminder.notif
        val at = ZonedDateTime.now().plusMinutes(MacEngine.SNOOZE_MIN)
        n.prayer?.let { going[n.date to it] = at }
        snoozed.add(at to n)
    }

    /** "এখনই যাচ্ছি": ১২ মিনিট চুপ, তারপর একবার খোঁজ — "পড়েছেন?" */
    fun goNow(c: ActiveCall) {
        dismiss(c)
        val n = c.reminder.notif
        val p = n.prayer ?: return
        val at = ZonedDateTime.now().plusMinutes(MacEngine.GOING_MIN)
        going[n.date to p] = at
        snoozed.add(at to PlannedNotif(n.date, p, com.istiqamah.app.core.NotifKind.WUDU_CHECK, at))
    }

    /** পুরো পর্দা খোলা থাকলে অ্যাপ বন্ধ করা যায় না — আগে একটা বোতাম বেছে নিতে হয় */
    val blocking: Boolean get() = fullScreen != null

    /** চিহ্ন দেওয়া (status = null হলে মুছে ফেলা) */
    fun mark(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        events = events + LogEvent(date, prayer, status, System.currentTimeMillis(), MacStore.DEVICE)
        runCatching { store.saveEvents(events) }
        events = SyncLog.compact(events)
        logs = SyncLog.dayLogs(events)
        if (status != null) {
            calls.removeAll { it.reminder.notif.date == date && it.reminder.notif.prayer == prayer }
            if (fullScreen?.reminder?.notif?.let { it.date == date && it.prayer == prayer } == true) fullScreen = null
            snoozed.removeAll { it.second.date == date && it.second.prayer == prayer }
        }
        refresh(ZonedDateTime.now())
    }

    fun status(date: LocalDate, prayer: Prayer): PrayerStatus? = logs[date]?.status(prayer)

    fun update(f: (MacSettings) -> MacSettings) {
        val old = settings
        settings = f(settings)
        runCatching { store.saveSettings(settings) }
        if (old.launchAtLogin != settings.launchAtLogin) MacSystem.setLaunchAtLogin(settings.launchAtLogin)
        refresh(ZonedDateTime.now())
    }

    /** আজকের আয়াত/হাদিস — ফোনের "daily" ট্যাগ থেকে */
    val daily: ContentItem? by lazy {
        val pool = content.filter { settings.gender.allows(it.tags) }
        ContentPicker.pick(pool, "daily", emptyMap(), LocalDate.now())
    }

    fun todayLog(): DayLog? = logs[now.toLocalDate()]

    /** CI-তে স্ক্রিনশটের জন্য (ISTIQAMAH_DEMO=today|settings|call|full) */
    fun demo(mode: String) {
        val t = ZonedDateTime.now()
        refresh(t)
        val w = MacEngine.current(windows, t) ?: windows[t.toLocalDate()]?.firstOrNull() ?: return
        when (mode) {
            "today" -> showToday = true
            "settings" -> showSettings = true
            "pair" -> showPair = true
            "call" -> fire(PlannedNotif(w.start.toLocalDate(), w.prayer, NotifKind.N1_INVITE, t), forceCall = true)
            "full" -> fire(PlannedNotif(w.start.toLocalDate(), w.prayer, NotifKind.N3_LAST_CALL, t))
        }
    }
}
