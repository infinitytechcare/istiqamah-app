package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MasailTest {
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

    @Test fun everyGroupHasLessonsWithRealSources() {
        MasailGroup.entries.forEach { g -> assertTrue(g.title, Masail.lessons(g).isNotEmpty()) }
        assertEquals(Masail.LESSONS.size, Masail.LESSONS.map { it.key }.toSet().size)
        Masail.LESSONS.forEach { l ->
            assertTrue(l.key, l.sections.isNotEmpty() && l.sections.all { it.points.isNotEmpty() })
            l.ayat.forEach { assertTrue("${l.key} ${it.bn}", valid(it)) }
            l.hadith.forEach { assertTrue("${l.key}: $it নেই", it in contentIds) }
        }
        Masail.COMPANIONS.forEach { c ->
            c.ayah?.let { assertTrue(c.key, valid(it)) }
            c.hadith?.let { assertTrue(c.key, it in contentIds) }
        }
        assertEquals(listOf("alim", "suhbah", "masjid"), Masail.COMPANIONS.map { it.key })
    }

    @Test fun wuduHasFourFard() {
        assertEquals(4, Masail.WUDU_STEPS.count { it.fard })
    }

    @Test fun womenGroupHiddenForMen() {
        assertFalse(MasailGroup.WOMEN in Masail.groupsFor(Gender.MALE))
        assertTrue(MasailGroup.WOMEN in Masail.groupsFor(Gender.FEMALE))
        assertTrue(MasailGroup.WOMEN in Masail.groupsFor(Gender.UNSET))
    }

    @Test fun wasiyyahTextSkipsEmptyParts() {
        val t = WasiyyahDraft(debts = "ক — ৫০০০ টাকা").text("বায়েজিদ", LocalDate.of(2026, 11, 21))
        assertTrue(t.contains("আমার দেনা"))
        assertFalse(t.contains("আমার কাছে রাখা আমানত"))
        assertTrue(t.contains("উত্তরাধিকার"))
        assertTrue(t.startsWith("বিসমিল্লাহ"))
    }
}
