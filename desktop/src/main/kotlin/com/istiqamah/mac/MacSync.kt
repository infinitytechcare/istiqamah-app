package com.istiqamah.mac

import com.istiqamah.app.core.PairInfo
import com.istiqamah.app.sync.SyncWire
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// =====================================================================
// ম্যাকের সিঙ্ক সার্ভার — একই Wi-Fi-তে ফোন এসে প্রশ্ন করে, ম্যাক উত্তর দেয়।
// চাবি ছাড়া কোনো বার্তা খোলা যায় না; ভুল চাবির সংযোগ চুপচাপ বন্ধ।
// Bonjour-এ "_istiqamah._tcp" নামে নিজেকে জানায়, যাতে রাউটার নতুন IP দিলেও ফোন খুঁজে পায়।
// =====================================================================

/** শুধু সংযোগ আর এনক্রিপশন — কী উত্তর দেবে তা [handle] ঠিক করে (টেস্টে আলাদা করে চালানো যায়) */
class SyncServer(
    private val key: () -> ByteArray,
    private val handle: (JSONObject) -> JSONObject?,
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(2) { r -> Thread(r, "istiqamah-sync").apply { isDaemon = true } }

    val port: Int get() = server?.localPort ?: 0

    /** [preferred] থেকে পরের ১০টা পোর্টের মধ্যে যেটা খালি; 0 = যেকোনো */
    fun start(preferred: Int = SyncWire.PORT): Int {
        val ports = if (preferred == 0) listOf(0) else (preferred until preferred + 10).toList()
        for (p in ports) {
            val s = runCatching {
                ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(p), 8) }
            }.getOrNull() ?: continue
            server = s
            Thread({ loop(s) }, "istiqamah-sync-accept").apply { isDaemon = true }.start()
            return s.localPort
        }
        return 0
    }

    private fun loop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = runCatching { s.accept() }.getOrNull() ?: continue
            runCatching { pool.execute { serve(c) } }.onFailure { runCatching { c.close() } }
        }
    }

    private fun serve(c: Socket) {
        c.use {
            runCatching {
                it.soTimeout = 5000
                val k = key()
                val req = SyncWire.read(DataInputStream(it.getInputStream().buffered()), k, "req") ?: return
                val res = handle(req) ?: return
                SyncWire.write(DataOutputStream(it.getOutputStream().buffered()), k, res, "res")
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
        pool.shutdown()
        runCatching { pool.awaitTermination(1, TimeUnit.SECONDS) }
    }
}

/** ফোনের সেটিংস বসানো — শুধু নামাজের সময় ও ডাকের অংশ; ম্যাকের নিজের পছন্দ (জানালা, শব্দ, লগইন) থাকে */
fun MacSettings.withPhone(p: com.istiqamah.app.sync.SyncedSettings): MacSettings = copy(
    city = p.city.ifBlank { city }, lat = p.lat, lng = p.lng, method = p.method, madhab = p.madhab, adjust = p.adjust,
    gender = p.gender, intensity = p.intensity, jamaat = p.jamaat, work = p.work, pausedFrom = p.pausedFrom,
)

object MacNet {
    /** এই ম্যাকের লোকাল নেটওয়ার্কের IPv4 ঠিকানা (Wi-Fi/ইথারনেট) — QR-এ দিই */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && !it.name.startsWith("utun") && !it.name.startsWith("bridge") }
            .sortedBy { if (it.name == "en0") 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())

    fun hostName(): String = runCatching {
        // "Bayejids-MacBook-Pro.local" → "Bayejids MacBook Pro"
        InetAddress.getLocalHost().hostName.removeSuffix(".local").replace('-', ' ').take(40)
    }.getOrDefault("Mac")

    /** Bonjour-এ নিজেকে জানানো — macOS-এর নিজস্ব dns-sd দিয়ে (আলাদা কোনো লাইব্রেরি নয়) */
    fun advertise(macId: String, port: Int): Process? {
        if (!MacSystem.isMac || port == 0) return null
        return runCatching {
            ProcessBuilder("/usr/bin/dns-sd", "-R", "Istiqamah-$macId", SyncWire.SERVICE, "local", port.toString(), "id=$macId")
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }.getOrNull()
    }

    fun pairInfo(pair: MacPair, port: Int, name: String = hostName()): PairInfo =
        PairInfo(pair.macId, name, pair.key, localAddresses().take(4), port)
}
