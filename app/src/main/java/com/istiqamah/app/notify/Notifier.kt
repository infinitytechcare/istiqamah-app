package com.istiqamah.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.istiqamah.app.MainActivity
import com.istiqamah.app.R
import com.istiqamah.app.core.Amal
import com.istiqamah.app.core.AzkarSet
import com.istiqamah.app.core.Bn
import com.istiqamah.app.core.ContentItem
import com.istiqamah.app.core.ContentPicker
import com.istiqamah.app.core.GainLoss
import com.istiqamah.app.core.format
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.Weekly
import com.istiqamah.app.data.AzkarRepo
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.engine.Engine
import java.time.Duration
import java.time.LocalDate

object Notifier {
    const val CH_PRAYER = "prayer"
    const val CH_AYAH = "ayah"
    const val EXTRA_OPEN_CONTENT = "open_content"
    /** নোটিফিকেশন থেকে কোন স্ক্রিন খুলবে: "azkar:MORNING", "muhasaba", "weekly" */
    const val EXTRA_ROUTE = "route"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_PRAYER, "নামাজের রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "ওয়াক্ত শুরু, মাঝামাঝি ও শেষ হওয়ার আগের রিমাইন্ডার"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AYAH, "দৈনিক আয়াত", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "প্রতিদিন একটা আয়াত বা হাদিস"
            }
        )
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** অ্যালার্ম বাজলে: পরিস্থিতি যাচাই করে কন্টেন্টসহ নোটিফিকেশন দেখানো */
    fun showFor(ctx: Context, kind: NotifKind, prayer: Prayer?, date: LocalDate, isTest: Boolean = false) {
        if (!canPost(ctx)) return
        createChannels(ctx)
        val store = LogStore.get(ctx)
        val today = Engine.today(ctx)
        val items = ContentRepo.all(ctx)
        val seen = store.seenMap()

        if (!kind.isPrayer || prayer == null) {
            showExtra(ctx, kind, date)
            return
        }

        // ইতিমধ্যে লগ হয়ে গেলে আর বিরক্ত করব না
        if (!isTest && store.day(date).status(prayer) != null) return

        val window = Engine.windows(ctx, date)?.firstOrNull { it.prayer == prayer } ?: return
        val now = Engine.now(ctx)
        if (!isTest && !now.isBefore(window.end)) return // ওয়াক্ত শেষ — দেরিতে বাজা অ্যালার্ম ফেলে দিই

        // প্রতি রিমাইন্ডারে লাভ-ক্ষতির জোড়া। ফজর/আসর/ইশায় একদিন পরপর ওয়াক্তের নিজস্ব আয়াত-হাদিস।
        val salt = prayer.ordinal * 3 + kind.ordinal
        val specific = Amal.specificFor(prayer)
        val gl = ContentPicker.gainLoss(
            items,
            Amal.SALAH.gainGeneral, specific?.first,
            Amal.SALAH.lossGeneral, specific?.second,
            seen, today, salt,
            preferSpecific = (date.toEpochDay() + kind.ordinal) % 2 == 0L,
        )
        val lastCall = kind == NotifKind.N3_LAST_CALL
        val title = when (kind) {
            NotifKind.N1_INVITE -> "${prayerName(date, prayer)}-এর সময় হয়েছে"
            NotifKind.N2_VIRTUE -> "${prayerName(date, prayer)}-এর সময় চলছে"
            NotifKind.N3_LAST_CALL -> {
                val left = Duration.between(now, window.end).toMinutes().coerceAtLeast(1)
                "${prayerName(date, prayer)}-এর আর ${Bn.duration(left)} বাকি"
            }
            else -> return // নামাজের বাইরের ধরন এখানে আসে না (showExtra)
        }
        listOfNotNull(gl.gain, gl.loss).forEach { store.markSeen(it.id, today) }
        val (short, body) = gl.format(Amal.SALAH, lossFirst = lastCall)

        val extra = mutableListOf<String>()
        // আগের ওয়াক্ত লগ হয়নি এবং শেষ হয়ে গেছে → নরম কাযা রিমাইন্ডার (শুধু N1-এ)
        if (kind == NotifKind.N1_INVITE) qazaLine(ctx, date, prayer)?.let { extra += it }
        // শেষ সতর্কতায় ভয়ের সাথে আশা: এখনো সময় আছে
        if (lastCall) extra += "এখনো সময় আছে — উঠে দাঁড়ান, আল্লাহ অপেক্ষা করছেন।"

        val shortText = if (isTest) "টেস্ট: রিমাইন্ডার ঠিকমতো বাজছে ✓" else short
        val bigText = (listOf(body) + extra).filter { it.isNotBlank() }.joinToString("\n\n")
        val id = PlannedNotif(date, prayer, kind, now).id
        // ট্যাপ করলে লাভের আয়াত/হাদিসটা খোলে (না থাকলে ক্ষতিরটা)
        post(ctx, id, CH_PRAYER, title, shortText, bigText, gl.gain ?: gl.loss, prayer, date, kind)
    }

    /** শুক্রবার যোহর = জুমা */
    private fun prayerName(date: LocalDate, p: Prayer) =
        if (p == Prayer.DHUHR && date.dayOfWeek == java.time.DayOfWeek.FRIDAY) "জুমা/যোহর" else p.bn

    private fun qazaLine(ctx: Context, date: LocalDate, prayer: Prayer): String? {
        if (prayer == Prayer.FAJR) return null
        val prev = Prayer.fromOrdinal(prayer.ordinal - 1)
        if (prev !in Prefs(ctx).stageState.focus) return null
        val s = LogStore.get(ctx).day(date).status(prev)
        return if (s == null || s == PrayerStatus.MISSED) "${prev.bn} কি ছুটে গেছে? এখনই কাযা করে নিন — আল্লাহ ক্ষমাশীল।" else null
    }

    @android.annotation.SuppressLint("MissingPermission") // canPost() আগে যাচাই করা হয়, SecurityException ধরা আছে
    private fun post(
        ctx: Context, id: Int, channel: String, title: String, text: String, bigText: String,
        item: ContentItem?, prayer: Prayer?, date: LocalDate, kind: NotifKind? = null, route: String? = null,
    ) {
        val openIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        item?.let { openIntent.putExtra(EXTRA_OPEN_CONTENT, it.id) }
        route?.let { openIntent.putExtra(EXTRA_ROUTE, it) }
        val open = PendingIntent.getActivity(ctx, id, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(if (prayer != null) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(if (prayer != null) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setColor(0xFF105440.toInt())

        if (prayer != null && kind != null) {
            // ইচ্ছা করে "বন্ধ করো" বাটন নেই — শুধু পড়েছি বা একটু পরে
            b.addAction(0, "পড়েছি ✓", actionIntent(ctx, id * 10 + 1, ActionReceiver.ACTION_CHECKIN, date, prayer, kind, PrayerStatus.ALONE))
            b.addAction(0, "জামাতে পড়েছি", actionIntent(ctx, id * 10 + 2, ActionReceiver.ACTION_CHECKIN, date, prayer, kind, PrayerStatus.JAMAAH))
            b.addAction(0, "১০ মিনিট পরে", actionIntent(ctx, id * 10 + 3, ActionReceiver.ACTION_SNOOZE, date, prayer, kind, null))
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (e: SecurityException) {
            // অনুমতি নেই — কিছু করার নেই
        }
    }

    /**
     * নামাজের বাইরের রিমাইন্ডার। কাজটা ইতিমধ্যে হয়ে গেলে দেখায় না।
     * গোপনীয়তা: কোনো নোটিফিকেশনে গুনাহের নাম বা মুহাসাবার লেখা থাকে না।
     */
    private fun showExtra(ctx: Context, kind: NotifKind, date: LocalDate) {
        val store = LogStore.get(ctx)
        val id = PlannedNotif(date, null, kind, Engine.now(ctx)).id
        fun simple(title: String, body: String, route: String?) =
            post(ctx, id, CH_AYAH, title, body.lineSequence().first(), body, null, null, date, route = route)
        val items = ContentRepo.all(ctx)
        val seen = store.seenMap()
        val today = Engine.today(ctx)
        /** লাভ-ক্ষতির জোড়াসহ নোটিফিকেশন; intro থাকলে সবার শেষে এক লাইন */
        fun withGainLoss(title: String, amal: Amal, gl: GainLoss, intro: String?, route: String?) {
            listOfNotNull(gl.gain, gl.loss).forEach { store.markSeen(it.id, today) }
            val (short, body) = gl.format(amal)
            val big = listOfNotNull(body.ifBlank { null }, intro).joinToString("\n\n")
            post(ctx, id, CH_AYAH, title, short.ifBlank { intro ?: title }, big, null, null, date, route = route)
        }
        when (kind) {
            NotifKind.DAILY_AYAH -> {
                val item = Engine.dailyItem(ctx, date) ?: return
                post(ctx, id, CH_AYAH, "আজকের ${if (item.type == "hadith") "হাদিস" else "আয়াত"}", item.shortBn,
                    "${item.shortBn}\n— ${item.refBn}", item, prayer = null, date = date)
            }
            NotifKind.AZKAR_MORNING, NotifKind.AZKAR_EVENING -> {
                val set = if (kind == NotifKind.AZKAR_MORNING) AzkarSet.MORNING else AzkarSet.EVENING
                if ((date to set) in store.azkarDone(date, date)) return
                val n = AzkarRepo.forSet(ctx, set).size
                val gl = ContentPicker.gainLoss(items, Amal.DHIKR.gainGeneral, null, Amal.DHIKR.lossGeneral, null, seen, today, set.code)
                withGainLoss("${set.bn}ের সময়", Amal.DHIKR, gl, "${set.whenBn} — ${Bn.num(n)}টি যিকির, ১০–১৫ মিনিট।", "azkar:${set.name}")
            }
            NotifKind.MUHASABA -> {
                if (store.muhasaba(date) != null) return
                val gl = ContentPicker.gainLoss(items, Amal.MUHASABA.gainGeneral, null, Amal.MUHASABA.lossGeneral, null, seen, today, 7)
                withGainLoss("আজকের হিসাব — ৯০ সেকেন্ড", Amal.MUHASABA, gl, "আজ কী ভালো করলেন, কোথায় ভুল হলো, কাল কী বদলাবেন?", "muhasaba")
            }
            NotifKind.JUMUAH_EVE -> {
                // কাহফের লাভ + জুমা ছাড়ার ক্ষতি
                val gl = ContentPicker.gainLoss(items, "kahf", null, Amal.JUMUAH.lossGeneral, null, seen, today, 11)
                withGainLoss("আগামীকাল জুমা", Amal.JUMUAH, gl, "আজ মাগরিব থেকেই সূরা কাহফ শুরু করা যায়। বেশি বেশি দরুদ পড়ুন।", null)
            }
            NotifKind.JUMUAH_DAY -> {
                val gl = ContentPicker.gainLoss(items, Amal.JUMUAH.gainGeneral, null, Amal.JUMUAH.lossGeneral, null, seen, today, 12)
                withGainLoss("আজ জুমা — সপ্তাহের সেরা দিন", Amal.JUMUAH, gl, "গোসল, পরিষ্কার পোশাক, সূরা কাহফ, বেশি বেশি দরুদ — আর আগে আগে মসজিদে।", null)
            }
            NotifKind.WEEKLY_REVIEW -> {
                val logs = LogStore.get(ctx).range(date.minusDays(13), date)
                val r = Weekly.review(logs, date)
                val diff = when {
                    r.change > 0 -> "গত সপ্তাহের চেয়ে ${Bn.num(r.change)} ওয়াক্ত বেশি — আলহামদুলিল্লাহ!"
                    r.change < 0 -> "গত সপ্তাহের চেয়ে ${Bn.num(-r.change)} ওয়াক্ত কম — নতুন সপ্তাহে আবার শুরু।"
                    else -> "গত সপ্তাহের সমান।"
                }
                simple("এই সপ্তাহের হিসাব তৈরি", "এ সপ্তাহে সময়মতো ${Bn.num(r.onTimeTotal)} ওয়াক্ত। $diff\nদেখে নিন আর পরের সপ্তাহের একটা লক্ষ্য ঠিক করুন।", "weekly")
            }
            else -> Unit
        }
    }

    private fun actionIntent(
        ctx: Context, rc: Int, action: String, date: LocalDate, prayer: Prayer, kind: NotifKind, status: PrayerStatus?,
    ): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(Engine.EXTRA_DATE, date.toString())
            .putExtra(Engine.EXTRA_PRAYER, prayer.ordinal)
            .putExtra(Engine.EXTRA_KIND, kind.name)
            .putExtra(ActionReceiver.EXTRA_STATUS, status?.code ?: 0)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** একটা ওয়াক্তের সব নোটিফিকেশন সরিয়ে দেয় (চেক-ইনের পর) */
    fun cancelFor(ctx: Context, date: LocalDate, prayer: Prayer) {
        val nm = NotificationManagerCompat.from(ctx)
        for (k in NotifKind.entries) nm.cancel(PlannedNotif(date, prayer, k, Engine.now(ctx)).id)
    }
}
