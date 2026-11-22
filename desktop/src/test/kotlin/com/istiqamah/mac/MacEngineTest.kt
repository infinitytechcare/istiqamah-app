package com.istiqamah.mac

import com.istiqamah.app.core.Gender
import com.istiqamah.app.core.LogEvent
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.SyncLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MacEngineTest {
    private val zone = ZoneId.of("Asia/Dhaka")
    private val day = LocalDate.of(2026, 11, 22) // রবিবার — অফিসের দিন
    private val men = MacSettings(gender = Gender.MALE)

    @Test fun windowsCoverYesterdayTodayTomorrow() {
        val ws = MacEngine.windowsByDate(men, day, zone)
        assertEquals(listOf(day.minusDays(1), day, day.plusDays(1)), ws.keys.sorted())
        // ইশা ইসলামি মধ্যরাতে শেষ (মাগরিব থেকে ফজরের অর্ধেক) — রাত ১১টায় চলমান, রাত ১টায় নয়
        assertEquals(Prayer.ISHA, MacEngine.current(ws, day.minusDays(1).atTime(23, 0).atZone(zone))?.prayer)
        assertNull(MacEngine.current(ws, day.atTime(1, 0).atZone(zone)))
        assertEquals(Prayer.FAJR, MacEngine.current(ws, day.atTime(5, 15).atZone(zone))?.prayer)
    }

    @Test fun officeDhuhrJamaatOnWorkday() {
        val ws = MacEngine.windowsByDate(men, day, zone)
        val j = MacEngine.jamaat(men, ws)[day to Prayer.DHUHR]!!
        assertEquals(LocalTime.of(13, 15), j.at.toLocalTime())
        assertTrue(j.office)
        // শুক্রবারে অফিস নেই — মসজিদের ১:৩০
        val fri = LocalDate.of(2026, 11, 27)
        val wf = MacEngine.windowsByDate(men, fri, zone)
        assertEquals(LocalTime.of(13, 30), MacEngine.jamaat(men, wf)[fri to Prayer.DHUHR]!!.at.toLocalTime())
    }

    @Test fun planOnlyPrayerCallsAndSkipsLogged() {
        val now = day.atTime(12, 0).atZone(zone)
        val plan = MacEngine.plan(men, now, emptySet())
        assertTrue(plan.isNotEmpty() && plan.all { it.kind.isPrayer && it.prayer != null })
        val dhuhrN1 = plan.first { it.date == day && it.prayer == Prayer.DHUHR && it.kind == NotifKind.N1_INVITE }
        assertEquals(LocalTime.of(13, 0), dhuhrN1.fireAt.toLocalTime()) // অফিস জামাত ১:১৫ − ১৫ মিনিট
        val after = MacEngine.plan(men, now, setOf(day to Prayer.DHUHR))
        assertTrue(after.none { it.date == day && it.prayer == Prayer.DHUHR })
    }

    @Test fun dueSkipsStaleAndKeepsLatestPerPrayer() {
        val from = day.atTime(12, 55).atZone(zone)
        val to = day.atTime(13, 1).atZone(zone)
        val d = MacEngine.due(men, from, to, emptySet())
        assertEquals(1, d.size)
        assertEquals(NotifKind.N1_INVITE, d.single().kind)
        // ঘুম থেকে অনেক পরে জাগলে (২ ঘণ্টা) পুরনো ডাক আসে না
        val late = MacEngine.due(men, day.atTime(12, 0).atZone(zone), day.atTime(14, 30).atZone(zone), emptySet())
        assertTrue(late.all { Duration.between(it.fireAt, day.atTime(14, 30).atZone(zone)).toMinutes() <= MacEngine.STALE_MIN })
    }

    @Test fun alertStyles() {
        assertEquals(MacAlert.FULLSCREEN, MacEngine.alertFor(men, NotifKind.N3_LAST_CALL))
        assertEquals(MacAlert.CALL, MacEngine.alertFor(men, NotifKind.N1_INVITE))
        assertEquals(MacAlert.BANNER, MacEngine.alertFor(men, NotifKind.N1_LOSS))
        assertEquals(MacAlert.CALL, MacEngine.alertFor(men.copy(fullScreenLast = false), NotifKind.N3_LAST_CALL))
        assertEquals(MacAlert.BANNER, MacEngine.alertFor(men.copy(fullScreenLast = false, callWindow = false), NotifKind.N3_LAST_CALL))
    }

    @Test fun reminderTextUsesJamaat() {
        val now = day.atTime(12, 0).atZone(zone)
        val ws = MacEngine.windowsByDate(men, day, zone)
        val n = MacEngine.plan(men, now, emptySet()).first { it.prayer == Prayer.DHUHR && it.kind == NotifKind.N1_INVITE }
        val r = MacEngine.reminder(men, n, ws, MacEngine.jamaat(men, ws))
        assertTrue(r.title, r.title.contains("জামাত") && r.title.contains("অফিসে"))
        val women = MacSettings(gender = Gender.FEMALE)
        val nw = MacEngine.plan(women, now, emptySet()).first { it.prayer == Prayer.DHUHR && it.kind == NotifKind.N1_INVITE }
        assertTrue(MacEngine.reminder(women, nw, ws, emptyMap()).title.contains("সময় হয়েছে"))
    }

    @Test fun countdownAndSummary() {
        assertEquals("২৩ মিনিট", MacEngine.countdown(Duration.ofMinutes(23)))
        assertEquals("১ ঘণ্টা ৫ মিনিট", MacEngine.countdown(Duration.ofMinutes(65)))
        assertEquals("২ ঘণ্টা", MacEngine.countdown(Duration.ofMinutes(120)))
        val ws = MacEngine.windowsByDate(men, day, zone)
        val asrTime = ws.getValue(day).first { it.prayer == Prayer.ASR }.start
        assertTrue(MacEngine.summary(ws, asrTime.plusMinutes(5), emptySet()).startsWith("আসর চলছে"))
        assertTrue(MacEngine.summary(ws, asrTime.plusMinutes(5), setOf(day to Prayer.ASR)).startsWith("পরের: মাগরিব"))
    }

    @Test fun syncLogLatestWinsAndMergeIsStable() {
        val a = LogEvent(day, Prayer.FAJR, PrayerStatus.ALONE, 1000, "mac")
        val b = LogEvent(day, Prayer.FAJR, PrayerStatus.JAMAAH, 2000, "phone")
        val del = LogEvent(day, Prayer.ASR, null, 3000, "phone")
        val asr = LogEvent(day, Prayer.ASR, PrayerStatus.QAZA, 2500, "mac")
        val logs = SyncLog.dayLogs(listOf(b, a, asr, del))
        assertEquals(PrayerStatus.JAMAAH, logs.getValue(day).status(Prayer.FAJR))
        assertNull(logs.getValue(day).status(Prayer.ASR)) // মুছে ফেলাটা পরে — চিহ্ন ফিরে আসে না
        val m1 = SyncLog.merge(listOf(a, asr), listOf(b, del, a))
        val m2 = SyncLog.merge(listOf(b, del), listOf(asr, a))
        assertEquals(m1, m2)
        assertEquals(4, m1.size)
        assertEquals(m1, SyncLog.merge(m1, m1))
        assertEquals(2, SyncLog.compact(m1).size)
        // একই মুহূর্তে দুই যন্ত্র: স্থির নিয়ম (নামের ক্রম) — দুই দিকেই একই ফল
        val x = LogEvent(day, Prayer.ISHA, PrayerStatus.ALONE, 5000, "mac")
        val y = LogEvent(day, Prayer.ISHA, PrayerStatus.JAMAAH, 5000, "phone")
        assertEquals(SyncLog.latest(listOf(x, y)), SyncLog.latest(listOf(y, x)))
    }

    @Test fun pauseOnlyForWomen() {
        val now = day.atTime(12, 0).atZone(zone)
        val w = MacSettings(gender = Gender.FEMALE, pausedFrom = day)
        assertTrue(MacEngine.plan(w, now, emptySet()).none { it.date == day })
        // পুরুষের ক্ষেত্রে ভুলে থাকা বিরতি কিছু বন্ধ করে না
        assertTrue(MacEngine.plan(men.copy(pausedFrom = day), now, emptySet()).any { it.date == day })
        assertTrue(MacEngine.CITIES.any { it.bn == "ব্রাহ্মণবাড়িয়া" } && MacEngine.CITIES.size == 64)
    }

    @Test fun contentRespectsGenderAndGainLoss() {
        fun item(id: String, vararg tags: String) = com.istiqamah.app.core.ContentItem(
            id = id, tags = tags.toList(), type = "hadith", refBn = "", surah = null, ayahFrom = null, ayahTo = null,
            arabic = "", bn = "", bnSource = "", shortBn = "", tone = "", pairWith = null, grading = null, reviewedBy = null, link = null)
        val gainT = com.istiqamah.app.core.ContentPicker.tagsFor(com.istiqamah.app.core.Amal.SALAH, Prayer.ASR, gain = true)
        val lossT = com.istiqamah.app.core.ContentPicker.tagsFor(com.istiqamah.app.core.Amal.SALAH, Prayer.ASR, gain = false)
        val items = listOf(item("g_men", gainT.first(), "men"), item("g", gainT.first()), item("l", lossT.first()))
        val forWoman = MacEngine.pickContent(items, NotifKind.N1_INVITE, Prayer.ASR, Gender.FEMALE, emptyMap(), day)
        assertTrue(forWoman?.id == "g")
        assertEquals("l", MacEngine.pickContent(items, NotifKind.N3_LAST_CALL, Prayer.ASR, Gender.MALE, emptyMap(), day)?.id)
    }

    @Test fun strongIsFullScreenFromStartAndRepeats() {
        val strong = men.copy(intensity = com.istiqamah.app.core.Intensity.STRONG)
        assertEquals(MacAlert.FULLSCREEN, MacEngine.alertFor(strong, NotifKind.N1_INVITE))
        assertEquals(MacAlert.FULLSCREEN, MacEngine.alertFor(strong, NotifKind.STRONG_CALL))
        // মাঝারিতে শেষ সতর্কতা পুরো পর্দা, শুরু কোণের জানালা
        assertEquals(MacAlert.CALL, MacEngine.alertFor(men, NotifKind.N1_INVITE))
        val ws = MacEngine.windowsByDate(strong, day, zone)
        val asr = ws.getValue(day).first { it.prayer == Prayer.ASR }
        val jam = MacEngine.jamaat(strong, ws).getValue(day to Prayer.ASR).at
        val plan = MacEngine.plan(strong, asr.start.minusMinutes(1), emptySet())
        // আসরে জামাতের ৫ মিনিট আগে প্রথম ডাক
        assertEquals(jam.minusMinutes(5), plan.first { it.prayer == Prayer.ASR && it.kind == NotifKind.N1_INVITE }.fireAt)
        val first = plan.first { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL }
        // "এখনই যাচ্ছি" — ১২ মিনিট চুপ
        val quiet = first.fireAt.plusMinutes(12)
        val after = MacEngine.plan(strong, first.fireAt.minusMinutes(1), emptySet(), mapOf((day to Prayer.ASR) to quiet))
            .first { it.prayer == Prayer.ASR && it.kind == NotifKind.STRONG_CALL }
        assertTrue(after.fireAt.isAfter(quiet))
    }
}
