package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IlmTest {
    private fun asset(name: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            listOf("app/src/main/assets/$name", "src/main/assets/$name").map { File(dir, it) }.firstOrNull { it.exists() }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("$name পাওয়া যায়নি")
    }
    private val ayahCounts: List<Int> by lazy {
        val text = asset("quran.json")
        val sIdx = text.indexOf("\"surahs\":[") + 10
        val sEnd = text.indexOf("]]", sIdx)
        Regex("\\[(\\d+),\\s*(\\d+),").findAll(text.substring(sIdx, sEnd + 1)).map { it.groupValues[2].toInt() }.toList()
    }
    private val contentIds: Set<String> by lazy {
        Regex("\"id\":\\s*\"([a-z0-9_]+)\"").findAll(asset("content.json")).map { it.groupValues[1] }.toSet()
    }
    private val today = LocalDate.of(2026, 11, 21)

    @Test fun hifzItemsAreRealRanges() {
        assertEquals(Ilm.HIFZ.size, Ilm.HIFZ.map { it.key }.toSet().size)
        Ilm.HIFZ.forEach { h ->
            assertTrue(h.key, h.surah in 1..114 && h.from >= 1 && (h.to ?: ayahCounts[h.surah - 1]) <= ayahCounts[h.surah - 1])
        }
        assertEquals(37 + 6, Ilm.HIFZ.size) // আমপারা ৭৮–১১৪ আর ছয়টি বিশেষ অংশ
        assertEquals("s114", Ilm.HIFZ[6].key)
    }

    @Test fun spacedReview() {
        val s = HifzState(0, today)
        assertFalse(Ilm.isDue(s, today))
        assertTrue(Ilm.isDue(s, today.plusDays(1)))
        val s1 = Ilm.review(s, true, today.plusDays(1))
        assertEquals(1, s1.level)
        assertEquals(today.plusDays(4), Ilm.nextDue(s1)) // ৩ দিন পরে
        assertEquals(0, Ilm.review(s1, false, today.plusDays(4)).level)
        // সর্বোচ্চ ধাপ ৬০ দিন
        var x = s
        repeat(10) { x = Ilm.review(x, true, x.last.plusDays(1)) }
        assertEquals(Ilm.INTERVALS.lastIndex, x.level)
        assertEquals(60, Ilm.interval(x.level))
        val states = mapOf("a" to HifzState(0, today.minusDays(5)), "b" to HifzState(2, today.minusDays(8)), "c" to HifzState(3, today))
        assertEquals(listOf("a", "b"), Ilm.dueToday(states, today)) // a বাকি ছিল ৪ দিন আগে থেকে, b ১ দিন আগে থেকে
    }

    @Test fun learningStreak() {
        val days = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(2, Ilm.streak(days, today))
        assertEquals(3, Ilm.streak(days + today, today))
        assertEquals(0, Ilm.streak(emptySet(), today))
    }

    @Test fun sourcesExist() {
        (Ilm.ILM_AYAT + Ilm.DAWAH_AYAT).forEach { assertTrue(it.bn, it.to <= ayahCounts[it.surah - 1]) }
        (Ilm.ILM_HADITH + Ilm.DAWAH_HADITH).forEach { assertTrue(it, it in contentIds) }
        IlmSource.entries.forEach { assertEquals(it, IlmSource.fromCode(it.code)) }
        DawahStage.entries.forEach { assertEquals(it, DawahStage.fromCode(it.code)) }
    }

    @Test fun dailyIlmReminderAtChosenTime() {
        val zone = ZoneId.of("Asia/Dhaka")
        val loc = Location(23.8103, 90.4125, zone)
        val ws = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(today, loc), PrayerTimesCalculator.compute(today.plusDays(1), loc))
        val input = PlannerInput(
            today.atStartOfDay(zone), mapOf(today to ws), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, ilmAt = LocalTime.of(21, 30)),
        )
        val n = Planner.akhlaqFor(today, ws, input).single()
        assertEquals(NotifKind.ILM_DAILY, n.kind)
        assertEquals(today.atTime(21, 30).atZone(zone), n.fireAt)
    }
}
