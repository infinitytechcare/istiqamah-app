package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ProfileTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private fun windows(d: LocalDate) =
        PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka))

    private val sunday = LocalDate.of(2026, 11, 15) // অফিসের দিন
    private val friday = LocalDate.of(2026, 11, 13)

    @Test fun genderFiltersContent() {
        assertTrue(Gender.MALE.allows(listOf("men", "salah_virtue")))
        assertFalse(Gender.MALE.allows(listOf("women")))
        assertFalse(Gender.FEMALE.allows(listOf("men")))
        assertTrue(Gender.FEMALE.allows(listOf("women")))
        assertTrue(Gender.FEMALE.allows(listOf("salah_virtue")))
    }

    @Test fun defaultJamaatRulesMatchDescribedMasjid() {
        val ws = windows(sunday).associateBy { it.prayer }
        val s = JamaatSettings()
        val sched = WorkSchedule()
        // ফজর: সূর্যোদয়ের ৪০ মিনিট আগে (ফজরের ওয়াক্ত শেষ = সূর্যোদয়)
        val fajr = Jamaat.at(ws.getValue(Prayer.FAJR), s, sched)!!
        assertEquals(40, Duration.between(fajr.at, ws.getValue(Prayer.FAJR).end).toMinutes())
        // অফিসের দিনে যোহর: অফিসের জামাত ১:১৫
        val dhuhr = Jamaat.at(ws.getValue(Prayer.DHUHR), s, sched)!!
        assertTrue(dhuhr.office)
        assertEquals(LocalTime.of(13, 15), dhuhr.at.toLocalTime())
        // আসর আযানের ২০ মিনিট পর, ইশা ৩০ মিনিট পর
        assertEquals(20, Duration.between(ws.getValue(Prayer.ASR).start, Jamaat.at(ws.getValue(Prayer.ASR), s, sched)!!.at).toMinutes())
        assertEquals(30, Duration.between(ws.getValue(Prayer.ISHA).start, Jamaat.at(ws.getValue(Prayer.ISHA), s, sched)!!.at).toMinutes())
    }

    @Test fun holidayDhuhrUsesMasjid() {
        val w = windows(friday).first { it.prayer == Prayer.DHUHR }
        val j = Jamaat.at(w, JamaatSettings(), WorkSchedule())!!
        assertFalse(j.office)
        assertEquals(LocalTime.of(13, 30), j.at.toLocalTime())
    }

    @Test fun fixedTimeOutsideWindowIsIgnored() {
        val w = windows(sunday).first { it.prayer == Prayer.ASR }
        assertNull(JamaatRule(JamaatMode.FIXED, fixed = LocalTime.of(11, 0)).at(w))
        assertNull(JamaatRule(JamaatMode.OFF).at(w))
    }

    @Test fun womenGetNoJamaat() {
        val all = mapOf(sunday to windows(sunday))
        assertTrue(Jamaat.times(all, Gender.FEMALE, JamaatSettings(), WorkSchedule()).isEmpty())
        assertEquals(5, Jamaat.times(all, Gender.MALE, JamaatSettings(), WorkSchedule()).size)
        assertTrue(Jamaat.times(all, Gender.MALE, JamaatSettings(enabled = false), WorkSchedule()).isEmpty())
    }

    private fun input(
        d: LocalDate,
        jamaat: Map<Pair<LocalDate, Prayer>, JamaatTime> = emptyMap(),
        intensity: Intensity = Intensity.MEDIUM,
        pausedFrom: LocalDate? = null,
        silent: Boolean = false,
        now: java.time.ZonedDateTime = d.atStartOfDay(zone),
        logged: Set<Pair<LocalDate, Prayer>> = emptySet(),
    ) = PlannerInput(
        now, mapOf(d to windows(d)), intensity,
        focus = Prayer.entries.toSet(), habitFormed = emptySet(), logged = logged,
        extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false),
        jamaat = jamaat, pausedFrom = pausedFrom, silentRepeat = silent,
    )

    @Test fun menFirstCallIsLeadBeforeJamaatThenMissedCall() {
        val all = mapOf(sunday to windows(sunday))
        val jam = Jamaat.times(all, Gender.MALE, JamaatSettings(), WorkSchedule())
        val plan = Planner.plan(input(sunday, jam)).filter { it.prayer == Prayer.DHUHR }
        val j = jam.getValue(sunday to Prayer.DHUHR).at
        val n1 = plan.first { it.kind == NotifKind.N1_INVITE }.fireAt
        assertEquals(15, Duration.between(n1, j).toMinutes())
        val missed = plan.first { it.kind == NotifKind.JAMAAT_MISSED }.fireAt
        assertEquals(15, Duration.between(j, missed).toMinutes())
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test fun womenFirstCallAtStart() {
        val plan = Planner.plan(input(sunday)).filter { it.prayer == Prayer.ASR }
        val w = windows(sunday).first { it.prayer == Prayer.ASR }
        assertEquals(w.start, plan.first { it.kind == NotifKind.N1_INVITE }.fireAt)
        assertTrue(plan.none { it.kind == NotifKind.JAMAAT_MISSED })
    }

    @Test fun pauseSilencesPrayersAndAsksFromDay3() {
        val d1 = Planner.plan(input(sunday, pausedFrom = sunday))
        assertTrue(d1.none { it.kind.isPrayer })
        assertTrue(d1.none { it.kind == NotifKind.PAUSE_CHECK })
        val d3 = Planner.plan(input(sunday, pausedFrom = sunday.minusDays(2)))
        assertTrue(d3.none { it.kind.isPrayer })
        assertNotNull(d3.firstOrNull { it.kind == NotifKind.PAUSE_CHECK })
        // বিরতির আগের দিন স্বাভাবিক
        assertTrue(Planner.plan(input(sunday, pausedFrom = sunday.plusDays(1))).any { it.kind.isPrayer })
    }

    @Test fun silentRepeatIsNextTenMinuteSlotAvoidingLoudCalls() {
        val w = windows(sunday).first { it.prayer == Prayer.ISHA }
        val now = w.start.plusMinutes(23)
        val plan = Planner.plan(input(sunday, silent = true, now = now)).filter { it.prayer == Prayer.ISHA }
        val s = plan.single { it.kind == NotifKind.SILENT_REPEAT }.fireAt
        assertTrue(s.isAfter(now))
        assertEquals(0, Duration.between(w.start, s).toMinutes() % 10)
        plan.filter { it.kind != NotifKind.SILENT_REPEAT }.forEach {
            assertTrue(Math.abs(Duration.between(it.fireAt, s).toMinutes()) >= 3)
        }
        // চেক-ইন হয়ে গেলে নেই
        val done = Planner.plan(input(sunday, silent = true, now = now, logged = setOf(sunday to Prayer.ISHA)))
        assertTrue(done.none { it.prayer == Prayer.ISHA })
        // বন্ধ থাকলে নেই
        assertTrue(Planner.plan(input(sunday, now = now)).none { it.kind == NotifKind.SILENT_REPEAT })
    }

    @Test fun silentRepeatStopsBeforeEnd() {
        val w = windows(sunday).first { it.prayer == Prayer.MAGHRIB }
        assertNull(Planner.nextSilentRepeat(w, w.start, emptyList(), w.end.minusMinutes(1)))
    }
}
