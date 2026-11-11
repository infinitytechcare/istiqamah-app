package com.istiqamah.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.istiqamah.app.core.Azkar
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.ContentPicker
import com.istiqamah.app.core.DayLog
import com.istiqamah.app.core.Habit
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Planner
import com.istiqamah.app.core.PlannerInput
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.PrayerTimesCalculator
import com.istiqamah.app.core.PrayerWindow
import com.istiqamah.app.data.AzkarRepo
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.notify.AlarmReceiver
import com.istiqamah.app.notify.Notifier
import com.istiqamah.app.notify.PrayerWidget
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * অ্যাপের কেন্দ্র: নামাজের সময়, লগ, ধাপ আর অ্যালার্ম শিডিউল — UI ও রিসিভার দুজনেই এটা ব্যবহার করে।
 */
object Engine {
    private const val TAG = "Istiqamah"
    const val ACTION_ALARM = "com.istiqamah.app.ALARM"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PRAYER = "prayer"
    const val EXTRA_DATE = "date"

    const val EXTRA_TEST = "test"

    private const val RC_RESCHEDULE = 1
    private const val RC_TEST = 2
    /** স্নুজের requestCode — মূল আইডির সাথে না মেলার জন্য অফসেট */
    const val SNOOZE_OFFSET = 20_000_000

    fun zone(ctx: Context): ZoneId = Prefs(ctx).location?.zone ?: ZoneId.systemDefault()
    fun now(ctx: Context): ZonedDateTime = ZonedDateTime.now(zone(ctx))
    fun today(ctx: Context): LocalDate = now(ctx).toLocalDate()

    /** একটা তারিখের ৫ ওয়াক্তের সময়সীমা; লোকেশন সেট না থাকলে null */
    fun windows(ctx: Context, date: LocalDate): List<PrayerWindow>? {
        val prefs = Prefs(ctx)
        val loc = prefs.location ?: return null
        val s = prefs.prayerSettings
        return PrayerTimesCalculator.windows(
            PrayerTimesCalculator.compute(date, loc, s),
            PrayerTimesCalculator.compute(date.plusDays(1), loc, s),
        )
    }

    fun logs(ctx: Context, from: LocalDate, to: LocalDate): Map<LocalDate, DayLog> = LogStore.get(ctx).range(from, to)

    /** ইউজার একটা ওয়াক্ত লগ করলেন (অ্যাপ বা নোটিফিকেশন থেকে) */
    fun checkIn(ctx: Context, date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        val store = LogStore.get(ctx)
        val old = store.day(date).status(prayer)
        store.setStatus(date, prayer, status)
        // কাযার হিসাব: "পড়িনি" দিলে এক বাড়ে; পরে কাযা/পড়া দিলে এক কমে
        val delta = (if (status == PrayerStatus.MISSED) 1 else 0) - (if (old == PrayerStatus.MISSED) 1 else 0)
        if (delta != 0) store.setQaza(prayer, store.qaza(prayer) + delta)
        Notifier.cancelFor(ctx, date, prayer)
        cancelSnooze(ctx, date, prayer)
        reschedule(ctx)
    }

    /** দিনে একবার ধাপ মূল্যায়ন */
    fun evaluateStageIfNeeded(ctx: Context) {
        val prefs = Prefs(ctx)
        val today = today(ctx)
        if (prefs.lastStageCheck == today) return
        val logs = logs(ctx, today.minusDays(31), today.minusDays(1))
        Habit.evaluateStage(prefs.stageState, logs, today)?.let { change ->
            prefs.stageState = change.newState
            prefs.pendingCelebration = change.messageBn
        }
        prefs.lastStageCheck = today
    }

    /**
     * আগের সব অ্যালার্ম বাতিল করে আজ ও আগামীকালের অ্যালার্ম নতুন করে বসায়।
     * ডাকা হয়: অ্যাপ খোলা, চেক-ইন, সেটিং বদল, মাঝরাত, রিবুট, সময়/টাইমজোন বদল।
     */
    fun reschedule(ctx: Context) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        // পুরনো অ্যালার্ম বাতিল
        for (idStr in prefs.scheduledIds) idStr.toIntOrNull()?.let { cancelAlarm(ctx, am, it) }
        prefs.scheduledIds = emptySet()
        if (!prefs.onboarded || prefs.location == null) {
            PrayerWidget.updateAll(ctx)
            return
        }

        evaluateStageIfNeeded(ctx)

        val now = now(ctx)
        val today = now.toLocalDate()
        val dates = listOf(today, today.plusDays(1))
        val windowsByDate = dates.associateWith { windows(ctx, it)!! }
        val recent = logs(ctx, today.minusDays(15), today.plusDays(1))
        val logged = recent.flatMap { (d, log) -> log.statuses.keys.map { d to it } }.toSet()
        val stage = prefs.stageState
        val habitFormed = Prayer.entries.filter { Habit.isHabitFormed(it, recent, today) }.toSet()

        val store = LogStore.get(ctx)
        val plan = Planner.plan(
            PlannerInput(
                now, windowsByDate, prefs.intensity, stage.focus, habitFormed, logged,
                extras = prefs.extras,
                azkarDone = store.azkarDone(today, today.plusDays(1)),
                muhasabaDone = store.muhasabaDates(today, today.plusDays(1)),
            )
        )
        val ids = HashSet<String>()
        for (p in plan) {
            setAlarm(ctx, am, p.id, p.fireAt.toInstant().toEpochMilli(), alarmIntent(ctx, p))
            ids += p.id.toString()
        }
        prefs.scheduledIds = ids

        // মাঝরাতের পরে আবার পুরো হিসাব
        val midnight = today.plusDays(1).atTime(0, 2).atZone(now.zone).toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            ctx, RC_RESCHEDULE,
            Intent(ctx, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RESCHEDULE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setAlarm(ctx, am, RC_RESCHEDULE, midnight, pi)
        Log.d(TAG, "scheduled ${plan.size} reminders")
        PrayerWidget.updateAll(ctx)
    }

    /** ১০ মিনিট পরে আবার মনে করানো */
    fun snooze(ctx: Context, date: LocalDate, prayer: Prayer, kind: NotifKind, minutes: Long = 10) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val p = PlannedNotif(date, prayer, kind, now(ctx).plusMinutes(minutes))
        val rc = SNOOZE_OFFSET + p.id
        setAlarm(ctx, am, rc, p.fireAt.toInstant().toEpochMilli(), alarmIntent(ctx, p, rc))
    }

    fun cancelSnooze(ctx: Context, date: LocalDate, prayer: Prayer) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        for (k in NotifKind.entries) cancelAlarm(ctx, am, SNOOZE_OFFSET + PlannedNotif(date, prayer, k, now(ctx)).id)
    }

    /** আজকের আয়াত: দিনে একবার বাছাই করে সংরক্ষণ — হোম ও নোটিফিকেশনে একই */
    fun dailyItem(ctx: Context, date: LocalDate = today(ctx)): ContentItem? {
        val prefs = Prefs(ctx)
        val items = ContentRepo.all(ctx)
        prefs.dailyPick?.let { (d, id) -> if (d == date) items.firstOrNull { it.id == id }?.let { return it } }
        val store = LogStore.get(ctx)
        val item = ContentPicker.pick(items, "daily", store.seenMap(), date) ?: return null
        store.markSeen(item.id, date)
        prefs.dailyPick = date to item.id
        return item
    }

    /**
     * QA-র জন্য: ১ মিনিট পরে আসল অ্যালার্ম-পথে একটা টেস্ট নোটিফিকেশন।
     * ফোন লক করে রেখে দেখুন বাজে কিনা — ব্যাটারি সেটিং ঠিক আছে কিনা বোঝা যায়।
     */
    fun scheduleTestAlarm(ctx: Context, delayMinutes: Long = 1) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val now = now(ctx)
        val prayer = windows(ctx, now.toLocalDate())?.let { Planner.current(it, now)?.prayer } ?: Prayer.MAGHRIB
        val p = PlannedNotif(now.toLocalDate(), prayer, NotifKind.N3_LAST_CALL, now.plusMinutes(delayMinutes))
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_KIND, p.kind.name)
            .putExtra(EXTRA_PRAYER, prayer.ordinal)
            .putExtra(EXTRA_DATE, p.date.toString())
            .putExtra(EXTRA_TEST, true)
        val pi = PendingIntent.getBroadcast(ctx, RC_TEST, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        setAlarm(ctx, am, RC_TEST, p.fireAt.toInstant().toEpochMilli(), pi)
    }

    // ---------- v0.2: আযকার, মুহাসাবা, গুনাহ ট্র্যাকার ----------

    /** একটা আযকারে একবার ট্যাপ। সেট শেষ হলে true (তখন রিমাইন্ডার বাতিল হয়)। */
    fun azkarTap(ctx: Context, date: LocalDate, set: AzkarSet, itemId: String, newCount: Int): Boolean {
        val store = LogStore.get(ctx)
        store.setAzkarCount(date, itemId, newCount)
        val items = AzkarRepo.forSet(ctx, set)
        val done = Azkar.isDone(items, store.azkarCounts(date))
        if (done && (date to set) !in store.azkarDone(date, date)) {
            store.markAzkarDone(date, set)
            NotificationManagerCompat.from(ctx).cancel(PlannedNotif(date, null, if (set == AzkarSet.EVENING) NotifKind.AZKAR_EVENING else NotifKind.AZKAR_MORNING, now(ctx)).id)
            reschedule(ctx)
            return true
        }
        return done
    }

    fun saveMuhasaba(ctx: Context, e: LogStore.MuhasabaEntry, slipGoalIds: Set<Long>) {
        val store = LogStore.get(ctx)
        store.saveMuhasaba(e)
        // মুহাসাবায় টিক দেওয়া গুনাহ = আজকের একটা ঘটনা (দিনে একবারই গোনা)
        val today = e.date
        val already = store.sinEvents().filter { it.at.toLocalDate() == today }.map { it.goalId }.toSet()
        for (id in slipGoalIds - already) store.addSinEvent(id, now(ctx).toLocalDateTime(), emptySet())
        NotificationManagerCompat.from(ctx).cancel(PlannedNotif(today, null, NotifKind.MUHASABA, now(ctx)).id)
        reschedule(ctx)
    }

    /** নির্দিষ্ট সময়ে অ্যালার্ম বাজার অনুমতি আছে কিনা (Android 12+) */
    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    // ---------- ভেতরের কাজ ----------

    private fun alarmIntent(ctx: Context, p: PlannedNotif, requestCode: Int = p.id): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_ALARM)
            .putExtra(EXTRA_KIND, p.kind.name)
            .putExtra(EXTRA_PRAYER, p.prayer?.ordinal ?: -1)
            .putExtra(EXTRA_DATE, p.date.toString())
        return PendingIntent.getBroadcast(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancelAlarm(ctx: Context, am: AlarmManager, requestCode: Int) {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_ALARM)
        PendingIntent.getBroadcast(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            ?.let { am.cancel(it); it.cancel() }
    }

    private fun setAlarm(ctx: Context, am: AlarmManager, rc: Int, atMillis: Long, pi: PendingIntent) {
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi) // কয়েক মিনিট দেরি হতে পারে
        } catch (e: SecurityException) {
            Log.w(TAG, "exact alarm denied for $rc, falling back", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }
}
