package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

class PrayerTimesTest {
    private val dhaka = Location(23.8103, 90.4125, ZoneId.of("Asia/Dhaka"))

    private fun near(expected: String, actual: LocalTime, tolMin: Long = 3) {
        val diff = abs(Duration.between(LocalTime.parse(expected), actual).toMinutes())
        assertTrue("expected ~$expected got $actual", diff <= tolMin)
    }

    @Test fun dhakaJuneMatchesPublishedKarachiTimes() {
        // প্রকাশিত (করাচি পদ্ধতি) ঢাকা, জুনের শেষ সপ্তাহ: ফজর ০৩:৪৪, সূর্যোদয় ০৫:১১, মাগরিব ১৮:৪৮, ইশা ২০:১৬
        val t = PrayerTimesCalculator.compute(LocalDate.of(2026, 6, 21), dhaka)
        near("03:44", t.fajr.toLocalTime())
        near("05:11", t.sunrise.toLocalTime())
        near("12:00", t.dhuhr.toLocalTime())
        near("18:48", t.maghrib.toLocalTime())
        near("20:16", t.isha.toLocalTime())
    }

    @Test fun hanafiAsrIsLaterThanShafii() {
        val d = LocalDate.of(2026, 11, 11)
        val h = PrayerTimesCalculator.compute(d, dhaka, PrayerSettings(madhab = AsrMadhab.HANAFI))
        val s = PrayerTimesCalculator.compute(d, dhaka, PrayerSettings(madhab = AsrMadhab.SHAFII))
        val gap = Duration.between(s.asr, h.asr).toMinutes()
        assertTrue("hanafi-shafii gap $gap", gap in 30..90)
    }

    @Test fun orderIsAlwaysCorrectAllYear() {
        var d = LocalDate.of(2026, 1, 1)
        while (d.year == 2026) {
            val t = PrayerTimesCalculator.compute(d, dhaka)
            val seq = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha)
            assertTrue("bad order on $d: $seq", seq.zipWithNext().all { (a, b) -> a.isBefore(b) })
            d = d.plusDays(1)
        }
    }

    @Test fun adjustmentsApply() {
        val d = LocalDate.of(2026, 11, 11)
        val base = PrayerTimesCalculator.compute(d, dhaka)
        val adj = PrayerTimesCalculator.compute(d, dhaka, PrayerSettings(adjustMinutes = listOf(2, 0, 0, 3, 0)))
        assertEquals(2, Duration.between(base.fajr, adj.fajr).toMinutes())
        assertEquals(3, Duration.between(base.maghrib, adj.maghrib).toMinutes())
    }

    @Test fun ishaWindowEndsAtIslamicMidnight() {
        val d = LocalDate.of(2026, 11, 11)
        val w = PrayerTimesCalculator.windows(
            PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka)
        )
        val isha = w.last()
        assertEquals(Prayer.ISHA, isha.prayer)
        assertTrue(isha.end.hour in 22..23 || isha.end.hour == 0)
    }
}

class HabitTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun logs(days: Int, f: (Int) -> Map<Prayer, PrayerStatus>): Map<LocalDate, DayLog> =
        (1..days).associate { i -> today.minusDays(i.toLong()).let { it to DayLog(it, f(i)) } }

    @Test fun initialStageFromOnboarding() {
        assertEquals(Stage.TAWBAH, Habit.initialStage(0, Prayer.MAGHRIB, today).stage)
        assertEquals(setOf(Prayer.MAGHRIB), Habit.initialStage(1, Prayer.MAGHRIB, today).focus)
        assertEquals(3, Habit.initialStage(3, Prayer.ASR, today).focus.size)
        assertTrue(Prayer.ASR in Habit.initialStage(3, Prayer.ASR, today).focus)
        assertEquals(Stage.IMAN, Habit.initialStage(5, Prayer.FAJR, today).stage)
    }

    @Test fun tawbahAdvancesAfterFiveOfSevenDays() {
        val st = StageState(Stage.TAWBAH, setOf(Prayer.MAGHRIB), today.minusDays(7))
        val l = logs(7) { i -> if (i <= 5) mapOf(Prayer.MAGHRIB to PrayerStatus.ALONE) else emptyMap() }
        val ch = Habit.evaluateStage(st, l, today)
        assertNotNull(ch)
        assertEquals(Stage.ISLAM, ch!!.newState.stage)
        assertEquals(setOf(Prayer.MAGHRIB, Prayer.ISHA), ch.newState.focus)
    }

    @Test fun tawbahDoesNotAdvanceWithFourDays() {
        val st = StageState(Stage.TAWBAH, setOf(Prayer.MAGHRIB), today.minusDays(7))
        val l = logs(7) { i -> if (i <= 4) mapOf(Prayer.MAGHRIB to PrayerStatus.QAZA) else emptyMap() }
        assertNull(Habit.evaluateStage(st, l, today))
    }

    @Test fun fajrIsAddedLast() {
        var focus = setOf(Prayer.MAGHRIB)
        val order = mutableListOf<Prayer>()
        while (true) { val n = Habit.nextPrayerToAdd(focus) ?: break; order += n; focus = focus + n }
        assertEquals(Prayer.FAJR, order.last())
    }

    @Test fun streakCountsQazaAndExcusedButBreaksOnMissed() {
        val focus = setOf(Prayer.MAGHRIB, Prayer.ISHA)
        val l = logs(10) { i ->
            when (i) {
                1 -> mapOf(Prayer.MAGHRIB to PrayerStatus.QAZA, Prayer.ISHA to PrayerStatus.JAMAAH)
                2 -> mapOf(Prayer.MAGHRIB to PrayerStatus.EXCUSED, Prayer.ISHA to PrayerStatus.EXCUSED)
                3 -> mapOf(Prayer.MAGHRIB to PrayerStatus.ALONE, Prayer.ISHA to PrayerStatus.ALONE)
                4 -> mapOf(Prayer.MAGHRIB to PrayerStatus.MISSED, Prayer.ISHA to PrayerStatus.ALONE)
                else -> mapOf(Prayer.MAGHRIB to PrayerStatus.ALONE, Prayer.ISHA to PrayerStatus.ALONE)
            }
        }
        // আজ এখনো কিছু লগ হয়নি — স্ট্রিক ভাঙবে না
        assertEquals(3, Habit.streak(focus, l, today))
        val withToday = l + (today to DayLog(today, mapOf(Prayer.MAGHRIB to PrayerStatus.ALONE, Prayer.ISHA to PrayerStatus.ALONE)))
        assertEquals(4, Habit.streak(focus, withToday, today))
    }

    @Test fun habitFormedNeedsFourteenOnTimeDays() {
        val good = logs(14) { mapOf(Prayer.FAJR to PrayerStatus.JAMAAH) }
        assertTrue(Habit.isHabitFormed(Prayer.FAJR, good, today))
        val oneQaza = good + (today.minusDays(3) to DayLog(today.minusDays(3), mapOf(Prayer.FAJR to PrayerStatus.QAZA)))
        assertTrue(!Habit.isHabitFormed(Prayer.FAJR, oneQaza, today))
    }
}

class PlannerTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private val d = LocalDate.of(2026, 11, 11)
    private val windows = mapOf(
        d to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka)),
    )

    private fun input(intensity: Intensity, focus: Set<Prayer> = Prayer.entries.toSet(), logged: Set<Pair<LocalDate, Prayer>> = emptySet(), habit: Set<Prayer> = emptySet()) =
        PlannerInput(d.atStartOfDay(zone), windows, intensity, focus, habit, logged,
            ExtraReminders(LocalTime.of(9, 0), azkar = false, muhasabaAt = null, jumuah = false, weekly = false))

    @Test fun strongGivesFourPerPrayerPlusAyah() {
        val p = Planner.plan(input(Intensity.STRONG))
        assertEquals(5 * 4 + 1, p.size)
        assertTrue(p.zipWithNext().all { (a, b) -> !a.fireAt.isAfter(b.fireAt) })
    }

    @Test fun mediumAndLight() {
        assertEquals(5 * 3 + 1, Planner.plan(input(Intensity.MEDIUM)).size)
        assertEquals(5 + 1, Planner.plan(input(Intensity.LIGHT)).size)
    }

    @Test fun nonFocusAndHabitFormedGetOnlyInvite() {
        val p = Planner.plan(input(Intensity.STRONG, focus = setOf(Prayer.MAGHRIB), habit = setOf(Prayer.MAGHRIB)))
        assertEquals(5 + 1, p.size)
        val p2 = Planner.plan(input(Intensity.STRONG, focus = setOf(Prayer.MAGHRIB)))
        assertEquals(4, p2.count { it.prayer == Prayer.MAGHRIB })
        assertEquals(1, p2.count { it.prayer == Prayer.FAJR })
    }

    @Test fun loggedPrayerGetsNoReminder() {
        val p = Planner.plan(input(Intensity.STRONG, logged = setOf(d to Prayer.DHUHR)))
        assertTrue(p.none { it.prayer == Prayer.DHUHR })
    }

    @Test fun lastCallIsTwentyMinutesBeforeEnd() {
        val p = Planner.plan(input(Intensity.MEDIUM))
        val asr = windows[d]!!.first { it.prayer == Prayer.ASR }
        val n3 = p.first { it.prayer == Prayer.ASR && it.kind == NotifKind.N3_LAST_CALL }
        assertEquals(20, Duration.between(n3.fireAt, asr.end).toMinutes())
    }

    @Test fun pastNotificationsAreSkipped() {
        val noon = input(Intensity.STRONG).copy(now = d.atTime(13, 0).atZone(zone))
        assertTrue(Planner.plan(noon).all { it.fireAt.isAfter(noon.now) })
    }

    @Test fun idsAreUnique() {
        val p = Planner.plan(input(Intensity.STRONG))
        assertEquals(p.size, p.map { it.id }.toSet().size)
    }
}

class ContentTest {
    private fun item(id: String, tag: String, pair: String? = null) =
        ContentItem(id, listOf(tag), "ayah", "ref", bn = "bn", bnSource = "src", shortBn = "s", tone = "gentle", pairWith = pair)

    private val items = listOf(item("a1", "warn", "hope"), item("a2", "warn", "hope"), item("h1", "hope"), item("h2", "hope"))
    private val today = LocalDate.of(2026, 11, 11)

    @Test fun avoidsRecentlyShown() {
        val shown = mapOf("a1" to today.minusDays(3))
        repeat(10) { s -> assertEquals("a2", ContentPicker.pick(items, "warn", shown, today, s)!!.id) }
    }

    @Test fun fallsBackToOldestWhenAllRecent() {
        val shown = mapOf("a1" to today.minusDays(3), "a2" to today.minusDays(10))
        assertEquals("a2", ContentPicker.pick(items, "warn", shown, today)!!.id)
    }

    @Test fun warningAlwaysComesWithHope() {
        val (main, pair) = ContentPicker.pickWithPair(items, "warn", emptyMap(), today)!!
        assertEquals("warn", main.tags.first())
        assertNotNull(pair)
        assertEquals("hope", pair!!.tags.first())
    }

    @Test fun bengaliFormatting() {
        assertEquals("১২৩", Bn.num(123))
        assertEquals("ভোর ৪:০৫", Bn.time(LocalTime.of(4, 5)))
        assertEquals("রাত ৮:১৬", Bn.time(LocalTime.of(20, 16)))
        assertEquals("১ ঘণ্টা ৩০ মিনিট", Bn.duration(90))
    }
}

class RewardThenLossTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private val d = LocalDate.of(2026, 11, 11)
    private val windows = mapOf(d to PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka)))

    @Test fun lossComesTenMinutesAfterRewardForEveryPrayer() {
        val p = Planner.plan(PlannerInput(d.atStartOfDay(zone), windows, Intensity.MEDIUM, Prayer.entries.toSet(), emptySet(), emptySet(),
            ExtraReminders(null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false)))
        for (pr in Prayer.entries) {
            val n1 = p.first { it.prayer == pr && it.kind == NotifKind.N1_INVITE }
            val loss = p.first { it.prayer == pr && it.kind == NotifKind.N1_LOSS }
            assertEquals(Planner.FOLLOW_UP_MIN, Duration.between(n1.fireAt, loss.fireAt).toMinutes())
            val n3 = p.first { it.prayer == pr && it.kind == NotifKind.N3_LAST_CALL }
            assertTrue(loss.fireAt.isBefore(n3.fireAt))
        }
    }

    @Test fun lightIntensityHasNoLossFollowUp() {
        val p = Planner.plan(PlannerInput(d.atStartOfDay(zone), windows, Intensity.LIGHT, Prayer.entries.toSet(), emptySet(), emptySet(),
            ExtraReminders(null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false)))
        assertTrue(p.none { it.kind == NotifKind.N1_LOSS })
    }

    @Test fun loggedPrayerGetsNoLossEither() {
        val p = Planner.plan(PlannerInput(d.atStartOfDay(zone), windows, Intensity.STRONG, Prayer.entries.toSet(), emptySet(), setOf(d to Prayer.ASR)))
        assertTrue(p.none { it.prayer == Prayer.ASR })
    }

    @Test fun extrasGetLossFollowUpsOutsideTheCap() {
        val p = Planner.plan(PlannerInput(d.atStartOfDay(zone), windows, Intensity.LIGHT, emptySet(), emptySet(), emptySet()))
        val primaries = p.filter { !it.kind.isPrayer && !it.kind.isLossFollowUp }
        assertEquals(Planner.MAX_EXTRA_PER_DAY, primaries.size)
        for ((k, f) in mapOf(NotifKind.AZKAR_MORNING to NotifKind.AZKAR_MORNING_LOSS, NotifKind.MUHASABA to NotifKind.MUHASABA_LOSS)) {
            val a = p.first { it.kind == k }; val b = p.first { it.kind == f }
            assertEquals(10, Duration.between(a.fireAt, b.fireAt).toMinutes())
        }
        assertTrue(p.none { it.kind.isLossFollowUp && it.kind == NotifKind.N1_LOSS })
    }

    @Test fun majorItemsComeFirstThenOthers() {
        fun item(id: String, vararg t: String) = ContentItem(id, t.toList(), "ayah", "r", bn = "b", bnSource = "s", shortBn = "x", tone = "gentle")
        val items = listOf(item("m1", "salah_virtue", "major"), item("n1", "salah_virtue"), item("n2", "salah_virtue"))
        assertEquals("m1", ContentPicker.pickMajorFirst(items, listOf("salah_virtue"), emptyMap(), d)!!.id)
        val seen = mapOf("m1" to d.minusDays(1))
        val next = ContentPicker.pickMajorFirst(items, listOf("salah_virtue"), seen, d)!!.id
        assertTrue(next == "n1" || next == "n2")
        assertEquals(listOf("salah_virtue", "gain_fajr"), ContentPicker.tagsFor(Amal.SALAH, Prayer.FAJR, gain = true))
        assertEquals(listOf("salah_warning"), ContentPicker.tagsFor(Amal.SALAH, Prayer.DHUHR, gain = false))
    }
}
