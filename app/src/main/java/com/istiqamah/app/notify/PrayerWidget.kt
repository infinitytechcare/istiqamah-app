package com.istiqamah.app.notify

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.istiqamah.app.MainActivity
import com.istiqamah.app.R
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.Habit
import com.istiqamah.app.core.Planner
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import java.time.Duration
import java.time.LocalDate
import kotlin.concurrent.thread

/**
 * হোম স্ক্রিন উইজেট: পরের/চলতি ওয়াক্ত, আজকের ৫টা দাগ, ধারা, আর অ্যাপ না খুলেই "পড়েছি ✓"।
 * হালনাগাদ হয়: চেক-ইন, প্রতিটা রিমাইন্ডার শিডিউল, মাঝরাত, আর প্রতি ৩০ মিনিটে (সিস্টেম)।
 */
class PrayerWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val ctx = context.applicationContext
        thread(name = "istiqamah-widget") {
            try { render(ctx, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private const val RC_OPEN = 40_000_000
        private const val RC_PRAYED = 40_000_001

        private val dotIds = intArrayOf(R.id.dot_0, R.id.dot_1, R.id.dot_2, R.id.dot_3, R.id.dot_4)

        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, PrayerWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun color(s: PrayerStatus?): Int = when (s) {
            PrayerStatus.JAMAAH -> 0xFF7FE0B5.toInt()
            PrayerStatus.ALONE -> 0xFFB6EBD3.toInt()
            PrayerStatus.QAZA -> 0xFFF1C76A.toInt()
            PrayerStatus.MISSED -> 0xFF8FA39A.toInt()
            PrayerStatus.EXCUSED -> 0xFFC9B8DD.toInt()
            null -> 0x66FFFFFF
        }

        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(ctx.packageName, R.layout.widget_prayer)
            val open = PendingIntent.getActivity(
                ctx, RC_OPEN,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val prefs = Prefs(ctx)
            val today = Engine.today(ctx)
            val windows = if (prefs.onboarded) Engine.windows(ctx, today) else null
            if (windows == null) {
                views.setTextViewText(R.id.w_title, "ইস্তিকামাহ")
                views.setTextViewText(R.id.w_sub, "অ্যাপ খুলে যাত্রা শুরু করুন")
                views.setViewVisibility(R.id.w_prayed, View.GONE)
                mgr.updateAppWidget(ids, views)
                return
            }
            val now = Engine.now(ctx)
            val logs = Engine.logs(ctx, today.minusDays(60), today)
            val todayLog = logs[today]
            val cur = Planner.current(windows, now)
            val friday = today.dayOfWeek == java.time.DayOfWeek.FRIDAY
            fun name(p: Prayer) = if (friday && p == Prayer.DHUHR) "জুমা/যোহর" else p.bn

            if (cur != null && todayLog?.status(cur.prayer) == null) {
                val left = Duration.between(now, cur.end).toMinutes()
                views.setTextViewText(R.id.w_title, "এখন ${name(cur.prayer)}")
                views.setTextViewText(R.id.w_sub, "শেষ ${Bn.time(cur.end.toLocalTime())} · আর ${Bn.duration(left)}")
                views.setViewVisibility(R.id.w_prayed, View.VISIBLE)
                views.setOnClickPendingIntent(R.id.w_prayed, prayedIntent(ctx, today, cur.prayer))
            } else {
                val tomorrow = Engine.windows(ctx, today.plusDays(1)) ?: emptyList()
                val next = Planner.next(mapOf(today to windows, today.plusDays(1) to tomorrow), now)
                views.setTextViewText(R.id.w_title, next?.let { "পরের: ${name(it.prayer)}" } ?: "আলহামদুলিল্লাহ")
                views.setTextViewText(R.id.w_sub, next?.let { Bn.time(it.start.toLocalTime()) } ?: "")
                views.setViewVisibility(R.id.w_prayed, View.GONE)
            }

            Prayer.entries.forEachIndexed { i, p ->
                val s = todayLog?.status(p)
                views.setTextViewText(dotIds[i], if (s == null) "○" else "●")
                views.setTextColor(dotIds[i], color(s))
            }
            val streak = Habit.streak(prefs.stageState.focus, logs, today)
            views.setTextViewText(R.id.w_streak, "ধারা ${Bn.num(streak)} দিন")
            mgr.updateAppWidget(ids, views)
        }

        private fun prayedIntent(ctx: Context, date: LocalDate, p: Prayer): PendingIntent {
            val i = Intent(ctx, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_CHECKIN)
                .putExtra(Engine.EXTRA_DATE, date.toString())
                .putExtra(Engine.EXTRA_PRAYER, p.ordinal)
                .putExtra(Engine.EXTRA_KIND, com.istiqamah.app.core.NotifKind.N1_INVITE.name)
                .putExtra(ActionReceiver.EXTRA_STATUS, PrayerStatus.ALONE.code)
            return PendingIntent.getBroadcast(ctx, RC_PRAYED, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
