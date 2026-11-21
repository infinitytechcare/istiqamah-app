package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class QuranTest {
    /** assets/quran.json থেকে আসল মানচিত্র (org.json ছাড়া — শুধু সংখ্যার তালিকা পড়া) */
    private val index: QuranIndex by lazy {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        var f: File? = null
        while (dir != null && f == null) {
            listOf("app/src/main/assets/quran.json", "src/main/assets/quran.json").map { File(dir, it) }.firstOrNull { it.exists() }?.let { f = it }
            dir = dir.parentFile
        }
        val text = f!!.readText()
        fun arr(key: String): IntArray {
            val i = text.indexOf("\"$key\":[") + key.length + 4
            return text.substring(i, text.indexOf(']', i)).split(',').map { it.trim().toInt() }.toIntArray()
        }
        // surahs: [[start, count, ...], ...] — প্রতি সারির প্রথম সংখ্যা
        val sIdx = text.indexOf("\"surahs\":[") + 10
        val sEnd = text.indexOf("]]", sIdx)
        val surahStarts = Regex("\\[(\\d+),").findAll(text.substring(sIdx, sEnd + 1)).map { it.groupValues[1].toInt() }.toList().toIntArray()
        QuranIndex(surahStarts, arr("pages"), arr("juz"), arr("ruku"), arr("sajda").toSet())
    }

    @Test fun mapsMatchMadaniMushaf() {
        assertEquals(604, index.pageCount)
        assertEquals(30, index.juzCount)
        assertEquals(1, index.pageOf(1))
        assertEquals(2, index.pageOf(index.idOf(2, 1)))
        // দ্বিতীয় পারা ২:১৪২ থেকে, ২২ নম্বর পৃষ্ঠায়
        assertEquals(2, index.juzOf(index.idOf(2, 142)))
        assertEquals(22, index.pageOf(index.idOf(2, 142)))
        // ৩০ নম্বর পারা সূরা নাবা থেকে
        assertEquals(30, index.juzOf(index.idOf(78, 1)))
        assertEquals(604, index.pageOf(6236))
        assertEquals(114, index.surahOf(6236))
        assertEquals(6, index.ayahNo(6236))
        assertEquals(18, index.surahOf(index.idOf(18, 10)))
        // সিজদার আয়াত: ৭:২০৬
        assertTrue(index.idOf(7, 206) in index.sajda)
    }

    @Test fun unitsCountBoundariesCrossed() {
        val p1 = index.pageRange(1)
        assertEquals(1, index.units(p1.first, p1.last + 1, QuranUnit.PAGE))
        assertEquals(0, index.units(p1.first, p1.last, QuranUnit.PAGE)) // শেষ আয়াত বাকি
        assertEquals(7, index.units(1, 8, QuranUnit.AYAH))
        assertEquals(604, index.units(1, 6237, QuranUnit.PAGE))
        assertEquals(30, index.units(1, 6237, QuranUnit.JUZ))
        assertEquals(0, index.units(5, 5, QuranUnit.PAGE))
    }

    private val today = LocalDate.of(2026, 11, 21)

    @Test fun goalStreakAndEta() {
        val goal = QuranGoal(QuranUnit.PAGE, 2)
        fun day(n: Long, fromPage: Int) = ReadEvent(today.minusDays(n), index.pageStart(fromPage), index.pageStart(fromPage + 2))
        val events = listOf(day(3, 1), day(2, 3), day(1, 5), ReadEvent(today, index.pageStart(7), index.pageStart(8)))
        assertFalse(QuranProgress.isDone(today, events, index, goal)) // আজ মাত্র ১ পৃষ্ঠা
        assertEquals(3, QuranProgress.streak(events, index, goal, today)) // গতকাল থেকে ৩ দিন
        val more = events + ReadEvent(today, index.pageStart(8), index.pageStart(9))
        assertTrue(QuranProgress.isDone(today, more, index, goal))
        assertEquals(4, QuranProgress.streak(more, index, goal, today))
        val rate = QuranProgress.pagesPerDay(more, index, today, days = 4)
        assertEquals(2.0, rate, 0.001)
        assertEquals(298, QuranProgress.daysToKhatm(index.pageStart(9), index, rate))
        assertNull(QuranProgress.daysToKhatm(1, index, 0.0))
    }

    @Test fun khatmWrapsBookmark() {
        assertEquals(1 to true, QuranProgress.advance(6237))
        assertEquals(100 to false, QuranProgress.advance(100))
    }

    @Test fun plannerQuranReminders() {
        val zone = ZoneId.of("Asia/Dhaka")
        val loc = Location(23.8103, 90.4125, zone)
        val ws = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(today, loc), PrayerTimesCalculator.compute(today.plusDays(1), loc))
        fun input(on: Boolean, done: Set<LocalDate> = emptySet()) = PlannerInput(
            today.atStartOfDay(zone), mapOf(today to ws), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, quran = on),
            quranDone = done,
        )
        val plan = Planner.plan(input(true))
        val daily = plan.single { it.kind == NotifKind.QURAN_DAILY }
        assertEquals(15, java.time.Duration.between(daily.fireAt, ws.first { it.prayer == Prayer.FAJR }.end).toMinutes())
        assertTrue(plan.single { it.kind == NotifKind.QURAN_LOSS }.fireAt.isAfter(ws.first { it.prayer == Prayer.ISHA }.start))
        assertTrue(Planner.plan(input(true, setOf(today))).none { it.kind == NotifKind.QURAN_DAILY || it.kind == NotifKind.QURAN_LOSS })
        assertTrue(Planner.plan(input(false)).none { it.kind == NotifKind.QURAN_DAILY })
    }
}
