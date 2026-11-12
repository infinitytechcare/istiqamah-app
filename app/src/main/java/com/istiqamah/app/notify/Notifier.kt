package com.istiqamah.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
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
import com.istiqamah.app.core.format
import com.istiqamah.app.core.formatSingle
import com.istiqamah.app.core.NotifKind
import com.istiqamah.app.core.PlannedNotif
import com.istiqamah.app.core.Prayer
import com.istiqamah.app.core.PrayerStatus
import com.istiqamah.app.core.Weekly
import com.istiqamah.app.data.AzkarRepo
import com.istiqamah.app.data.ContentRepo
import com.istiqamah.app.data.LogStore
import com.istiqamah.app.data.Prefs
import com.istiqamah.app.data.SoundMode
import com.istiqamah.app.engine.Engine
import java.time.Duration
import java.time.LocalDate

object Notifier {
    const val CH_PRAYER = "prayer"
    const val CH_AYAH = "ayah"
    const val CH_SUNNAH = "sunnah"
    const val EXTRA_OPEN_CONTENT = "open_content"
    /** নোটিফিকেশন থেকে কোন স্ক্রিন খুলবে: "azkar:MORNING", "muhasaba", "weekly" */
    const val EXTRA_ROUTE = "route"

    /** চ্যানেলের আসল আইডি — শব্দ বদলালে সংস্করণ বাড়ে (Android 8+ এ পুরনো চ্যানেলের শব্দ বদলানো যায় না) */
    fun channelId(ctx: Context, base: String) = "${base}_v${Prefs(ctx).channelVersion}"

    /** বেছে নেওয়া শব্দের URI; নীরব হলে null */
    fun soundUri(ctx: Context, prefs: Prefs = Prefs(ctx)): Uri? = when (prefs.soundMode) {
        // R.raw সরাসরি উল্লেখ — নইলে release বিল্ডের resource shrinker ফাইলটা মুছে ফেলত
        SoundMode.APP_CHIME -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${ctx.packageName}/${R.raw.notify_chime}")
        SoundMode.PHONE_DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
        SoundMode.CUSTOM -> prefs.customSound?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI
        SoundMode.SILENT -> null
    }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val prefs = Prefs(ctx)
        val v = prefs.channelVersion
        // পুরনো সংস্করণের চ্যানেল মুছে ফেলি (v0.1/v0.2-এর "prayer", "ayah" সহ)
        nm.notificationChannels
            .filter { c -> listOf(CH_PRAYER, CH_AYAH, CH_SUNNAH).any { c.id == it || c.id.startsWith("${it}_v") } && !c.id.endsWith("_v$v") }
            .forEach { nm.deleteNotificationChannel(it.id) }
        val sound = soundUri(ctx, prefs)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun ch(base: String, name: String, importance: Int, desc: String, withSound: Boolean) =
            NotificationChannel(channelId(ctx, base), name, importance).apply {
                description = desc
                if (withSound && sound != null) setSound(sound, attrs) else setSound(null, null)
                enableVibration(prefs.vibrate)
                if (prefs.vibrate) vibrationPattern = longArrayOf(0, 220, 120, 220)
            }
        nm.createNotificationChannel(ch(CH_PRAYER, "নামাজের রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH,
            "ওয়াক্ত শুরুতে পুরস্কার, ১০ মিনিট পরে ক্ষতি, শেষের আগে সতর্কতা", withSound = true))
        nm.createNotificationChannel(ch(CH_AYAH, "আয়াত, আযকার ও মুহাসাবা", NotificationManager.IMPORTANCE_DEFAULT,
            "দৈনিক আয়াত, সকাল-সন্ধ্যার আযকার, রাতের মুহাসাবা, জুমা", prefs.soundForOthers))
        nm.createNotificationChannel(ch(CH_SUNNAH, "সুন্নত দিনলিপি", NotificationManager.IMPORTANCE_DEFAULT,
            "খাবার, কাইলুলা, ঘুম, দোহা, তাহাজ্জুদ, সুন্নত রোজা", prefs.soundForOthers))
    }

    /** শব্দ/ভাইব্রেশনের সেটিং বদলের পর — নতুন সংস্করণের চ্যানেল */
    fun applySoundSettings(ctx: Context) {
        val prefs = Prefs(ctx)
        prefs.channelVersion = prefs.channelVersion + 1
        createChannels(ctx)
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

        // v0.3: শুরুতে শুধু সবচেয়ে বড় পুরস্কার → ১০ মিনিট পরে (না পড়লে) বড় ক্ষতি → মাঝামাঝি আরেক পুরস্কার → শেষে ক্ষতি+পুরস্কার।
        // ৩০ দিনের মধ্যে একই আয়াত/হাদিস আবার আসে না; "major" আগে।
        val salt = prayer.ordinal * 3 + kind.ordinal
        val name = prayerName(date, prayer)
        val gainTags = ContentPicker.tagsFor(Amal.SALAH, prayer, gain = true)
        val lossTags = ContentPicker.tagsFor(Amal.SALAH, prayer, gain = false)
        val title: String
        val shortText: String
        val bigText: String
        val opened: ContentItem?
        when (kind) {
            NotifKind.N1_INVITE -> {
                val g = ContentPicker.pickMajorFirst(items, gainTags, seen, today, salt) ?: return
                store.markSeen(g.id, today)
                val (sh, body) = g.formatSingle(Amal.SALAH, gain = true)
                title = "$name-এর সময় হয়েছে"
                shortText = sh
                bigText = listOfNotNull(body, qazaLine(ctx, date, prayer)).joinToString("\n\n")
                opened = g
            }
            NotifKind.N1_LOSS -> {
                val l = ContentPicker.pickMajorFirst(items, lossTags, seen, today, salt) ?: return
                store.markSeen(l.id, today)
                val (sh, body) = l.formatSingle(Amal.SALAH, gain = false, hopeLine = "আল্লাহ ক্ষমাশীল — এখনো সময় আছে, এখনই উঠে দাঁড়ান।")
                title = "$name এখনো পড়া হয়নি"
                shortText = sh; bigText = body; opened = l
            }
            NotifKind.N2_VIRTUE -> {
                val g = ContentPicker.pickAny(items, gainTags, seen, today, salt) ?: return
                store.markSeen(g.id, today)
                val (sh, body) = g.formatSingle(Amal.SALAH, gain = true)
                title = "$name-এর সময় চলছে"
                shortText = sh; bigText = body; opened = g
            }
            NotifKind.N3_LAST_CALL -> {
                val specific = Amal.specificFor(prayer)
                val gl = ContentPicker.gainLoss(
                    items, Amal.SALAH.gainGeneral, specific?.first, Amal.SALAH.lossGeneral, specific?.second,
                    seen, today, salt, preferSpecific = date.toEpochDay() % 2 == 0L,
                )
                listOfNotNull(gl.gain, gl.loss).forEach { store.markSeen(it.id, today) }
                val (sh, body) = gl.format(Amal.SALAH, lossFirst = true)
                val left = Duration.between(now, window.end).toMinutes().coerceAtLeast(1)
                title = "$name-এর আর ${Bn.duration(left)} বাকি"
                shortText = sh
                bigText = body + "\n\nএখনো সময় আছে — উঠে দাঁড়ান, আল্লাহ অপেক্ষা করছেন।"
                opened = gl.loss ?: gl.gain
            }
            else -> return // নামাজের বাইরের ধরন এখানে আসে না (showExtra)
        }
        // একই ওয়াক্তের আগের নোটিফিকেশন সরিয়ে নতুনটা — ট্রেতে জমে থাকবে না
        cancelFor(ctx, date, prayer)
        val id = PlannedNotif(date, prayer, kind, now).id
        post(ctx, id, CH_PRAYER, title, if (isTest) "টেস্ট: রিমাইন্ডার ঠিকমতো বাজছে ✓" else shortText, bigText, opened, prayer, date, kind)
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
        val b = NotificationCompat.Builder(ctx, channelId(ctx, channel))
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
        /** একটা পুরস্কার বা ক্ষতি (major আগে) — intro/hope থাকলে শেষে এক লাইন করে */
        fun single(title: String, amal: Amal, gain: Boolean, intro: String?, route: String?, salt: Int, hope: String? = null) {
            val tags = listOfNotNull(if (gain) amal.gainGeneral else amal.lossGeneral)
            val item = ContentPicker.pickMajorFirst(items, tags, seen, today, salt)
            if (item == null) {
                intro?.let { simple(title, it, route) }
                return
            }
            store.markSeen(item.id, today)
            val (sh, body) = item.formatSingle(amal, gain, hope)
            post(ctx, id, CH_AYAH, title, sh, listOfNotNull(body, intro).joinToString("\n\n"), null, null, date, route = route)
        }
        /** ক্ষতির নোটিফিকেশন এলে আগের পুরস্কারেরটা সরিয়ে দিই */
        fun cancelExtra(k: NotifKind) = NotificationManagerCompat.from(ctx).cancel(PlannedNotif(date, null, k, Engine.now(ctx)).id)
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
                single("${set.bn}ের সময়", Amal.DHIKR, gain = true, intro = "${set.whenBn} — ${Bn.num(n)}টি যিকির, ১০–১৫ মিনিট।", route = "azkar:${set.name}", salt = set.code)
            }
            NotifKind.AZKAR_MORNING_LOSS, NotifKind.AZKAR_EVENING_LOSS -> {
                val set = if (kind == NotifKind.AZKAR_MORNING_LOSS) AzkarSet.MORNING else AzkarSet.EVENING
                if ((date to set) in store.azkarDone(date, date)) return
                cancelExtra(if (set == AzkarSet.MORNING) NotifKind.AZKAR_MORNING else NotifKind.AZKAR_EVENING)
                single("${set.bn} এখনো বাকি", Amal.DHIKR, gain = false, intro = null, route = "azkar:${set.name}", salt = set.code,
                    hope = "এখনো দেরি হয়নি — ১০ মিনিটেই হয়ে যায়।")
            }
            NotifKind.MUHASABA -> {
                if (store.muhasaba(date) != null) return
                single("আজকের হিসাব — ৯০ সেকেন্ড", Amal.MUHASABA, gain = true, intro = "আজ কী ভালো করলেন, কোথায় ভুল হলো, কাল কী বদলাবেন?", route = "muhasaba", salt = 7)
            }
            NotifKind.MUHASABA_LOSS -> {
                if (store.muhasaba(date) != null) return
                cancelExtra(NotifKind.MUHASABA)
                single("আজকের হিসাব এখনো বাকি", Amal.MUHASABA, gain = false, intro = null, route = "muhasaba", salt = 7,
                    hope = "আল্লাহ রাতে ক্ষমার হাত প্রসারিত করেন — ঘুমানোর আগে ৯০ সেকেন্ড।")
            }
            NotifKind.JUMUAH_EVE -> {
                val g = ContentPicker.pickAny(items, listOf("kahf"), seen, today, 11) ?: return
                store.markSeen(g.id, today)
                val (sh, body) = g.formatSingle(Amal.JUMUAH, gain = true)
                post(ctx, id, CH_AYAH, "আগামীকাল জুমা", sh, body + "\n\nআজ মাগরিব থেকেই সূরা কাহফ শুরু করা যায়। বেশি বেশি দরুদ পড়ুন।", g, null, date)
            }
            NotifKind.JUMUAH_DAY -> single("আজ জুমা — সপ্তাহের সেরা দিন", Amal.JUMUAH, gain = true,
                intro = "গোসল, পরিষ্কার পোশাক, সূরা কাহফ, বেশি বেশি দরুদ — আর আগে আগে মসজিদে।", route = null, salt = 12)
            NotifKind.JUMUAH_DAY_LOSS -> {
                cancelExtra(NotifKind.JUMUAH_DAY)
                single("জুমা — অবহেলা নয়", Amal.JUMUAH, gain = false, intro = null, route = null, salt = 12,
                    hope = "এখনই প্রস্তুতি নিন — আগে গেলে বেশি সওয়াব।")
            }
            NotifKind.BREAKFAST, NotifKind.QAILULA, NotifKind.DINNER, NotifKind.SLEEP_PREP,
            NotifKind.DUHA, NotifKind.TAHAJJUD, NotifKind.FAST_EVE -> routine(ctx, kind, date, id)
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

    /** সুন্নত দিনলিপির নোটিফিকেশন: শিরোনাম + একটা হাদিস + একটা বাস্তব পরামর্শ */
    private fun routine(ctx: Context, kind: NotifKind, date: LocalDate, id: Int) {
        val store = LogStore.get(ctx)
        val today = Engine.today(ctx)
        val tomorrowName = if (date.dayOfWeek == java.time.DayOfWeek.SUNDAY) "সোমবার" else "বৃহস্পতিবার"
        val (tag, title, tip, route) = when (kind) {
            NotifKind.BREAKFAST -> Quad("sunnah_breakfast", "সকালের খাবারের সময়",
                "বিসমিল্লাহ বলে ডান হাতে খান, পেট ভরে নয় — শেষে আলহামদুলিল্লাহ।", null)
            NotifKind.QAILULA -> Quad("sunnah_qailula", "কাইলুলা — দুপুরের সুন্নত বিশ্রাম",
                "২০–৩০ মিনিট চোখ বন্ধ করে বিশ্রাম নিন — রাতে ইবাদত আর ফজরে ওঠা সহজ হয়।", null)
            NotifKind.DINNER -> Quad("sunnah_dinner", "রাতের খাবারের সময়",
                "ইশার আগে খেয়ে নিন — তাড়াতাড়ি ঘুমানো সহজ হবে। বিসমিল্লাহ, ডান হাত, পরিমিত।", null)
            NotifKind.SLEEP_PREP -> Quad("sunnah_sleep", "ঘুমের প্রস্তুতি — সুন্নত মেনে",
                "ওযু, ডান কাতে শোয়া, তিন কুল ও আয়াতুল কুরসি — ট্যাপ করে ঘুমের আমল খুলুন।", "azkar:SLEEP")
            NotifKind.DUHA -> Quad("sunnah_duha", "ইশরাক/দোহার সময়",
                "সূর্য উঠে গেছে — দুই রাকাত নফল পড়ে নিন।", null)
            NotifKind.TAHAJJUD -> Quad("sunnah_tahajjud", "তাহাজ্জুদের সময় — রাতের শেষ তৃতীয়াংশ",
                "ওযু করে দুই রাকাত হলেও পড়ুন, তারপর যা চাওয়ার চেয়ে নিন।", null)
            NotifKind.FAST_EVE -> Quad("sunnah_fast", "আগামীকাল $tomorrowName — সুন্নত রোজা",
                "নিয়ত করে সাহরির জন্য অ্যালার্ম দিয়ে রাখুন।", null)
            else -> return
        }
        val items = ContentRepo.all(ctx)
        val item = ContentPicker.pickAny(items, listOf(tag), store.seenMap(), today, kind.ordinal)
        item?.let { store.markSeen(it.id, today) }
        val body = listOfNotNull(item?.let { "${it.shortBn}\n— ${it.refBn}" }, tip).joinToString("\n\n")
        post(ctx, id, CH_SUNNAH, title, item?.shortBn ?: tip, body, if (route == null) item else null, null, date, route = route)
    }

    private data class Quad(val tag: String, val title: String, val tip: String, val route: String?)

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
