package com.istiqamah.app.engine

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.PairInfo
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.SyncLog
import com.istiqamah.app.core.SyncPair
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.notify.Notifier
import com.istiqamah.app.sync.SyncWire
import com.istiqamah.app.sync.SyncedSettings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ফোনের দিক থেকে ম্যাকের সাথে সিঙ্ক — শুধু একই Wi-Fi-তে, এনক্রিপ্ট করে।
 * কখন: চিহ্ন দেওয়ার পরপর, অ্যাপ খুললে, সেটিং বদলালে, আর নামাজের ডাক বাজার ঠিক আগে
 * (ম্যাকে পড়া হয়ে গেলে ফোন আর বাজে না)।
 * যা যায়: শেষ ৬০ দিনের নামাজের চিহ্ন + নামাজের সময়ের সেটিংস। আর কিছু না।
 */
object PhoneSync {
    private const val TAG = "IstiqamahSync"
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "istiqamah-sync").apply { isDaemon = true } }

    enum class Result(val bn: String) {
        NOT_PAIRED("জোড়া নেই"),
        OK("সিঙ্ক হয়েছে"),
        NOT_FOUND("ম্যাক পাওয়া যায়নি — একই Wi-Fi-তে আছে আর ম্যাকে ইস্তিকামাহ চালু আছে তো?"),
        REJECTED("ম্যাক চাবি চিনল না — ম্যাকে নতুন QR বানানো হয়ে থাকলে আবার স্ক্যান করুন"),
    }

    fun paired(ctx: Context): PairInfo? = Prefs(ctx).syncPair

    /** QR থেকে পাওয়া লিংক দিয়ে জোড়া — সাথে সাথে প্রথম সিঙ্ক (ব্লক করে; UI থেকে IO থ্রেডে ডাকুন) */
    fun pair(ctx: Context, uri: String): Result? {
        val info = SyncPair.parse(uri) ?: return null
        val prefs = Prefs(ctx)
        prefs.syncPair = info
        prefs.syncLastHost = ""
        prefs.syncMacName = info.name
        return syncNow(ctx)
    }

    fun unpair(ctx: Context) {
        Prefs(ctx).syncPair = null
    }

    /** পেছনে চুপচাপ সিঙ্ক (চিহ্ন দেওয়ার পরে) — জোড়া না থাকলে কিছুই না */
    fun syncAsync(ctx: Context, after: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (Prefs(app).syncPair == null) return
        exec.execute {
            val changed = runCatching { syncNow(app) == Result.OK && lastChanged }.getOrDefault(false)
            after?.invoke(changed)
        }
    }

    @Volatile private var lastChanged = false

    /**
     * এখনই সিঙ্ক — ব্লক করে, [budgetMs]-এর মধ্যে। ম্যাক না পেলে চুপচাপ ব্যর্থ।
     * ম্যাক থেকে নতুন চিহ্ন এলে বসিয়ে অ্যালার্ম নতুন করে সাজায়।
     */
    @Synchronized
    fun syncNow(ctx: Context, budgetMs: Long = 6000): Result {
        lastChanged = false
        val prefs = Prefs(ctx)
        val pair = prefs.syncPair ?: return Result.NOT_PAIRED
        val deadline = System.currentTimeMillis() + budgetMs
        val store = LogStore.get(ctx)
        val from = Engine.today(ctx).minusDays(SyncWire.WINDOW_DAYS)
        val local = store.syncEvents(from)
        val request = SyncWire.request(prefs.syncDeviceId, deviceName(), local, settings(prefs))

        val candidates = LinkedHashSet<Pair<String, Int>>()
        prefs.syncLastHost.takeIf { it.contains(':') }?.let { h ->
            h.substringAfterLast(':').toIntOrNull()?.let { candidates += h.substringBeforeLast(':') to it }
        }
        pair.hosts.forEach { candidates += it to pair.port }

        var res: org.json.JSONObject? = null
        var used: Pair<String, Int>? = null
        for (c in candidates) {
            val left = deadline - System.currentTimeMillis()
            if (left < 300) break
            res = SyncWire.exchange(c.first, c.second, pair.key, request, connectMs = minOf(1200L, left).toInt(), readMs = minOf(4000L, left).toInt())
            if (res != null) { used = c; break }
        }
        if (res == null) {
            // IP বদলে গেলে: Bonjour-এ খুঁজি
            val left = deadline - System.currentTimeMillis()
            if (left > 800) find(ctx, pair.macId, minOf(3000L, left - 500))?.let { c ->
                val l2 = deadline - System.currentTimeMillis()
                if (l2 > 300) {
                    res = SyncWire.exchange(c.first, c.second, pair.key, request, connectMs = minOf(1500L, l2).toInt(), readMs = minOf(4000L, l2).toInt())
                    if (res != null) used = c
                }
            }
        }
        val r = res
        if (r == null || r.optString("dev") != "mac") {
            prefs.syncLastOk = false
            return Result.NOT_FOUND
        }
        used?.let { prefs.syncLastHost = "${it.first}:${it.second}" }
        prefs.syncLastOk = true
        prefs.syncLastAt = System.currentTimeMillis()
        r.optString("name", "").takeIf { it.isNotBlank() }?.let { prefs.syncMacName = it.take(40) }

        val remote = SyncWire.parseEvents(r.optJSONArray("events")).filter { !it.date.isBefore(from) }
        val apply = SyncLog.toApply(local, remote)
        if (apply.isNotEmpty()) {
            applyRemote(ctx, apply)
            lastChanged = true
        }
        return Result.OK
    }

    /** ম্যাকের নতুন চিহ্ন ফোনে বসানো — কাযার হিসাব চেক-ইনের মতোই, তারপর অ্যালার্ম নতুন করে */
    private fun applyRemote(ctx: Context, events: List<LogEvent>) {
        val store = LogStore.get(ctx)
        for (e in events) {
            val old = store.day(e.date).status(e.prayer)
            store.setStatus(e.date, e.prayer, e.status, ts = e.ts, src = e.device)
            val delta = (if (e.status == PrayerStatus.MISSED) 1 else 0) - (if (old == PrayerStatus.MISSED) 1 else 0)
            if (delta != 0) store.setQaza(e.prayer, maxOf(0, store.qaza(e.prayer) + delta))
            if (e.status != null) {
                Notifier.cancelFor(ctx, e.date, e.prayer)
                Engine.cancelSnooze(ctx, e.date, e.prayer)
            }
        }
        Engine.reschedule(ctx)
        Log.i(TAG, "applied ${events.size} marks from Mac")
    }

    private fun settings(p: Prefs): SyncedSettings? {
        val loc = p.location ?: return null
        val ps = p.prayerSettings
        val hm = p.homeMode
        return SyncedSettings(
            city = p.placeName, lat = loc.latitude, lng = loc.longitude,
            method = ps.method, madhab = ps.madhab, adjust = ps.adjustMinutes,
            gender = p.gender, intensity = p.intensity, jamaat = p.jamaatSettings,
            work = hm.schedule, pausedFrom = p.pausedFrom,
        )
    }

    private fun deviceName(): String =
        listOfNotNull(Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, Build.MODEL).joinToString(" ").take(40).ifBlank { "ফোন" }

    /** Bonjour (_istiqamah._tcp) দিয়ে এই জোড়ার ম্যাক খোঁজা */
    @Suppress("DEPRECATION")
    private fun find(ctx: Context, macId: String, timeoutMs: Long): Pair<String, Int>? {
        val nsd = ctx.getSystemService(NsdManager::class.java) ?: return null
        val found = AtomicReference<Pair<String, Int>?>(null)
        val latch = CountDownLatch(1)
        val resolving = java.util.concurrent.atomic.AtomicBoolean(false)
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { resolving.set(false) }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress
                if (host != null && info.port > 0) { found.set(host to info.port); latch.countDown() }
                resolving.set(false)
            }
        }
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName.contains(macId) && resolving.compareAndSet(false, true)) {
                    runCatching { nsd.resolveService(info, resolver) }.onFailure { resolving.set(false) }
                }
            }
        }
        return try {
            nsd.discoverServices(SyncWire.SERVICE, NsdManager.PROTOCOL_DNS_SD, discovery)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            found.get()
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { nsd.stopServiceDiscovery(discovery) }
        }
    }
}
