package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RizqTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val sun = LocalDate.of(2026, 11, 22) // রবিবার — অফিস
    private val fri = LocalDate.of(2026, 11, 27) // শুক্রবার — ছুটি
    private val sched = WorkSchedule(enabled = true, days = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), start = LocalTime.of(9, 30), end = LocalTime.of(18, 0))
    private val rem = WorkReminders(leave = true, start = true, end = true, commuteMin = 40)

    @Test fun leaveBeforeCommutePlusReady() {
        assertEquals(LocalTime.of(8, 40), Rizq.leaveAt(sun, sched, rem, zone).toLocalTime())
    }

    @Test fun onlyWorkDaysAndNotWhenStayingHome() {
        assertEquals(listOf(NotifKind.WORK_LEAVE, NotifKind.WORK_START, NotifKind.WORK_END), Rizq.plan(sun, sched, rem, DayOverride.AUTO, zone).map { it.kind })
        assertTrue(Rizq.plan(fri, sched, rem, DayOverride.AUTO, zone).isEmpty())
        assertTrue(Rizq.plan(sun, sched, rem, DayOverride.HOME, zone).isEmpty())
        assertTrue(Rizq.plan(sun, sched, null, DayOverride.AUTO, zone).isEmpty())
        assertTrue(Rizq.plan(sun, sched.copy(enabled = false), rem, DayOverride.AUTO, zone).isEmpty())
        assertEquals(listOf(NotifKind.WORK_LEAVE), Rizq.plan(sun, sched, rem.copy(start = false, end = false), DayOverride.AUTO, zone).map { it.kind })
    }

    @Test fun plannerIncludesWorkReminders() {
        val dhaka = Location(23.8103, 90.4125, zone)
        val ws = mapOf(sun to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(sun, dhaka), PrayerTimesCalculator.compute(sun.plusDays(1), dhaka)))
        fun input(work: WorkReminders?) = PlannerInput(
            sun.atStartOfDay(zone), ws, Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            ExtraReminders(null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, work = work, workSchedule = sched),
        )
        val p = Planner.plan(input(rem))
        assertEquals(LocalTime.of(8, 40), p.first { it.kind == NotifKind.WORK_LEAVE }.fireAt.toLocalTime())
        assertTrue(Planner.plan(input(null)).none { it.kind == NotifKind.WORK_LEAVE })
        assertTrue(NotifKind.WORK_END.ordinal < 100 && !NotifKind.WORK_LEAVE.isPrayer)
        assertTrue(Rizq.PROOFS.all { it.ref.isNotBlank() })
    }
}
