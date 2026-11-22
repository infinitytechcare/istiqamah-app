package com.istiqamah.mac

import com.istiqamah.app.core.CalcMethod
import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** org.json লাগে — তাই শুধু gradle-এর টেস্টে চলে (CI-তে ম্যাকে) */
class MacStoreTest {
    @Test fun settingsRoundTrip() {
        val dir = Files.createTempDirectory("ist").toFile()
        val store = MacStore(dir)
        assertEquals(MacSettings(), store.loadSettings()) // ফাইল নেই → ডিফল্ট
        val s = MacSettings(
            city = "ব্রাহ্মণবাড়িয়া", lat = 23.9571, lng = 91.1119, method = CalcMethod.MWL,
            adjust = listOf(1, 0, -2, 0, 3), gender = Gender.MALE, intensity = Intensity.STRONG,
            jamaat = MacSettings().jamaat.copy(officeDhuhr = LocalTime.of(13, 30), officeAsr = null, leadMin = 20),
            work = MacSettings().work.copy(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), start = LocalTime.of(10, 0)),
            callWindow = false, sound = false, pausedFrom = null,
        )
        store.saveSettings(s)
        assertEquals(s, MacStore(dir).loadSettings())
    }

    @Test fun eventsRoundTripAndCompact() {
        val dir = Files.createTempDirectory("ist").toFile()
        val store = MacStore(dir)
        val d = LocalDate.now()
        val ev = listOf(
            LogEvent(d, Prayer.FAJR, PrayerStatus.ALONE, 1, "mac"),
            LogEvent(d, Prayer.FAJR, PrayerStatus.JAMAAH, 2, "phone"),
            LogEvent(d, Prayer.ASR, null, 3, "mac"),
            LogEvent(d.minusDays(500), Prayer.ISHA, PrayerStatus.QAZA, 4, "mac"),
        )
        store.saveEvents(ev)
        val back = store.loadEvents()
        assertEquals(2, back.size)
        assertTrue(back.any { it.prayer == Prayer.FAJR && it.status == PrayerStatus.JAMAAH && it.device == "phone" })
        assertTrue(back.any { it.prayer == Prayer.ASR && it.status == null })
    }

    @Test fun badFilesFallBackToDefaults() {
        val dir = Files.createTempDirectory("ist").toFile()
        java.io.File(dir, "settings.json").writeText("{ ভাঙা")
        java.io.File(dir, "events.json").writeText("[{\"d\":\"x\"}]")
        val store = MacStore(dir)
        assertEquals(MacSettings(), store.loadSettings())
        assertEquals(emptyList<LogEvent>(), store.loadEvents())
    }

    @Test fun bundledContentParses() {
        val text = javaClass.classLoader.getResourceAsStream("content.json")!!.bufferedReader().readText()
        val items = MacStore.parseContent(text)
        assertTrue(items.size > 200)
        assertTrue(items.any { "daily" in it.tags })
    }
}
