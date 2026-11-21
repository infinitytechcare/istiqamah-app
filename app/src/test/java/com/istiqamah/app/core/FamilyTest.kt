package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FamilyTest {
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
    private fun valid(r: AyahRef) = r.surah in 1..114 && r.from >= 1 && r.to <= ayahCounts[r.surah - 1]
    private val today = LocalDate.of(2026, 11, 21)

    @Test fun sourcesExist() {
        (Family.SPOUSE_AYAT).forEach { assertTrue(it.bn, valid(it)) }
        (Family.SPOUSE_HADITH + Family.FAMILY_HADITH).forEach { assertTrue(it, it in contentIds) }
        Family.HALAQA.forEach { h ->
            assertTrue(h.title, valid(h.ayah) && valid(h.dua))
            assertTrue(h.hadith, h.hadith in contentIds)
        }
    }

    @Test fun childStagesCoverEveryAge() {
        (0..30).forEach { a -> assertEquals(1, Family.CHILD_STAGES.count { a in it.fromAge..it.toAge }) }
        val c = Child(1, "ক", YearMonth.of(2019, 6))
        assertEquals(7, Family.age(c, today))
        assertEquals("নামাজ শেখা (৭–৯ বছর)", Family.stageFor(Family.age(c, today)).title)
        assertTrue(Family.prays(c, today))
        assertFalse(Family.prays(Child(2, "খ", YearMonth.of(2020, 1)), today))
        // জন্মমাস এখনো আসেনি → এক বছর কম
        assertEquals(6, Family.age(Child(3, "গ", YearMonth.of(2019, 12)), today))
        assertEquals(0, Family.age(Child(4, "ঘ", YearMonth.of(2027, 1)), today))
    }

    @Test fun spouseActsByGender() {
        assertTrue(Family.spouseActFor(Gender.FEMALE, today) in Family.WIFE_ACTS)
        assertTrue(Family.spouseActFor(Gender.MALE, today) in Family.HUSBAND_ACTS)
        assertTrue(Family.spouseActFor(Gender.UNSET, today) in Family.HUSBAND_ACTS)
        assertNotEquals(Family.spouseActFor(Gender.MALE, today), Family.spouseActFor(Gender.MALE, today.plusDays(1)))
    }

    @Test fun halaqaChangesWeeklyOnMonday() {
        val sat = today // শনিবার
        assertEquals(Family.halaqaFor(sat), Family.halaqaFor(sat.plusDays(1))) // রবিবার একই সপ্তাহ
        assertNotEquals(Family.halaqaFor(sat), Family.halaqaFor(sat.plusDays(2))) // সোমবার নতুন
        val all = (0 until 8).map { Family.halaqaFor(sat.plusWeeks(it.toLong())).title }.toSet()
        assertEquals(Family.HALAQA.size, all.size)
        assertEquals(50, Family.halaqaPercent(setOf(Ihsan.weekKey(today), Ihsan.weekKey(today.minusWeeks(2))), today))
        assertEquals(0, Family.halaqaPercent(setOf(Ihsan.weekKey(today.minusWeeks(5))), today))
    }

    @Test fun childSalahAverage() {
        assertNull(Family.salahAverage(emptyMap(), today, 7))
        assertEquals(4.0, Family.salahAverage(mapOf(today to 5, today.minusDays(1) to 3, today.minusDays(9) to 0), today, 7)!!, 0.001)
    }

    @Test fun halaqaReminderAfterIsha() {
        val zone = ZoneId.of("Asia/Dhaka")
        val loc = Location(23.8103, 90.4125, zone)
        val ws = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(today, loc), PrayerTimesCalculator.compute(today.plusDays(1), loc))
        val input = PlannerInput(
            today.atStartOfDay(zone), mapOf(today to ws), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, halaqaDay = DayOfWeek.SATURDAY),
        )
        val n = Planner.akhlaqFor(today, ws, input).single()
        assertEquals(NotifKind.FAMILY_HALAQA, n.kind)
        assertEquals(ws.first { it.prayer == Prayer.ISHA }.start.plusMinutes(20), n.fireAt)
        assertTrue(Planner.akhlaqFor(today.plusDays(1), ws, input).isEmpty())
    }
}
