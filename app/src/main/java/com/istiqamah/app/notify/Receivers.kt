package com.istiqamah.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.engine.Engine
import java.time.LocalDate
import kotlin.concurrent.thread

/** রিসিভারে ডেটাবেসের কাজ মূল থ্রেডের বাইরে করি; goAsync দিয়ে সিস্টেমকে অপেক্ষা করাই */
private inline fun BroadcastReceiver.work(crossinline block: () -> Unit) {
    val pending = goAsync()
    thread(name = "istiqamah-rx") {
        try { block() } finally { pending.finish() }
    }
}

/** নির্ধারিত সময়ে বাজে: নোটিফিকেশন দেখায় বা মাঝরাতে নতুন করে শিডিউল করে */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_RESCHEDULE = "com.istiqamah.app.RESCHEDULE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        when (intent.action) {
            ACTION_RESCHEDULE -> work { Engine.reschedule(ctx) }
            Engine.ACTION_ALARM -> work {
                val kind = runCatching { NotifKind.valueOf(intent.getStringExtra(Engine.EXTRA_KIND) ?: "") }.getOrNull() ?: return@work
                val p = intent.getIntExtra(Engine.EXTRA_PRAYER, -1).takeIf { it in 0..4 }?.let(Prayer::fromOrdinal)
                val date = runCatching { LocalDate.parse(intent.getStringExtra(Engine.EXTRA_DATE)) }.getOrElse { Engine.today(ctx) }
                Notifier.showFor(ctx, kind, p, date, isTest = intent.getBooleanExtra(Engine.EXTRA_TEST, false))
            }
        }
    }
}

/** নোটিফিকেশনের বাটন: পড়েছি / জামাতে / ১০ মিনিট পরে */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CHECKIN = "com.istiqamah.app.CHECKIN"
        const val ACTION_SNOOZE = "com.istiqamah.app.SNOOZE"
        const val EXTRA_STATUS = "status"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val prayer = intent.getIntExtra(Engine.EXTRA_PRAYER, -1).takeIf { it in 0..4 }?.let(Prayer::fromOrdinal) ?: return
        val date = runCatching { LocalDate.parse(intent.getStringExtra(Engine.EXTRA_DATE)) }.getOrNull() ?: return
        val kind = runCatching { NotifKind.valueOf(intent.getStringExtra(Engine.EXTRA_KIND) ?: "") }.getOrNull() ?: NotifKind.N1_INVITE
        when (intent.action) {
            ACTION_CHECKIN -> {
                val status = PrayerStatus.fromCode(intent.getIntExtra(EXTRA_STATUS, 0)) ?: return
                work { Engine.checkIn(ctx, date, prayer, status) }
            }
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(ctx).cancel(PlannedNotif(date, prayer, kind, Engine.now(ctx)).id)
                work { Engine.snooze(ctx, date, prayer, kind) }
            }
        }
    }
}

/** রিবুট, অ্যাপ আপডেট, সময় বা টাইমজোন বদল → সব অ্যালার্ম আবার বসাই */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        work { Engine.reschedule(ctx) }
    }
}
