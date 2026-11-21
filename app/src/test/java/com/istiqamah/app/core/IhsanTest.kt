package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IhsanTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val all12 = SunnahSlot.TWELVE.toSet()

    @Test fun twelveRakatLayoutOfTirmidhi415() {
        assertEquals(12, SunnahSlot.TWELVE.sumOf { it.rakat })
        assertEquals(listOf(2, 4, 2, 2, 2), SunnahSlot.TWELVE.map { it.rakat })
        assertEquals(12, Ihsan.rakat(all12 + SunnahSlot.WITR + SunnahSlot.TAHAJJUD))
        assertEquals(6, Ihsan.rakat(setOf(SunnahSlot.DHUHR_BEFORE, SunnahSlot.FAJR_BEFORE)))
        SunnahSlot.entries.forEach { assertEquals(it, SunnahSlot.fromCode(it.code)) }
    }

    @Test fun fullDaysPercentAndStreak() {
        val log = mapOf(
            today to setOf(SunnahSlot.FAJR_BEFORE),
            today.minusDays(1) to all12,
            today.minusDays(2) to all12,
            today.minusDays(4) to all12,
        )
        assertEquals(3, Ihsan.fullDays(log, today, 7))
        // (২ + ১২ + ১২ + ১২) / (১২ × ৭) = ৩৮/৮৪ → ৪৫%
        assertEquals(45, Ihsan.percent(log, today, 7))
        // আজ পূর্ণ না হলেও গতকাল থেকে টানা ২
        assertEquals(2, Ihsan.streak(log, today))
        assertEquals(3, Ihsan.streak(log + (today to all12), today))
        assertEquals(0, Ihsan.percent(emptyMap(), today, 30))
        assertEquals(1, Ihsan.countOf(SunnahSlot.TAHAJJUD, mapOf(today.minusDays(3) to setOf(SunnahSlot.TAHAJJUD)), today, 7))
        assertEquals(0, Ihsan.countOf(SunnahSlot.TAHAJJUD, mapOf(today.minusDays(8) to setOf(SunnahSlot.TAHAJJUD)), today, 7))
    }

    @Test fun khushuWeekAverages() {
        val r = mapOf(
            today to mapOf(Prayer.FAJR to 3, Prayer.DHUHR to 1),
            today.minusDays(2) to mapOf(Prayer.ISHA to 2),
            today.minusDays(9) to mapOf(Prayer.ISHA to 1), // সপ্তাহের বাইরে
        )
        val w = Ihsan.khushuWeek(r, today)
        assertEquals(7, w.days.size)
        assertEquals(today, w.days.last().first)
        assertEquals(2.0, w.days.last().second!!, 0.001)
        assertNull(w.days[5].second)
        assertEquals(2.0, w.average!!, 0.001)
        assertEquals(3, w.rated)
        assertNull(Ihsan.khushuWeek(emptyMap(), today).average)
    }

    @Test fun pendingKhushuIsLatestOnTimePrayer() {
        val log = DayLog(today, mapOf(Prayer.FAJR to PrayerStatus.JAMAAH, Prayer.DHUHR to PrayerStatus.ALONE, Prayer.ASR to PrayerStatus.QAZA))
        assertEquals(Prayer.DHUHR, Ihsan.pendingKhushu(log, emptySet()))
        assertEquals(Prayer.FAJR, Ihsan.pendingKhushu(log, setOf(Prayer.DHUHR)))
        assertNull(Ihsan.pendingKhushu(log, setOf(Prayer.DHUHR, Prayer.FAJR)))
        assertEquals(Khushu.GOOD, Khushu.fromScore(3))
    }

    @Test fun weekKeyChangesOnMonday() {
        // ২১ নভেম্বর ২০২৬ শনিবার; ২৩ নভেম্বর সোমবার নতুন সপ্তাহ
        assertEquals(Ihsan.weekKey(today), Ihsan.weekKey(today.plusDays(1)))
        assertNotEquals(Ihsan.weekKey(today), Ihsan.weekKey(today.plusDays(2)))
    }

    @Test fun salahTextsComplete() {
        val keys = Ihsan.SALAH_TEXTS.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(listOf("thana", "ruku", "sujud", "tashahhud", "durood", "beforesalam").all { it in keys })
        Ihsan.SALAH_TEXTS.forEach { assertTrue(it.key, it.arabic.isNotBlank() && it.pron.isNotBlank() && it.bn.isNotBlank() && it.ref.isNotBlank()) }
        assertEquals(Ihsan.SECRET_DEEDS.size, Ihsan.SECRET_DEEDS.map { it.key }.toSet().size)
    }

    @Test fun sunnahJoinsWholenessOnlyWhenUsed() {
        val base = WholenessInput(30, 1.0, null, 30, 30, null, null, 6)
        assertTrue(Wholeness.of(base).areas.none { it.area == AmalArea.SUNNAH })
        val r = Wholeness.of(base.copy(sunnahPercent = 20))
        assertEquals(AmalArea.SUNNAH, r.areas[1].area)
        assertEquals(AmalArea.SUNNAH, r.weakest?.area)
    }
}
