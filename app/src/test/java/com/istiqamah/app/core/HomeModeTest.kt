package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class HomeModeTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private fun windows(d: LocalDate) =
        PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka))

    private val sunday = LocalDate.of(2026, 11, 15) // অফিসের দিন
    private val friday = LocalDate.of(2026, 11, 13) // ছুটি

    @Test fun workTimeFollowsDaysAndHours() {
        val s = WorkSchedule()
        assertTrue(s.isWorkTime(sunday.atTime(13, 0).atZone(zone)))
        assertFalse(s.isWorkTime(sunday.atTime(17, 30).atZone(zone)))
        assertFalse(s.isWorkTime(sunday.atTime(5, 0).atZone(zone)))
        assertFalse(s.isWorkTime(friday.atTime(13, 0).atZone(zone)))
        assertFalse(s.copy(enabled = false).isWorkTime(sunday.atTime(13, 0).atZone(zone)))
        // রাতের শিফট
        val night = WorkSchedule(days = DayOfWeek.entries.toSet(), start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))
        assertTrue(night.isWorkTime(sunday.atTime(23, 0).atZone(zone)))
        assertTrue(night.isWorkTime(sunday.atTime(5, 0).atZone(zone)))
        assertFalse(night.isWorkTime(sunday.atTime(12, 0).atZone(zone)))
    }

    @Test fun officeDayOnlyNonOfficePrayersAreHome() {
        val home = Situation.homePrayers(mapOf(sunday to windows(sunday)), HomeMode())
        val prayers = home.map { it.second }.toSet()
        // যোহর-আসর অফিসে; ফজর, মাগরিব, ইশা ঘরে (নভেম্বরে মাগরিব ~৫:১৫ — ৫টার পরে, তাই ঘরে)
        assertFalse(Prayer.DHUHR in prayers)
        assertTrue(Prayer.FAJR in prayers && Prayer.ISHA in prayers)
    }

    @Test fun holidayAndOverridesWork() {
        val fri = Situation.homePrayers(mapOf(friday to windows(friday)), HomeMode())
        assertEquals(5, fri.size)
        val out = HomeMode(overrides = mapOf(friday to DayOverride.OUT))
        assertTrue(Situation.homePrayers(mapOf(friday to windows(friday)), out).isEmpty())
        val inn = HomeMode(overrides = mapOf(sunday to DayOverride.HOME))
        assertEquals(5, Situation.homePrayers(mapOf(sunday to windows(sunday)), inn).size)
        assertTrue(Situation.homePrayers(mapOf(friday to windows(friday)), HomeMode(enabled = false)).isEmpty())
    }

    private fun input(d: LocalDate, home: Set<Pair<LocalDate, Prayer>>, logged: Set<Pair<LocalDate, Prayer>> = emptySet()) =
        PlannerInput(
            d.atStartOfDay(zone), mapOf(d to windows(d)), Intensity.LIGHT,
            focus = emptySet(), habitFormed = emptySet(), logged = logged,
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false),
            home = home,
        )

    @Test fun homePrayerGetsFullCallEvenOnLightIntensity() {
        val home = setOf(friday to Prayer.ISHA)
        val plan = Planner.plan(input(friday, home))
        val isha = plan.filter { it.prayer == Prayer.ISHA }.map { it.kind }
        assertEquals(
            listOf(NotifKind.N1_INVITE, NotifKind.N1_LOSS, NotifKind.HOME_NUDGE_1, NotifKind.HOME_NUDGE_2, NotifKind.HOME_NUDGE_3, NotifKind.N3_LAST_CALL),
            isha,
        )
        // বাকি ওয়াক্ত হালকা: শুধু N1
        assertEquals(listOf(NotifKind.N1_INVITE), plan.filter { it.prayer == Prayer.DHUHR }.map { it.kind })
    }

    @Test fun nudgesAreEvery15MinutesAndKeepGapBeforeLastCall() {
        for (p in Prayer.entries) {
            val plan = Planner.plan(input(friday, setOf(friday to p))).filter { it.prayer == p }
            val n1 = plan.first { it.kind == NotifKind.N1_INVITE }.fireAt
            val n3 = plan.firstOrNull { it.kind == NotifKind.N3_LAST_CALL }?.fireAt
            val nudges = plan.filter { it.kind.isHomeNudge }
            nudges.forEachIndexed { i, n ->
                assertEquals(Planner.FOLLOW_UP_MIN + Situation.NUDGE_EVERY_MIN * (i + 1), Duration.between(n1, n.fireAt).toMinutes())
                if (n3 != null) assertTrue("${p.bn}: শেষ সতর্কতার অন্তত ১০ মিনিট আগে", Duration.between(n.fireAt, n3).toMinutes() >= 10)
            }
            // আইডি অনন্য
            assertEquals(plan.size, plan.map { it.id }.toSet().size)
        }
    }

    @Test fun loggedHomePrayerIsSilent() {
        val plan = Planner.plan(input(friday, setOf(friday to Prayer.ISHA), logged = setOf(friday to Prayer.ISHA)))
        assertTrue(plan.none { it.prayer == Prayer.ISHA })
    }

    @Test fun dailyReportAfterIshaBeforeQuietHours() {
        val w = windows(friday)
        assertNull(Planner.reportFor(friday, w, ExtraReminders()))
        val r = Planner.reportFor(friday, w, ExtraReminders(dailyReport = true))!!
        val isha = w.first { it.prayer == Prayer.ISHA }
        assertTrue(r.fireAt.isAfter(isha.start) && r.fireAt.isBefore(isha.end))
        assertFalse(r.kind.isPrayer)
    }

    @Test fun sadaqahCountsMissedAndQazaSinceStart() {
        val d1 = friday.minusDays(1)
        val logs = mapOf(
            friday.minusDays(5) to DayLog(friday.minusDays(5), mapOf(Prayer.FAJR to PrayerStatus.MISSED)), // শুরুর আগে
            d1 to DayLog(d1, mapOf(Prayer.FAJR to PrayerStatus.QAZA, Prayer.ASR to PrayerStatus.MISSED, Prayer.ISHA to PrayerStatus.JAMAAH)),
            friday to DayLog(friday, mapOf(Prayer.DHUHR to PrayerStatus.EXCUSED)),
        )
        val s = Sadaqah.status(logs, from = d1, today = friday, perMiss = 20, paid = 15)
        assertEquals(2, s.misses)
        assertEquals(40, s.pledged)
        assertEquals(25, s.due)
        assertEquals(0, Sadaqah.status(logs, d1, friday, 20, paid = 100).due)
    }

    @Test fun reportHasOnlyPrayers() {
        val log = DayLog(friday, mapOf(Prayer.FAJR to PrayerStatus.JAMAAH, Prayer.DHUHR to PrayerStatus.QAZA, Prayer.ASR to PrayerStatus.MISSED))
        val t = Report.daily(friday, log, "আমিন")
        assertTrue(t.contains("আমিন"))
        assertTrue(t.contains("সময়মতো ১/৫"))
        assertTrue(t.contains("✗"))
        assertFalse(t.contains("গুনাহ"))
    }

    @Test fun easeKeysAreWellFormed() {
        assertEquals(8, EaseKeys.ALL.size)
        val ids = EaseKeys.ALL.flatMap { it.contentIds }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(EaseKeys.ALL.all { it.contentIds.isNotEmpty() && it.practice.isNotBlank() })
        assertTrue(ids.contains("q_2_45_46"))
    }

    @Test fun microStepRotates() {
        assertEquals(Situation.MICRO_STEPS.size, (0 until Situation.MICRO_STEPS.size).map { Situation.microStep(it) }.toSet().size)
        assertEquals(Situation.microStep(-1), Situation.MICRO_STEPS.last())
    }
}
