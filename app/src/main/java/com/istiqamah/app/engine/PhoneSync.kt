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

    /** জোড়া লাগানো সব ম্যাক (v0.18: বাসা, অফিস… একাধিক) */
    fun pairs(ctx: Context): List<PairInfo> = Prefs(ctx).syncPairs

    fun hasPairs(ctx: Context): Boolean = pairs(ctx).isNotEmpty()

    /** QR থেকে পাওয়া লিংক দিয়ে জোড়া (আগেরগুলো থাকে) — সাথে সাথে ওই ম্যাকের সাথে প্রথম সিঙ্ক */
    fun pair(ctx: Context, uri: String): Result? {
        val info = SyncPair.parse(uri) ?: return null
        Prefs(ctx).addSyncPair(info)
        return syncNow(ctx, only = info.macId)
    }

    fun unpair(ctx: Context, macId: String) {
        Prefs(ctx).removeSyncPair(macId)
    }

    /** পেছনে চুপচাপ সিঙ্ক (চিহ্ন দেওয়ার পরে) — জোড়া না থাকলে কিছুই না */
    fun syncAsync(ctx: Context, after: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!hasPairs(app)) return
        exec.execute {
            val changed = runCatching { syncNow(app) == Result.OK && lastChanged }.getOrDefault(false)
            after?.invoke(changed)
        }
    }

    @Volatile private var lastChanged = false

    /**
     * এখনই সিঙ্ক — ব্লক করে, [budgetMs]-এর মধ্যে। যে ম্যাক এই Wi-Fi-তে আছে তার সাথে
     * (সবচেয়ে সম্প্রতি পাওয়াটা আগে চেষ্টা)। একাধিক পাওয়া গেলে সবার সাথে — ফোন সেতুর কাজ করে:
     * অফিসের ম্যাকের চিহ্ন ফোন হয়ে বাসার ম্যাকে যায়।
     */
    @Synchronized
    fun syncNow(ctx: Context, budgetMs: Long = 6000, only: String? = null): Result {
        lastChanged = false
        val prefs = Prefs(ctx)
        val all = prefs.syncPairs.filter { only == null || it.macId == only }
        if (all.isEmpty()) return Result.NOT_PAIRED
        val order = all.sortedWith(compareByDescending<PairInfo> { prefs.syncOk(it.macId) }.thenByDescending { prefs.syncAt(it.macId) })
        val deadline = System.currentTimeMillis() + budgetMs
        var any = false
        for (pair in order) {
            if (deadline - System.currentTimeMillis() < 400) break
            if (syncOne(ctx, prefs, pair, deadline)) any = true
        }
        return if (any) Result.OK else Result.NOT_FOUND
    }

    private fun syncOne(ctx: Context, prefs: Prefs, pair: PairInfo, deadline: Long): Boolean {
        val store = LogStore.get(ctx)
        val from = Engine.today(ctx).minusDays(SyncWire.WINDOW_DAYS)
        val local = store.syncEvents(from)
        val request = SyncWire.request(prefs.syncDeviceId, deviceName(), local, settings(prefs))

        val candidates = LinkedHashSet<Pair<String, Int>>()
        prefs.syncHost(pair.macId).takeIf { it.contains(':') }?.let { h ->
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
            prefs.setSyncOk(pair.macId, false)
            return false
        }
        used?.let { prefs.setSyncHost(pair.macId, "${it.first}:${it.second}") }
        prefs.setSyncOk(pair.macId, true)
        prefs.setSyncAt(pair.macId, System.currentTimeMillis())
        r.optString("name", "").takeIf { it.isNotBlank() }?.let { prefs.setSyncMacName(pair.macId, it) }

        val remote = SyncWire.parseEvents(r.optJSONArray("events")).filter { !it.date.isBefore(from) }
        val apply = SyncLog.toApply(local, remote)
        if (apply.isNotEmpty()) {
            applyRemote(ctx, apply)
            lastChanged = true
        }
        return true
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
