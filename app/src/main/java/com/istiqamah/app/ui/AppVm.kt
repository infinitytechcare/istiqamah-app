package com.istiqamah.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.Habit
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Planner
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.core.StageState
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime

/** হোম স্ক্রিনের এক নজরে সব তথ্য */
data class TodayState(
    val today: LocalDate,
    val windows: List<PrayerWindow>,
    val tomorrow: List<PrayerWindow>,
    val log: DayLog,
    val yesterdayPending: List<Prayer>,
    val stage: StageState,
    val streak: Int,
    val daily: ContentItem?,
    val celebration: String?,
    val unreviewed: Int,
)

class AppVm(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    val prefs = Prefs(ctx)

    var onboarded by mutableStateOf(prefs.onboarded)
        private set
    var now by mutableStateOf(ZonedDateTime.now())
        private set
    var todayState by mutableStateOf<TodayState?>(null)
        private set
    var month by mutableStateOf(YearMonth.now())
        private set
    var monthLogs by mutableStateOf<Map<LocalDate, DayLog>>(emptyMap())
        private set
    var qaza by mutableStateOf<Map<Prayer, Int>>(emptyMap())
        private set
    /** সেটিং বদলালে UI আবার পড়ার সংকেত */
    var settingsVersion by mutableStateOf(0)
        private set

    init {
        refresh()
        // ঘড়ি: প্রতি ২০ সেকেন্ডে সময় হালনাগাদ, তারিখ বদলালে পুরো রিফ্রেশ
        viewModelScope.launch {
            while (true) {
                delay(20_000)
                val n = Engine.now(ctx)
                val dayChanged = n.toLocalDate() != now.toLocalDate()
                now = n
                if (dayChanged) refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val (t, m, q) = withContext(Dispatchers.IO) {
                Triple(loadToday(), LogStore.get(ctx).range(month.atDay(1), month.atEndOfMonth()), Prayer.entries.associateWith { LogStore.get(ctx).qaza(it) })
            }
            now = Engine.now(ctx)
            todayState = t
            monthLogs = m
            qaza = q
        }
    }

    private fun loadToday(): TodayState? {
        if (!prefs.onboarded) return null
        Engine.evaluateStageIfNeeded(ctx)
        val today = Engine.today(ctx)
        val windows = Engine.windows(ctx, today) ?: return null
        val tomorrow = Engine.windows(ctx, today.plusDays(1)) ?: emptyList()
        val logs = Engine.logs(ctx, today.minusDays(400), today)
        val stage = prefs.stageState
        val yesterday = logs[today.minusDays(1)]
        val pending = stage.focus.filter { yesterday?.status(it) == null }.sortedBy { it.ordinal }
        return TodayState(
            today = today,
            windows = windows,
            tomorrow = tomorrow,
            log = logs[today] ?: DayLog(today, emptyMap()),
            yesterdayPending = pending,
            stage = stage,
            streak = Habit.streak(stage.focus, logs, today),
            daily = Engine.dailyItem(ctx, today),
            celebration = prefs.pendingCelebration,
            unreviewed = ContentRepo.unreviewedCount(ctx),
        )
    }

    fun nextWindow(): PrayerWindow? {
        val t = todayState ?: return null
        return Planner.next(mapOf(t.today to t.windows, t.today.plusDays(1) to t.tomorrow), now)
    }

    fun currentWindow(): PrayerWindow? = todayState?.let { Planner.current(it.windows, now) }

    fun checkIn(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Engine.checkIn(ctx, date, prayer, status) }
            refresh()
        }
    }

    fun dismissCelebration() {
        prefs.pendingCelebration = null
        refresh()
    }

    fun showMonth(m: YearMonth) {
        month = m
        refresh()
    }

    fun setQaza(p: Prayer, n: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogStore.get(ctx).setQaza(p, n) }
            refresh()
        }
    }

    /** অনবোর্ডিং শেষ */
    fun finishOnboarding(place: String, loc: Location, avgDaily: Int, first: Prayer, intensity: Intensity) {
        prefs.placeName = place
        prefs.location = loc
        prefs.baselineDaily = avgDaily
        prefs.stageState = Habit.initialStage(avgDaily, first, LocalDate.now(loc.zone))
        prefs.intensity = intensity
        prefs.lastStageCheck = LocalDate.now(loc.zone) // প্রথম দিনেই মূল্যায়ন নয়
        prefs.onboarded = true
        onboarded = true
        settingsChanged()
    }

    /** যেকোনো সেটিং বদলের পর: অ্যালার্ম নতুন করে, UI রিফ্রেশ */
    fun settingsChanged() {
        settingsVersion++
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Engine.reschedule(ctx) }
            refresh()
        }
    }

    fun onResume() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Engine.reschedule(ctx) }
            refresh()
        }
    }
}
