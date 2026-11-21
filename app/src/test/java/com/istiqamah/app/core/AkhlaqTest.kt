package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class AkhlaqTest {
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

    @Test fun sixAreasWithRealSources() {
        assertEquals(listOf("parents", "kin", "neighbor", "tongue", "debt", "work"), Akhlaq.AREAS.map { it.key })
        for (a in Akhlaq.AREAS) {
            assertTrue(a.key, a.ayat.all { it.surah in 1..114 && it.from >= 1 && it.to <= ayahCounts[it.surah - 1] })
            (a.hadith).forEach { assertTrue("${a.key}: $it নেই", it in contentIds) }
            assertTrue(a.key, a.actions.size >= 4 && a.check.isNotBlank())
        }
        Akhlaq.INTRO_HADITH.forEach { assertTrue(it, it in contentIds) }
    }

    @Test fun dailyActionRotatesThroughAreas() {
        val d = LocalDate.of(2026, 11, 22)
        val seen = (0 until 6).map { Akhlaq.actionFor(d.plusDays(it.toLong())).first.key }.toSet()
        assertEquals(6, seen.size)
        // সব কাজই কোনো না কোনো দিন আসে
        val all = (0 until 400).map { Akhlaq.actionFor(d.plusDays(it.toLong())).second }.toSet()
        assertEquals(Akhlaq.ALL_ACTIONS.map { it.second }.toSet(), all)
    }

    @Test fun weeklyCheckPercent() {
        assertNull(Akhlaq.weekPercent(emptyMap()))
        assertEquals(100, Akhlaq.weekPercent(Akhlaq.AREAS.associate { it.key to 2 }))
        assertEquals(50, Akhlaq.weekPercent(mapOf("parents" to 2, "kin" to 0)))
        assertEquals(75, Akhlaq.weekPercent(mapOf("parents" to 2, "kin" to 1, "unknown" to 0)))
        assertEquals(75, Akhlaq.average(listOf(mapOf("parents" to 2), emptyMap(), mapOf("kin" to 1))))
        assertNull(Akhlaq.average(listOf(emptyMap())))
    }

    @Test fun debtsDueDatesNeverIncludeLentOrDone() {
        val t = LocalDate.of(2026, 11, 21)
        val e = listOf(
            DebtEntry(1, DebtKind.I_OWE, "ক", 5000, "", t.plusDays(3), false),
            DebtEntry(2, DebtKind.OWED_TO_ME, "খ", 2000, "", t.plusDays(1), false),
            DebtEntry(3, DebtKind.AMANAH, "গ", 0, "ঘড়ি", t.minusDays(2), false),
            DebtEntry(4, DebtKind.I_OWE, "ঘ", 100, "", t, true),
        )
        assertEquals(setOf(t.plusDays(3), t.minusDays(2)), Akhlaq.dueDates(e))
        assertEquals(listOf(3L), Akhlaq.overdue(e, t).map { it.id })
        assertEquals(5000L, Akhlaq.openTotal(e, DebtKind.I_OWE))
        assertEquals("৩ দিন বাকি", Akhlaq.dueLabel(t.plusDays(3), t))
        assertEquals("আজ", Akhlaq.dueLabel(t, t))
        assertEquals("২ দিন পেরিয়েছে", Akhlaq.dueLabel(t.minusDays(2), t))
    }

    @Test fun kinDayAndDebtReminders() {
        val zone = ZoneId.of("Asia/Dhaka")
        val loc = Location(23.8103, 90.4125, zone)
        val fri = LocalDate.of(2026, 11, 20)
        val ws = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(fri, loc), PrayerTimesCalculator.compute(fri.plusDays(1), loc))
        fun input(kin: DayOfWeek?, due: Set<LocalDate>) = PlannerInput(
            fri.atStartOfDay(zone), mapOf(fri to ws), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, kinDay = kin, debtDue = due),
        )
        val a = Planner.akhlaqFor(fri, ws, input(DayOfWeek.FRIDAY, setOf(fri.plusDays(3))))
        assertEquals(setOf(NotifKind.KIN_DAY, NotifKind.DEBT_DUE), a.map { it.kind }.toSet())
        val maghrib = ws.first { it.prayer == Prayer.MAGHRIB }.start
        assertEquals(maghrib.plusMinutes(40), a.first { it.kind == NotifKind.KIN_DAY }.fireAt)
        assertTrue(Planner.akhlaqFor(fri, ws, input(DayOfWeek.MONDAY, setOf(fri.plusDays(2)))).isEmpty())
        assertEquals(listOf(NotifKind.DEBT_DUE), Planner.akhlaqFor(fri, ws, input(null, setOf(fri))).map { it.kind })
        // নোটিফিকেশন আইডির নিয়ম: ধরনের সূচি ১০০-র নিচে
        assertTrue(NotifKind.entries.size < 100)
    }
}
