package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ExtraRemindersTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private fun windows(d: LocalDate) =
        PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka))

    private fun input(d: LocalDate, extras: ExtraReminders = ExtraReminders(), azkarDone: Set<Pair<LocalDate, AzkarSet>> = emptySet(), muhasabaDone: Set<LocalDate> = emptySet()) =
        PlannerInput(d.atStartOfDay(zone), mapOf(d to windows(d)), Intensity.LIGHT, emptySet(), emptySet(), emptySet(), extras, azkarDone, muhasabaDone)

    private val friday = LocalDate.of(2026, 11, 13)
    private val wednesday = LocalDate.of(2026, 11, 11)

    @Test fun fridayRespectsDailyCapAndDropsAyahFirst() {
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val extras = Planner.plan(input(friday)).filter { !it.kind.isPrayer && !it.kind.isLossFollowUp }
        assertEquals(Planner.MAX_EXTRA_PER_DAY, extras.size)
        assertTrue(extras.any { it.kind == NotifKind.WEEKLY_REVIEW })
        assertTrue(extras.any { it.kind == NotifKind.JUMUAH_DAY })
        assertTrue(extras.none { it.kind == NotifKind.DAILY_AYAH })
    }

    @Test fun ordinaryDayHasAzkarMuhasabaAndAyah() {
        val kinds = Planner.plan(input(wednesday)).filter { !it.kind.isPrayer && !it.kind.isLossFollowUp }.map { it.kind }.toSet()
        assertEquals(setOf(NotifKind.DAILY_AYAH, NotifKind.AZKAR_MORNING, NotifKind.AZKAR_EVENING, NotifKind.MUHASABA), kinds)
    }

    @Test fun thursdayHasJumuahEveAfterMaghrib() {
        val thu = LocalDate.of(2026, 11, 12)
        val p = Planner.plan(input(thu)).first { it.kind == NotifKind.JUMUAH_EVE }
        val maghrib = windows(thu).first { it.prayer == Prayer.MAGHRIB }
        assertTrue(p.fireAt.isAfter(maghrib.start))
    }

    @Test fun morningAzkarIsBeforeSunrise() {
        val p = Planner.plan(input(wednesday)).first { it.kind == NotifKind.AZKAR_MORNING }
        val fajr = windows(wednesday).first { it.prayer == Prayer.FAJR }
        assertTrue(fajr.contains(p.fireAt))
    }

    @Test fun muhasabaNeverInQuietHours() {
        val late = ExtraReminders(muhasabaAt = LocalTime.of(23, 55))
        val p = Planner.plan(input(wednesday, late)).first { it.kind == NotifKind.MUHASABA }
        val isha = windows(wednesday).first { it.prayer == Prayer.ISHA }
        assertTrue(p.fireAt.isBefore(isha.end))
        val pl = Planner.plan(input(wednesday, late)).first { it.kind == NotifKind.MUHASABA_LOSS }
        assertTrue(pl.fireAt.isBefore(isha.end))
        val early = ExtraReminders(muhasabaAt = LocalTime.of(14, 0))
        val p2 = Planner.plan(input(wednesday, early)).first { it.kind == NotifKind.MUHASABA }
        assertTrue(p2.fireAt.isAfter(isha.start))
    }

    @Test fun doneThingsAreNotReminded() {
        val p = Planner.plan(input(wednesday, azkarDone = setOf(wednesday to AzkarSet.MORNING), muhasabaDone = setOf(wednesday)))
        assertTrue(p.none { it.kind == NotifKind.AZKAR_MORNING || it.kind == NotifKind.MUHASABA || it.kind == NotifKind.AZKAR_MORNING_LOSS || it.kind == NotifKind.MUHASABA_LOSS })
        assertTrue(p.any { it.kind == NotifKind.AZKAR_EVENING })
    }

    @Test fun idsUniqueAcrossTwoDaysWithEverything() {
        val inp = PlannerInput(
            friday.minusDays(1).atStartOfDay(zone),
            mapOf(friday.minusDays(1) to windows(friday.minusDays(1)), friday to windows(friday)),
            Intensity.STRONG, Prayer.entries.toSet(), emptySet(), emptySet(),
        )
        val p = Planner.plan(inp)
        assertEquals(p.size, p.map { it.id }.toSet().size)
        assertTrue(p.all { it.id in 0 until 20_000_000 })
    }
}

class ProgressTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun logs(days: Int, f: (Int) -> Map<Prayer, PrayerStatus>) =
        (1..days).associate { i -> today.minusDays(i.toLong()).let { it to DayLog(it, f(i)) } }

    @Test fun tawbahProgressCountsAndWaits() {
        val st = StageState(Stage.TAWBAH, setOf(Prayer.MAGHRIB), today.minusDays(2))
        val pr = Progress.of(st, logs(7) { i -> if (i <= 2) mapOf(Prayer.MAGHRIB to PrayerStatus.ALONE) else emptyMap() }, today)!!
        assertEquals(2, pr.current)
        assertEquals(5, pr.target)
        assertEquals(3, pr.waitDays)
        assertFalse(pr.percent)
    }

    @Test fun imanProgressIsPercent() {
        val st = StageState(Stage.IMAN, Prayer.entries.toSet(), today.minusDays(40))
        val all = Prayer.entries.associateWith { PrayerStatus.JAMAAH }
        val pr = Progress.of(st, logs(30) { all }, today)!!
        assertTrue(pr.percent)
        assertEquals(100, pr.current)
        assertEquals(0, pr.waitDays)
    }

    @Test fun lastStageHasNoProgress() {
        assertNull(Progress.of(StageState(Stage.IHSAN, Prayer.entries.toSet(), today), emptyMap(), today))
    }
}

class WeeklyTest {
    private val friday = LocalDate.of(2026, 11, 13)

    @Test fun hardestIsFajrAndChangeIsComputed() {
        val logs = (0..13).associate { i ->
            val d = friday.minusDays(i.toLong())
            val thisWeek = i <= 6
            val m = Prayer.entries.associateWith { p ->
                when {
                    p == Prayer.FAJR && thisWeek && i % 2 == 0 -> PrayerStatus.MISSED
                    p == Prayer.FAJR && !thisWeek -> PrayerStatus.MISSED
                    else -> PrayerStatus.JAMAAH
                }
            }
            d to DayLog(d, m)
        }
        val r = Weekly.review(logs, friday)
        assertEquals(Prayer.FAJR, r.hardest)
        assertEquals(3, r.onTime[Prayer.FAJR])
        assertEquals(7 * 4 + 3, r.onTimeTotal)
        assertEquals(7 * 4, r.prevOnTimeTotal)
        assertEquals(3, r.change)
        assertTrue(Weekly.suggestions(r.hardest).isNotEmpty())
    }

    @Test fun perfectWeekHasNoHardest() {
        val logs = (0..6).associate { i -> friday.minusDays(i.toLong()).let { it to DayLog(it, Prayer.entries.associateWith { PrayerStatus.ALONE }) } }
        assertNull(Weekly.review(logs, friday).hardest)
    }
}

class SinsTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val goal = SinGoal(1, "x", today.minusDays(40), true)

    @Test fun cleanAndBestDays() {
        val ev = listOf(
            SinEvent(1, today.minusDays(30).atTime(23, 30), emptySet()),
            SinEvent(1, today.minusDays(5).atTime(23, 40), emptySet()),
            SinEvent(2, today.minusDays(1).atTime(10, 0), emptySet()), // অন্য লক্ষ্য
        )
        val s = Sins.stats(goal, ev, today)
        assertEquals(5, s.cleanDays)
        assertEquals(25, s.bestCleanDays)
        assertEquals(2, s.eventsLast30)
    }

    @Test fun noPatternBeforeFiveEvents() {
        val ev = (1..4).map { SinEvent(1, today.minusDays(it.toLong()).atTime(23, 30), setOf(TriggerTag.ALONE_HOME)) }
        assertTrue(Sins.stats(goal, ev, today).insights.isEmpty())
    }

    @Test fun lateNightAloneAtHomePatternFound() {
        val ev = (1..6).map { i ->
            SinEvent(1, LocalDateTime.of(today.minusDays(i.toLong()), LocalTime.of(if (i == 6) 15 else 23, 30)), setOf(TriggerTag.ALONE_HOME, TriggerTag.TIRED))
        }
        val ins = Sins.stats(goal, ev, today).insights
        assertTrue(ins.joinToString(), ins.any { "গভীর রাতে" in it })
        assertTrue(ins.any { "বাসায় একা" in it })
    }
}

class AzkarTest {
    private fun item(id: String, n: Int) = AzkarItem(id, AzkarSet.MORNING, 0, "t", "a", "p", "m", n, "r", "g")

    @Test fun completionCapsEachItem() {
        val items = listOf(item("a", 1), item("b", 3))
        assertEquals(0.5f, Azkar.completion(items, mapOf("b" to 2)), 0.001f)
        assertEquals(1f, Azkar.completion(items, mapOf("a" to 5, "b" to 3)), 0.001f)
        assertTrue(Azkar.isDone(items, mapOf("a" to 1, "b" to 3)))
        assertFalse(Azkar.isDone(items, mapOf("a" to 1, "b" to 2)))
    }

    @Test fun suggestsMorningAfterFajrAndEveningAfterAsr() {
        val zone = ZoneId.of("Asia/Dhaka")
        val d = LocalDate.of(2026, 11, 11)
        val loc = Location(23.8103, 90.4125, zone)
        val w = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, loc), PrayerTimesCalculator.compute(d.plusDays(1), loc))
        assertEquals(AzkarSet.MORNING, Azkar.suggested(w[0].start.plusMinutes(10), w))
        assertEquals(AzkarSet.EVENING, Azkar.suggested(w[2].start.plusMinutes(10), w))
        assertEquals(AzkarSet.SLEEP, Azkar.suggested(w[4].start.plusMinutes(60), w))
        assertNotNull(Azkar.suggested(w[0].start, w))
    }
}

class GainLossTest {
    private fun item(id: String, vararg tags: String) =
        ContentItem(id, tags.toList(), "ayah", "ref-$id", bn = "bn", bnSource = "src", shortBn = "short-$id", tone = "gentle")

    private val items = listOf(
        item("g1", "salah_virtue"), item("g2", "salah_virtue"), item("gf", "gain_fajr"),
        item("l1", "salah_warning"), item("lf", "loss_fajr"),
        item("both", "salah_virtue", "salah_warning"),
    )
    private val today = LocalDate.of(2026, 11, 11)

    @Test fun gainAndLossAreNeverTheSameItem() {
        repeat(20) { s ->
            val gl = ContentPicker.gainLoss(items, "salah_virtue", null, "salah_warning", null, emptyMap(), today.plusDays(s.toLong()), s)
            assertNotNull(gl.gain); assertNotNull(gl.loss)
            assertTrue(gl.gain!!.id != gl.loss!!.id)
            assertTrue("salah_virtue" in gl.gain!!.tags)
            assertTrue("salah_warning" in gl.loss!!.tags)
        }
    }

    @Test fun preferSpecificUsesPrayerItems() {
        val (sg, sl) = Amal.specificFor(Prayer.FAJR)!!
        val gl = ContentPicker.gainLoss(items, "salah_virtue", sg, "salah_warning", sl, emptyMap(), today, 0, preferSpecific = true)
        assertEquals("gf", gl.gain!!.id)
        assertEquals("lf", gl.loss!!.id)
        assertNull(Amal.specificFor(Prayer.DHUHR))
    }

    @Test fun recentlyShownAreAvoided() {
        val shown = mapOf("g1" to today.minusDays(2), "both" to today.minusDays(2))
        repeat(10) { s ->
            val gl = ContentPicker.gainLoss(items, "salah_virtue", null, "salah_warning", null, shown, today, s)
            assertEquals("g2", gl.gain!!.id)
        }
    }

    @Test fun formatPutsLossFirstOnLastCall() {
        val gl = GainLoss(item("g", "x"), item("l", "y"))
        val (short, big) = gl.format(Amal.SALAH, lossFirst = true)
        assertTrue(short.startsWith("না পড়লে ক্ষতি"))
        assertTrue(big.indexOf("⚠️") < big.indexOf("✅"))
        val (short2, big2) = gl.format(Amal.SALAH)
        assertTrue(short2.startsWith("পড়লে লাভ"))
        assertTrue(big2.indexOf("✅") < big2.indexOf("⚠️"))
        assertTrue(big2.contains("— ref-g") && big2.contains("— ref-l"))
    }
}

class SunnahRoutineTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val dhaka = Location(23.8103, 90.4125, zone)
    private fun windows(d: LocalDate) =
        PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(d, dhaka), PrayerTimesCalculator.compute(d.plusDays(1), dhaka))
    private val all = ExtraReminders(routine = NotifKind.ROUTINE.toSet())
    private val wed = LocalDate.of(2026, 11, 11)

    @Test fun timesFollowTheDayInOrder() {
        val w = windows(wed)
        val r = Planner.routineFor(wed, w, all).associate { it.kind to it.fireAt }
        val sunrise = w[0].end
        assertTrue(r.getValue(NotifKind.DUHA).isAfter(sunrise))
        assertTrue(r.getValue(NotifKind.BREAKFAST).isAfter(r.getValue(NotifKind.DUHA)))
        assertTrue(r.getValue(NotifKind.QAILULA).isAfter(w[1].start) && r.getValue(NotifKind.QAILULA).isBefore(w[2].start))
        assertTrue(w[3].contains(r.getValue(NotifKind.DINNER)))
        assertTrue(w[4].contains(r.getValue(NotifKind.SLEEP_PREP)))
        // তাহাজ্জুদ মধ্যরাতের পরে, পরের ফজরের আগে
        val tahajjud = r.getValue(NotifKind.TAHAJJUD)
        val nextFajr = windows(wed.plusDays(1))[0].start
        assertTrue(tahajjud.isAfter(w[4].end) && tahajjud.isBefore(nextFajr))
        assertNotNull(r[NotifKind.FAST_EVE]) // বুধবার রাত → বৃহস্পতিবারের রোজা
    }

    @Test fun qailulaCanBeBeforeDhuhr() {
        val w = windows(wed)
        val q = Planner.routineFor(wed, w, all.copy(qailulaBeforeDhuhr = true)).first { it.kind == NotifKind.QAILULA }
        assertTrue(q.fireAt.isBefore(w[1].start))
    }

    @Test fun fastEveOnlySundayAndWednesday() {
        val thu = LocalDate.of(2026, 11, 12)
        assertTrue(Planner.routineFor(thu, windows(thu), all).none { it.kind == NotifKind.FAST_EVE })
        val sun = LocalDate.of(2026, 11, 15)
        assertTrue(Planner.routineFor(sun, windows(sun), all).any { it.kind == NotifKind.FAST_EVE })
    }

    @Test fun disabledItemsAreNotPlannedAndCapIsUnaffected() {
        val w = windows(wed)
        assertTrue(Planner.routineFor(wed, w, ExtraReminders()).isEmpty())
        val only = Planner.routineFor(wed, w, ExtraReminders(routine = setOf(NotifKind.QAILULA)))
        assertEquals(listOf(NotifKind.QAILULA), only.map { it.kind })
        val inp = PlannerInput(wed.atStartOfDay(zone), mapOf(wed to w), Intensity.LIGHT, emptySet(), emptySet(), emptySet(), all)
        val p = Planner.plan(inp)
        assertEquals(Planner.MAX_EXTRA_PER_DAY, p.count { !it.kind.isPrayer && !it.kind.isLossFollowUp && !it.kind.isRoutine })
        assertEquals(p.size, p.map { it.id }.toSet().size)
    }
}
