package com.istiqamah.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.istiqamah.app.core.Azkar
import com.istiqamah.app.core.AzkarItem
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.Habit
import com.istiqamah.app.core.Intensity
import com.istiqamah.app.core.Location
import com.istiqamah.app.core.Planner
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.core.Progress
import com.istiqamah.app.core.SinEvent
import com.istiqamah.app.core.SinGoal
import com.istiqamah.app.core.SinStats
import com.istiqamah.app.core.Sins
import com.istiqamah.app.core.StageProgress
import com.istiqamah.app.core.StageState
import com.istiqamah.app.core.TriggerTag
import com.istiqamah.app.core.Weekly
import com.istiqamah.app.core.WeeklyReview
import com.istiqamah.app.data.AzkarRepo
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
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
    val progress: StageProgress?,
    val streak: Int,
    val daily: ContentItem?,
    val celebration: String?,
    val unreviewed: Int,
    val azkarDone: Set<AzkarSet>,
    val muhasabaDone: Boolean,
    val muhasabaStreak: Int,
    val weeklyGoal: String?,
) {
    val isFriday: Boolean get() = today.dayOfWeek == DayOfWeek.FRIDAY
    fun prayerName(p: Prayer) = if (isFriday && p == Prayer.DHUHR) "জুমা / যোহর" else p.bn
}

/** গোপন অংশ — শুধু আনলক করার পর লোড হয় */
data class PrivateState(
    val goals: List<SinGoal>,
    val stats: Map<Long, SinStats>,
    val events: List<SinEvent>,
    val muhasabaToday: LogStore.MuhasabaEntry?,
    val muhasabaRecent: List<LogStore.MuhasabaEntry>,
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

    // ---------- আযকার ----------
    var azkarCounts by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    // ---------- সাপ্তাহিক ----------
    var weekly by mutableStateOf<WeeklyReview?>(null)
        private set

    // ---------- গোপন অংশ ----------
    var privateUnlocked by mutableStateOf(false)
        private set
    var privateState by mutableStateOf<PrivateState?>(null)
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
            val store = LogStore.get(ctx)
            val (t, m, q) = withContext(Dispatchers.IO) {
                Triple(loadToday(), store.range(month.atDay(1), month.atEndOfMonth()), Prayer.entries.associateWith { store.qaza(it) })
            }
            val counts = withContext(Dispatchers.IO) { store.azkarCounts(Engine.today(ctx)) }
            now = Engine.now(ctx)
            todayState = t
            monthLogs = m
            qaza = q
            azkarCounts = counts
            if (privateUnlocked) loadPrivate()
        }
    }

    private fun loadToday(): TodayState? {
        if (!prefs.onboarded) return null
        Engine.evaluateStageIfNeeded(ctx)
        val store = LogStore.get(ctx)
        val today = Engine.today(ctx)
        val windows = Engine.windows(ctx, today) ?: return null
        val tomorrow = Engine.windows(ctx, today.plusDays(1)) ?: emptyList()
        val logs = Engine.logs(ctx, today.minusDays(400), today)
        val stage = prefs.stageState
        val yesterday = logs[today.minusDays(1)]
        val pending = stage.focus.filter { yesterday?.status(it) == null }.sortedBy { it.ordinal }
        val muhDates = store.muhasabaDates(today.minusDays(60), today)
        var muhStreak = if (today in muhDates) 1 else 0
        var d = today.minusDays(1)
        while (d in muhDates) { muhStreak++; d = d.minusDays(1) }
        // এ সপ্তাহের লক্ষ্য: বাছাইয়ের ৭ দিনের মধ্যে হলে দেখাই
        val goal = prefs.weeklyGoal?.takeIf { (on, _) -> !on.isBefore(today.minusDays(7)) }?.second
        return TodayState(
            today = today,
            windows = windows,
            tomorrow = tomorrow,
            log = logs[today] ?: DayLog(today, emptyMap()),
            yesterdayPending = pending,
            stage = stage,
            progress = Progress.of(stage, logs, today),
            streak = Habit.streak(stage.focus, logs, today),
            daily = Engine.dailyItem(ctx, today),
            celebration = prefs.pendingCelebration,
            unreviewed = ContentRepo.unreviewedCount(ctx) + AzkarRepo.all(ctx).count { it.reviewedBy == null },
            azkarDone = store.azkarDone(today, today).map { it.second }.toSet(),
            muhasabaDone = today in muhDates,
            muhasabaStreak = muhStreak,
            weeklyGoal = goal,
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

    // ---------- আযকার ----------

    fun azkarItems(set: AzkarSet): List<AzkarItem> = AzkarRepo.forSet(ctx, set)

    fun azkarCompletion(set: AzkarSet): Float = Azkar.completion(azkarItems(set), azkarCounts)

    /** একবার ট্যাপ = এক গণনা; লক্ষ্যে পৌঁছালে আর বাড়ে না। সেট শেষ হলে true। */
    fun azkarTap(item: AzkarItem, reset: Boolean = false): Boolean {
        val cur = azkarCounts[item.id] ?: 0
        val next = if (reset) 0 else minOf(cur + 1, item.count)
        if (next == cur && !reset) return false
        azkarCounts = azkarCounts + (item.id to next)
        val date = todayState?.today ?: Engine.today(ctx)
        val doneNow = Azkar.isDone(azkarItems(item.set), azkarCounts)
        viewModelScope.launch {
            val newlyDone = withContext(Dispatchers.IO) { Engine.azkarTap(ctx, date, item.set, item.id, next) }
            if (newlyDone) refresh()
        }
        return doneNow
    }

    // ---------- সাপ্তাহিক ----------

    fun loadWeekly() {
        viewModelScope.launch {
            weekly = withContext(Dispatchers.IO) {
                val store = LogStore.get(ctx)
                val today = Engine.today(ctx)
                Weekly.review(
                    store.range(today.minusDays(13), today), today,
                    store.muhasabaDates(today.minusDays(6), today),
                    store.azkarDone(today.minusDays(6), today),
                )
            }
        }
    }

    fun setWeeklyGoal(goal: String) {
        prefs.weeklyGoal = Engine.today(ctx) to goal
        refresh()
    }

    // ---------- গোপন অংশ ----------

    fun markPrivateUnlocked() {
        privateUnlocked = true
        loadPrivate()
    }

    fun lockPrivate() {
        privateUnlocked = false
        privateState = null
    }

    fun loadPrivate() {
        viewModelScope.launch {
            privateState = withContext(Dispatchers.IO) {
                val store = LogStore.get(ctx)
                val today = Engine.today(ctx)
                val goals = store.sinGoals()
                val events = store.sinEvents()
                PrivateState(
                    goals = goals,
                    stats = goals.associate { it.id to Sins.stats(it, events, today) },
                    events = events,
                    muhasabaToday = store.muhasaba(today),
                    muhasabaRecent = (1L..7L).mapNotNull { store.muhasaba(today.minusDays(it)) },
                )
            }
        }
    }

    fun addSinGoal(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogStore.get(ctx).addSinGoal(title, Engine.today(ctx)) }
            loadPrivate()
        }
    }

    fun archiveSinGoal(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogStore.get(ctx).archiveSinGoal(id) }
            loadPrivate()
        }
    }

    fun recordSlip(goalId: Long, tags: Set<TriggerTag>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogStore.get(ctx).addSinEvent(goalId, Engine.now(ctx).toLocalDateTime(), tags) }
            loadPrivate()
        }
    }

    /** মুহাসাবা সংরক্ষণ — লেখা এনক্রিপ্ট হয়, টিক দেওয়া গুনাহ আজকের ঘটনা হিসেবে যোগ হয় */
    fun saveMuhasaba(good: String, slip: String, tomorrow: String, slipGoals: Set<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Engine.saveMuhasaba(ctx, LogStore.MuhasabaEntry(Engine.today(ctx), good, slip, tomorrow), slipGoals)
            }
            refresh()
            if (privateUnlocked) loadPrivate()
        }
    }

    // ---------- অনবোর্ডিং ও সেটিং ----------

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
