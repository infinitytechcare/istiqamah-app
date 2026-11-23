package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class BodyTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private val d = LocalDate.of(2026, 11, 23)
    private val ws = mapOf(
        d to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka)),
        d.plusDays(1) to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d.plusDays(1), dhaka), PrayerTimesCalculator.compute(d.plusDays(2), dhaka)),
    )
    private val fajr = ws.getValue(d).first { it.prayer == Prayer.FAJR }

    @Test fun walkAfterFajrJamaatOrStartButBeforeSunrise() {
        assertEquals(fajr.start.plusMinutes(35), Body.walkTime(fajr, null))
        val jam = fajr.start.plusMinutes(30)
        assertEquals(jam.plusMinutes(25), Body.walkTime(fajr, jam))
        // খুব দেরির জামাত — সূর্যোদয়ের ১০ মিনিট আগেই
        assertEquals(fajr.end.minusMinutes(10), Body.walkTime(fajr, fajr.end.minusMinutes(5)))
    }

    @Test fun weekAndStreak() {
        val log = mapOf(
            d to setOf(BodyAct.WALK),
            d.minusDays(1) to setOf(BodyAct.WALK, BodyAct.STRENGTH),
            d.minusDays(2) to setOf(BodyAct.WALK),
            d.minusDays(4) to setOf(BodyAct.WALK, BodyAct.STRENGTH),
            d.minusDays(8) to setOf(BodyAct.STRENGTH),
        )
        assertEquals(4, Body.daysThisWeek(log, BodyAct.WALK, d))
        assertEquals(2, Body.daysThisWeek(log, BodyAct.STRENGTH, d))
        assertEquals(3, Body.streak(log, BodyAct.WALK, d))
        // আজ না হলেও গতকাল পর্যন্ত টানা
        assertEquals(1, Body.streak(log, BodyAct.STRENGTH, d))
        assertEquals(0, Body.streak(emptyMap(), BodyAct.STAND, d))
    }

    @Test fun suggestNextLevelAfterTenStrengthDays() {
        val ten = (0L until 10L).associate { d.minusDays(it * 2) to setOf(BodyAct.STRENGTH) }
        assertEquals(FitLevel.STEADY, Body.suggestNext(ten, FitLevel.START, d))
        assertNull(Body.suggestNext(ten, FitLevel.STRONG, d))
        assertNull(Body.suggestNext(ten.entries.take(5).associate { it.key to it.value }, FitLevel.START, d))
    }

    @Test fun levelsGrowAndContentIsComplete() {
        FitLevel.entries.zipWithNext().forEach { (a, b) -> assertTrue(b.pushups > a.pushups && b.walkMin > a.walkMin) }
        assertTrue(Body.PROOFS.all { it.ref.isNotBlank() && it.text.isNotBlank() })
        assertEquals(Body.PROOFS[0], Body.proofFor(LocalDate.ofEpochDay(0)))
        assertTrue(Body.EXERCISES.all { it.amount(FitLevel.START).isNotBlank() })
    }

    @Test fun plannerSchedulesWalkOnlyWhenOn() {
        fun input(on: Boolean) = PlannerInput(
            d.atStartOfDay(zone), ws, Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            ExtraReminders(null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false, walkAfterFajr = on),
        )
        val on = Planner.plan(input(true)).filter { it.kind == NotifKind.FIT_WALK }
        assertEquals(2, on.size)
        assertEquals(fajr.start.plusMinutes(35), on.first { it.date == d }.fireAt)
        assertTrue(Planner.plan(input(false)).none { it.kind == NotifKind.FIT_WALK })
        assertTrue(NotifKind.FIT_WALK.ordinal < 100 && !NotifKind.FIT_WALK.isPrayer)
        assertTrue(Duration.between(fajr.start, on.first().fireAt).toMinutes() > 0)
    }
}
