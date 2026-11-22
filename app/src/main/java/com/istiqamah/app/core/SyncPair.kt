package com.istiqamah.app.core

import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// =====================================================================
// ফোন ↔ ম্যাক জোড়া: ম্যাক একটা QR দেখায়, ফোন স্ক্যান করে।
// QR-এ থাকে ম্যাকের নাম/আইডি, লোকাল নেটওয়ার্কের ঠিকানা আর ৩২ বাইটের গোপন চাবি।
// প্রতিটি বার্তা ওই চাবি দিয়ে AES-GCM-এ এনক্রিপ্ট — চাবি ছাড়া কেউ পড়তে বা বানাতে পারে না।
// ইন্টারনেট বা কোনো সার্ভার নেই; শুধু একই Wi-Fi।
// =====================================================================

class PairInfo(
    val macId: String,
    val name: String,
    val key: ByteArray,
    val hosts: List<String>,
    val port: Int,
) {
    override fun equals(other: Any?): Boolean = other is PairInfo && macId == other.macId && name == other.name &&
        key.contentEquals(other.key) && hosts == other.hosts && port == other.port
    override fun hashCode(): Int = macId.hashCode() * 31 + key.contentHashCode()
}

object SyncPair {
    const val PREFIX = "istiqamah://pair?"
    private val rnd = SecureRandom()
    private val b64 = Base64.getUrlEncoder().withoutPadding()
    private val unb64 = Base64.getUrlDecoder()

    fun newKey(): ByteArray = ByteArray(32).also { rnd.nextBytes(it) }

    /** ৮ অক্ষরের এলোমেলো আইডি (a-z0-9) */
    fun newId(): String {
        val abc = "abcdefghijkmnpqrstuvwxyz23456789"
        return (1..8).map { abc[rnd.nextInt(abc.length)] }.joinToString("")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun uri(p: PairInfo): String = PREFIX + listOf(
        "v" to "1", "id" to p.macId, "n" to p.name, "k" to b64.encodeToString(p.key),
        "h" to p.hosts.joinToString(","), "p" to p.port.toString(),
    ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }

    fun parse(s: String?): PairInfo? {
        val t = s?.trim() ?: return null
        if (!t.startsWith(PREFIX)) return null
        val q = t.removePrefix(PREFIX).split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to runCatching { URLDecoder.decode(it.substring(i + 1), "UTF-8") }.getOrNull()
        }.toMap()
        if (q["v"] != "1") return null
        val id = q["id"]?.takeIf { it.matches(Regex("[a-z0-9]{4,16}")) } ?: return null
        val key = runCatching { unb64.decode(q["k"]) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
        val port = q["p"]?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
        val hosts = q["h"].orEmpty().split(',').map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 64 && it.all { c -> c.isLetterOrDigit() || c in ".:-%" } }.take(6)
        val name = q["n"].orEmpty().take(40).ifBlank { "Mac" }
        return PairInfo(id, name, key, hosts, port)
    }

    fun keyToString(k: ByteArray): String = b64.encodeToString(k)
    fun keyFromString(s: String?): ByteArray? = runCatching { unb64.decode(s) }.getOrNull()?.takeIf { it.size == 32 }
}

object SyncCrypto {
    private const val NONCE = 12
    private val rnd = SecureRandom()

    /** nonce(১২) + ciphertext+tag; aad = বার্তার দিক ("req"/"res"), যাতে উত্তরকে প্রশ্ন বানিয়ে ফেরত পাঠানো না যায় */
    fun seal(key: ByteArray, plain: ByteArray, aad: String): ByteArray {
        val nonce = ByteArray(NONCE).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray())
        return nonce + c.doFinal(plain)
    }

    /** চাবি না মিললে বা কেউ বদলালে null */
    fun open(key: ByteArray, sealed: ByteArray, aad: String): ByteArray? {
        if (sealed.size < NONCE + 16) return null
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 0, NONCE))
            c.updateAAD(aad.toByteArray())
            c.doFinal(sealed, NONCE, sealed.size - NONCE)
        }.getOrNull()
    }
}
