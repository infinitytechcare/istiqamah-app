package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ImanTest {
    private fun asset(name: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            listOf("app/src/main/assets/$name", "src/main/assets/$name").map { File(dir, it) }.firstOrNull { it.exists() }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("$name পাওয়া যায়নি")
    }

    /** quran.json থেকে প্রতিটি সূরার আয়াত সংখ্যা */
    private val ayahCounts: List<Int> by lazy {
        val text = asset("quran.json")
        val sIdx = text.indexOf("\"surahs\":[") + 10
        val sEnd = text.indexOf("]]", sIdx)
        Regex("\\[(\\d+),\\s*(\\d+),").findAll(text.substring(sIdx, sEnd + 1)).map { it.groupValues[2].toInt() }.toList()
    }

    private val contentIds: Set<String> by lazy {
        Regex("\"id\":\\s*\"([a-z0-9_]+)\"").findAll(asset("content.json")).map { it.groupValues[1] }.toSet()
    }

    private fun valid(r: AyahRef) = r.surah in 1..114 && r.from >= 1 && r.to >= r.from && r.to <= ayahCounts[r.surah - 1]

    @Test fun sixPillarsInOrderOfHadithJibril() {
        assertEquals(114, ayahCounts.size)
        assertEquals(listOf("allah", "malaika", "kutub", "rusul", "akhirah", "qadr"), Iman.LESSONS.map { it.key })
        assertEquals((1..6).toList(), Iman.LESSONS.map { it.no })
    }

    @Test fun lessonsReferToRealAyatAndHadith() {
        for (l in Iman.LESSONS) {
            assertTrue(l.key, l.ayat.isNotEmpty() && l.ayat.all(::valid))
            assertTrue(l.key, l.hadith.isNotEmpty())
            l.hadith.forEach { assertTrue("${l.key}: $it নেই", it in contentIds) }
            assertEquals(l.key, 3, l.quiz.size)
            l.quiz.forEach { q ->
                assertTrue(q.q, q.answer in q.options.indices)
                assertEquals(q.q, q.options.size, q.options.toSet().size)
            }
        }
        // সব উত্তর একই জায়গায় নয়
        assertTrue(Iman.LESSONS.flatMap { it.quiz }.map { it.answer }.toSet().size >= 3)
    }

    @Test fun passOnlyWithAllCorrect() {
        val l = Iman.LESSONS[0]
        val right = l.quiz.map { it.answer }
        assertTrue(Iman.passed(l, right))
        assertFalse(Iman.passed(l, right.dropLast(1) + null))
        assertFalse(Iman.passed(l, right.dropLast(1)))
        assertFalse(Iman.passed(l, listOf((right[0] + 1) % 3) + right.drop(1)))
        assertEquals("allah", Iman.next(emptySet())?.key)
        assertEquals("kutub", Iman.next(setOf("allah", "malaika"))?.key)
        assertNull(Iman.next(Iman.LESSONS.map { it.key }.toSet()))
    }

    @Test fun akhirahCardsBalanceFearWithHope() {
        val keys = Akhirah.CARDS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (c in Akhirah.CARDS) {
            c.ayah?.let { assertTrue(c.key, valid(it)) }
            c.hopeAyah?.let { assertTrue(c.key, valid(it)) }
            c.hadith?.let { assertTrue("${c.key}: $it নেই", it in contentIds) }
            assertTrue(c.key, c.hope.isNotBlank() && c.action.isNotBlank())
        }
        // শেষ কার্ড তাওবার দরজা — আশা দিয়ে শেষ
        assertEquals("tawbah", keys.last())
        val d = LocalDate.of(2026, 11, 21)
        assertEquals((Akhirah.forDay(d) + 1) % Akhirah.CARDS.size, Akhirah.forDay(d.plusDays(1)))
        assertTrue(Akhirah.forDay(LocalDate.of(1960, 1, 1)) in Akhirah.CARDS.indices)
    }

    @Test fun refLabelInBangla() {
        assertEquals("৫৭:২২–২৩", AyahRef(57, 22, 23).bn)
        assertEquals("১০৮:১", AyahRef(108, 1).bn)
    }

    // ---------- আমলের পূর্ণতা ----------

    private fun input(
        salah: Double = 1.0, quran: Int? = 30, azkar: Int = 30, muh: Int = 30, fasts: Int? = 3, sadaqah: Int? = 4, iman: Int = 6,
    ) = WholenessInput(30, salah, quran, azkar, muh, fasts, sadaqah, iman)

    @Test fun fullMarksWhenEverythingDone() {
        val r = Wholeness.of(input())
        assertEquals(100, r.overall)
        assertNull(r.weakest)
        assertEquals("চমৎকার", Wholeness.label(r.overall))
    }

    @Test fun disabledFeaturesAreLeftOut() {
        val r = Wholeness.of(input(quran = null, fasts = null, sadaqah = null))
        assertEquals(listOf(AmalArea.SALAH, AmalArea.AZKAR, AmalArea.MUHASABA, AmalArea.IMAN), r.areas.map { it.area })
        assertEquals(100, r.overall)
    }

    @Test fun weakestPointsToSmallNextStep() {
        val r = Wholeness.of(input(azkar = 15, muh = 3, iman = 2))
        assertEquals(AmalArea.MUHASABA, r.weakest?.area)
        assertEquals(10, r.areas.first { it.area == AmalArea.MUHASABA }.percent)
        // নামাজ অর্ধেকের নিচে হলে নামাজই আগে
        assertEquals(AmalArea.SALAH, Wholeness.of(input(salah = 0.3, muh = 0)).weakest?.area)
        // অতিরিক্ত রোজা ১০০% ছাড়ায় না
        assertEquals(100, Wholeness.of(input(fasts = 9)).areas.first { it.area == AmalArea.FASTING }.percent)
    }

    @Test fun salahCarriesFortyPercent() {
        // নামাজ শূন্য, বাকি সব পূর্ণ → ৬০
        assertEquals(60, Wholeness.of(input(salah = 0.0)).overall)
        // নামাজ পূর্ণ, বাকি সব শূন্য → ৪০
        assertEquals(40, Wholeness.of(input(quran = 0, azkar = 0, muh = 0, fasts = 0, sadaqah = 0, iman = 0)).overall)
    }

    // ---------- মুসহাফের লাইন ও ফন্টের কোড ----------

    @Test fun mushafLinesAreGreedyAndComplete() {
        val w = listOf(30f, 30f, 30f, 30f, 30f)
        assertEquals(listOf(0..2, 3..4), MushafLayout.breakLines(w, 100f, 5f))
        // খুব চওড়া শব্দ একাই এক লাইনে
        assertEquals(listOf(0..0, 1..1, 2..2), MushafLayout.breakLines(listOf(30f, 150f, 30f), 100f, 5f))
        assertEquals(emptyList<IntRange>(), MushafLayout.breakLines(emptyList(), 100f, 5f))
        // সব শব্দ ঠিক একবার
        val many = List(200) { (it % 7 + 1) * 11f }
        val lines = MushafLayout.breakLines(many, 300f, 6f)
        assertEquals((0 until 200).toList(), lines.flatMap { it.toList() })
        lines.dropLast(1).forEach { r -> assertTrue(r.sumOf { many[it].toDouble() } + 6 * (r.count() - 1) <= 300.0) }
    }

    @Test fun openTanweenMappedForFont() {
        assertEquals("هُدًࣰى", QuranScript.forFont("هُدًٗى"))
        assertEquals("غِشَٰوَةࣱ", QuranScript.forFont("غِشَٰوَةٞ"))
        assertEquals("ظُلُمَٰتࣲ", QuranScript.forFont("ظُلُمَٰتٖ"))
        val plain = "بِسۡمِ ٱللَّهِ"
        assertTrue(plain === QuranScript.forFont(plain))
        assertEquals("١٢٣", QuranScript.digits(123))
    }
}
