package com.istiqamah.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SyncTest {
    private val d = LocalDate.of(2026, 11, 22)

    @Test fun pairUriRoundTrip() {
        val p = PairInfo(SyncPair.newId(), "Bayejid's MacBook Pro", SyncPair.newKey(), listOf("192.168.0.105", "10.0.0.4"), 47630)
        val u = SyncPair.uri(p)
        assertTrue(u.startsWith(SyncPair.PREFIX))
        assertEquals(p, SyncPair.parse(u))
    }

    @Test fun pairUriRejectsJunk() {
        assertNull(SyncPair.parse("https://example.com"))
        assertNull(SyncPair.parse(SyncPair.PREFIX + "v=1&id=abcd1234&k=short&p=47630"))
        assertNull(SyncPair.parse(SyncPair.PREFIX + "v=2&id=abcd1234&k=" + SyncPair.keyToString(SyncPair.newKey()) + "&p=47630"))
        val ok = SyncPair.parse(SyncPair.PREFIX + "v=1&id=abcd1234&k=" + SyncPair.keyToString(SyncPair.newKey()) + "&p=47630&h=1.2.3.4,bad%20host;rm")
        assertEquals(listOf("1.2.3.4"), ok?.hosts)
        assertEquals("Mac", ok?.name)
    }

    @Test fun cryptoSealsAndRejectsTampering() {
        val k = SyncPair.newKey()
        val msg = "নামাজের চিহ্ন".toByteArray()
        val s = SyncCrypto.seal(k, msg, "req")
        assertArrayEquals(msg, SyncCrypto.open(k, s, "req"))
        assertNull(SyncCrypto.open(SyncPair.newKey(), s, "req")) // ভুল চাবি
        assertNull(SyncCrypto.open(k, s, "res"))                // উত্তরকে প্রশ্ন বানানো যায় না
        val bad = s.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertNull(SyncCrypto.open(k, bad, "req"))
        assertTrue(!SyncCrypto.seal(k, msg, "req").contentEquals(s)) // প্রতিবার নতুন nonce
    }

    @Test fun toApplyOnlyNewerRemote() {
        val phone = listOf(
            LogEvent(d, Prayer.FAJR, PrayerStatus.JAMAAH, 100, "phone"),
            LogEvent(d, Prayer.DHUHR, PrayerStatus.ALONE, 300, "phone"),
        )
        val mac = listOf(
            LogEvent(d, Prayer.FAJR, PrayerStatus.JAMAAH, 100, "phone"), // আগেই পাঠানো — একই
            LogEvent(d, Prayer.DHUHR, PrayerStatus.QAZA, 200, "mac"),    // পুরনো — বাদ
            LogEvent(d, Prayer.ASR, PrayerStatus.ALONE, 400, "mac"),     // নতুন
            LogEvent(d, Prayer.MAGHRIB, null, 500, "mac"),               // মুছে ফেলা — মনে রাখতে বসাই
        )
        val a = SyncLog.toApply(phone, mac)
        assertEquals(listOf(Prayer.ASR, Prayer.MAGHRIB), a.map { it.prayer })
        // বসানোর পর আবার সিঙ্ক করলে কিছু নেই
        assertEquals(emptyList<LogEvent>(), SyncLog.toApply(phone + a, mac))
        // আর উল্টো দিকে ম্যাকের জন্য: ফোনের যোহর নতুন
        assertEquals(listOf(Prayer.DHUHR), SyncLog.toApply(mac, phone).map { it.prayer })
    }
}
