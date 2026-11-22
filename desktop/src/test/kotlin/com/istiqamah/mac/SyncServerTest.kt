package com.istiqamah.mac

import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.JamaatSettings
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.AsrMadhab
import com.istiqamah.app.core.SyncPair
import com.istiqamah.app.core.WorkSchedule
import com.istiqamah.app.sync.SyncWire
import com.istiqamah.app.sync.SyncedSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

/** আসল সকেটে ফোনের মতো প্রশ্ন পাঠিয়ে ম্যাকের উত্তর যাচাই (CI-তে চলে) */
class SyncServerTest {
    @Test fun encryptedRoundTripAndWrongKeyIgnored() {
        val key = SyncPair.newKey()
        val server = SyncServer(key = { key }, handle = { req -> JSONObject().put("echo", req.getString("hello")) })
        val port = server.start(0)
        try {
            val res = SyncWire.exchange("127.0.0.1", port, key, JSONObject().put("hello", "সালাম"))
            assertEquals("সালাম", res?.getString("echo"))
            assertNull(SyncWire.exchange("127.0.0.1", port, SyncPair.newKey(), JSONObject().put("hello", "x"), readMs = 1500))
        } finally { server.stop() }
    }

    @Test fun settingsJsonRoundTrip() {
        val s = SyncedSettings(
            "ব্রাহ্মণবাড়িয়া", 23.9571, 91.1119, CalcMethod.KARACHI, AsrMadhab.HANAFI, listOf(0, 1, 0, -1, 2),
            Gender.MALE, Intensity.STRONG, JamaatSettings(officeAsr = java.time.LocalTime.of(16, 0), leadMin = 20),
            WorkSchedule(days = setOf(java.time.DayOfWeek.SATURDAY)), null,
        )
        assertEquals(s, SyncWire.parseSettings(JSONObject(SyncWire.settingsJson(s).toString())))
    }

    @Test fun macAppMergesPhoneMarksAndSettings() {
        val app = MacApp(MacStore(Files.createTempDirectory("ist").toFile()))
        val today = LocalDate.now()
        app.mark(today, Prayer.FAJR, PrayerStatus.ALONE) // ম্যাকে আগে দেওয়া
        val phoneEvents = listOf(
            LogEvent(today, Prayer.DHUHR, PrayerStatus.JAMAAH, System.currentTimeMillis(), "phone"),
            LogEvent(today.minusDays(200), Prayer.ASR, PrayerStatus.QAZA, 5, "phone"), // ৬০ দিনের বাইরে — বাদ
        )
        val settings = SyncedSettings(
            "সিলেট", 24.8949, 91.8687, CalcMethod.MWL, AsrMadhab.SHAFII, listOf(0, 0, 0, 0, 0),
            Gender.MALE, Intensity.LIGHT, JamaatSettings(), WorkSchedule(), null,
        )
        val req = JSONObject(SyncWire.request("ph1", "Pixel", phoneEvents, settings).toString())
        val res = app.onSyncRequest(req)
        assertNotNull(res)
        assertEquals(PrayerStatus.JAMAAH, app.status(today, Prayer.DHUHR))
        assertNull(app.status(today.minusDays(200), Prayer.ASR))
        assertEquals("সিলেট", app.settings.city)
        assertEquals(CalcMethod.MWL, app.settings.method)
        assertTrue(app.pair.paired)
        assertEquals("Pixel", app.pair.phoneName)
        val back = SyncWire.parseEvents(res!!.getJSONArray("events"))
        assertTrue(back.any { it.prayer == Prayer.FAJR && it.status == PrayerStatus.ALONE && it.device == "mac" })
        // ঘড়ির অনেক পার্থক্য বা অন্য যন্ত্র — উত্তর নয়
        assertNull(app.onSyncRequest(JSONObject(SyncWire.request("ph1", "Pixel", emptyList(), null, now = 1000L).toString())))
        assertNull(app.onSyncRequest(JSONObject().put("v", 1).put("dev", "mac").put("ts", System.currentTimeMillis())))
    }
}

/** ম্যাকের QR আঁকা লিংকটা আবার পড়লে হুবহু একই জোড়া পাওয়া যায় */
class PairQrTest {
    @Test fun qrDecodesBackToSamePair() {
        val info = com.istiqamah.app.core.PairInfo("abcd2345", "Bayejid’s MacBook Pro", SyncPair.newKey(), listOf("192.168.1.20"), 47630)
        val text = SyncPair.uri(info)
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 2))
        val scale = 4
        val w = m.width * scale
        val h = m.height * scale
        val px = IntArray(w * h) { i -> if (m.get((i % w) / scale, (i / w) / scale)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val bmp = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(com.google.zxing.RGBLuminanceSource(w, h, px)))
        val decoded = com.google.zxing.qrcode.QRCodeReader().decode(bmp).text
        assertEquals(info, SyncPair.parse(decoded))
    }
}
