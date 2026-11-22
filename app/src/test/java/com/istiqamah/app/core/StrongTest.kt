package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** v0.15: কড়া মাত্রা — শুরু থেকে প্রতি ১০ মিনিটে, জামাতের প্রস্তুতির আগে থেকে, "এখনই যাচ্ছি"-তে চুপ */
class StrongTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private val d = LocalDate.of(2026, 11, 22) // রবিবার
    private val ws = mapOf(
        d to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka)),
    )
    private val asr = ws.getValue(d).first { it.prayer == Prayer.ASR }
    private val jamSettings = JamaatSettings()
    private val jam = Jamaat.times(ws, Gender.MALE, jamSettings, WorkSchedule())

    private fun input(now: java.time.ZonedDateTime, men: Boolean = true, going: Map<Pair<LocalDate, Prayer>, java.time.ZonedDateTime> = emptyMap()) =
        PlannerInput(
            now, ws, Intensity.STRONG, Prayer.entries.toSet(), emptySet(), emptySet(),
            ExtraReminders(null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false),
            jamaat = if (men) jam else emptyMap(),
            leads = if (men) Prayer.entries.associateWith { jamSettings.lead(it).toLong() } else emptyMap(),
            goingUntil = going,
        )

    @Test fun defaultLeadsAsrShortOthersLonger() {
        assertEquals(5, JamaatSettings().lead(Prayer.ASR))
        assertTrue(Prayer.entries.filter { it != Prayer.ASR }.all { JamaatSettings().lead(it) > 5 })
        // পুরনো সেটিংস (leads খালি) — আগের এক মান
        assertEquals(15, JamaatSettings(leads = emptyMap()).lead(Prayer.FAJR))
    }

    @Test fun menStartBeforeJamaatByPrayerLead() {
        val p = Planner.plan(input(d.atStartOfDay(zone)))
        val n1 = p.first { it.prayer == Prayer.ASR && it.kind == NotifKind.N1_INVITE }
        val j = jam.getValue(d to Prayer.ASR).at
        assertEquals(5, Duration.between(n1.fireAt, j).toMinutes())
    }

    @Test fun womenStartAtWaqtStartAndRepeatEveryTen() {
        val p = Planner.plan(input(d.atStartOfDay(zone), men = false))
        assertEquals(asr.start, p.first { it.prayer == Prayer.ASR && it.kind == NotifKind.N1_INVITE }.fireAt)
        // N1_LOSS ১০ মিনিটে, তাই প্রথম নাছোড় ডাক ২০ মিনিটে
        val first = p.first { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL }
        assertEquals(20, Duration.between(asr.start, first.fireAt).toMinutes())
        // বাজার পরে আবার হিসাব → পরেরটা আরও ১০ মিনিট পরে
        val next = Planner.plan(input(first.fireAt.plusSeconds(5), men = false)).first { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL }
        assertEquals(10, Duration.between(first.fireAt, next.fireAt).toMinutes())
    }

    @Test fun repeatsStopBeforeLastCall() {
        val n3 = asr.end.minusMinutes(Planner.LAST_CALL_BEFORE_END_MIN)
        val late = Planner.plan(input(n3.minusMinutes(3), men = false))
        assertTrue(late.none { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL })
        assertTrue(late.any { it.prayer == Prayer.ASR && it.kind == NotifKind.N3_LAST_CALL })
    }

    @Test fun goingSilencesRepeats() {
        val t = asr.start.plusMinutes(21)
        val quiet = t.plusMinutes(12)
        val p = Planner.plan(input(t, men = false, going = mapOf((d to Prayer.ASR) to quiet)))
        val next = p.first { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL }
        assertTrue(next.fireAt.isAfter(quiet))
    }

    @Test fun nextStrongCallNoneAfterWindow() {
        val n1 = asr.start
        assertNull(Planner.nextStrongCall(n1, n1.plusMinutes(12), emptyList(), n1))
        assertEquals(n1.plusMinutes(10), Planner.nextStrongCall(n1, n1.plusMinutes(30), emptyList(), n1))
    }

    @Test fun kindOrdinalStaysSmall() {
        assertTrue(NotifKind.STRONG_CALL.ordinal < 100)
        assertTrue(NotifKind.STRONG_CALL.isPrayer)
    }
}
