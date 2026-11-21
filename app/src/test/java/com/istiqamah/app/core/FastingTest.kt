package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class FastingTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val loc = Location(23.8103, 90.4125, zone)
    private fun windows(d: LocalDate) =
        PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, loc), PrayerTimesCalculator.compute(d.plusDays(1), loc))

    private val wed = LocalDate.of(2026, 11, 18)
    private val thu = wed.plusDays(1)

    @Test fun hijriCalendarIsAvailable() {
        val h = Hijri.of(LocalDate.of(2026, 3, 1))!!
        // ২০২৬-এর মার্চের শুরু = রমজান ১৪৪৭ (উম্মুল কুরা)
        assertEquals(1447, h.year)
        assertEquals(9, h.month)
        assertEquals(h.day + 1, Hijri.of(LocalDate.of(2026, 3, 1), adjustDays = 1)!!.day)
    }

    @Test fun naflReasonsAndForbiddenDays() {
        val mon = LocalDate.of(2026, 11, 16)
        assertEquals(listOf(NaflFast.MON_THU), Fasting.naflReasons(mon, HijriDay(1448, 6, 5)))
        assertTrue(NaflFast.AYYAM_BID in Fasting.naflReasons(wed, HijriDay(1448, 6, 14)))
        assertEquals(listOf(NaflFast.ARAFAH), Fasting.naflReasons(wed, HijriDay(1448, 12, 9)))
        assertEquals(listOf(NaflFast.ASHURA), Fasting.naflReasons(wed, HijriDay(1448, 1, 10)))
        // ঈদ ও তাশরিকে কিছুই নয় — সোমবার হলেও
        assertTrue(Fasting.naflReasons(mon, HijriDay(1448, 10, 1)).isEmpty())
        assertTrue(Fasting.naflReasons(mon, HijriDay(1448, 12, 13)).isEmpty())
        // রমজানে নফল নয়
        assertTrue(Fasting.naflReasons(mon, HijriDay(1448, 9, 14)).isEmpty())
        // শাওয়ালের ছয়টা হয়ে গেলে আর নয়
        assertTrue(NaflFast.SHAWWAL in Fasting.naflReasons(wed, HijriDay(1448, 10, 5), shawwalDone = 2))
        assertFalse(NaflFast.SHAWWAL in Fasting.naflReasons(wed, HijriDay(1448, 10, 5), shawwalDone = 6))
    }

    @Test fun qadrNightsAreOddNightsOfLastTen() {
        // ২০ তারিখের সন্ধ্যা = ২১তম রাত
        assertTrue(Fasting.isOddQadrNightTonight(HijriDay(1448, 9, 20)))
        assertTrue(Fasting.isOddQadrNightTonight(HijriDay(1448, 9, 26)))
        assertFalse(Fasting.isOddQadrNightTonight(HijriDay(1448, 9, 21)))
        assertFalse(Fasting.isOddQadrNightTonight(HijriDay(1448, 9, 18)))
        assertFalse(Fasting.isOddQadrNightTonight(HijriDay(1448, 8, 20)))
    }

    @Test fun khatmPlan() {
        assertEquals(21, Fasting.khatmPagesPerDay(1, 604, 30)) // ৬০৪/৩০ = ২০.১ → ২১
        assertEquals(10, Fasting.khatmPagesPerDay(505, 604, 10))
        assertEquals(30, Fasting.ramadanDaysLeft(HijriDay(1448, 9, 1)))
        assertEquals(0, Fasting.ramadanDaysLeft(HijriDay(1448, 8, 29)))
    }

    private fun input(
        d: LocalDate, h: Map<LocalDate, HijriDay>, fasting: Boolean = true, routine: Set<NotifKind> = setOf(NotifKind.FAST_EVE),
        logged: Set<LocalDate> = emptySet(), paused: LocalDate? = null,
    ) = PlannerInput(
        d.atStartOfDay(zone), mapOf(d to windows(d)), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
        extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, routine = routine, fasting = fasting),
        hijri = h, fastLogged = logged, pausedFrom = paused,
    )

    @Test fun ramadanDayReminders() {
        val h = mapOf(wed to HijriDay(1448, 9, 20), thu to HijriDay(1448, 9, 21))
        val plan = Planner.fastingFor(wed, windows(wed), input(wed, h)).associateBy { it.kind }
        val w = windows(wed).associateBy { it.prayer }
        assertEquals(45, Duration.between(plan.getValue(NotifKind.SUHOOR).fireAt, w.getValue(Prayer.FAJR).start).toMinutes())
        assertEquals(10, Duration.between(plan.getValue(NotifKind.SUHOOR_END).fireAt, w.getValue(Prayer.FAJR).start).toMinutes())
        assertEquals(10, Duration.between(plan.getValue(NotifKind.IFTAR).fireAt, w.getValue(Prayer.MAGHRIB).start).toMinutes())
        assertNotNull(plan[NotifKind.QADR_NIGHT]) // ২১তম রাত
        assertTrue(NotifKind.FAST_EVE !in plan) // রমজানে নফল নয়
        // ইফতারের আগে রোজা লেখা হয়ে গেলে ইফতারের ডাক নেই
        assertTrue(Planner.fastingFor(wed, windows(wed), input(wed, h, logged = setOf(wed))).none { it.kind == NotifKind.IFTAR })
        // বিরতিতে কিছুই নয়
        assertTrue(Planner.fastingFor(wed, windows(wed), input(wed, h, paused = wed)).isEmpty())
        // বন্ধ থাকলে কিছুই নয়
        assertTrue(Planner.fastingFor(wed, windows(wed), input(wed, h, fasting = false, routine = emptySet())).isEmpty())
    }

    @Test fun ramadanEveAndNaflEve() {
        val eve = Planner.fastingFor(wed, windows(wed), input(wed, mapOf(wed to HijriDay(1448, 8, 29), thu to HijriDay(1448, 9, 1))))
        assertTrue(eve.any { it.kind == NotifKind.RAMADAN_EVE })
        assertTrue(eve.none { it.kind == NotifKind.FAST_EVE })
        // বুধবার রাত → বৃহস্পতিবার সুন্নত রোজা
        val nafl = Planner.fastingFor(wed, windows(wed), input(wed, mapOf(wed to HijriDay(1448, 6, 2), thu to HijriDay(1448, 6, 3))))
        assertTrue(nafl.any { it.kind == NotifKind.FAST_EVE })
        // কাল ঈদ — বৃহস্পতিবার হলেও নয়
        val eid = Planner.fastingFor(wed, windows(wed), input(wed, mapOf(wed to HijriDay(1448, 12, 9), thu to HijriDay(1448, 12, 10))))
        assertTrue(eid.none { it.kind == NotifKind.FAST_EVE })
        // হিজরি না জানলে শুধু সোম/বৃহস্পতি
        assertTrue(Planner.fastingFor(wed, windows(wed), input(wed, emptyMap())).any { it.kind == NotifKind.FAST_EVE })
    }
}
