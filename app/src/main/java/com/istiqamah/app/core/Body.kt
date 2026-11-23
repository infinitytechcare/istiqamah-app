package com.istiqamah.app.core

import java.time.LocalDate
import java.time.ZonedDateTime

// =====================================================================
// v0.17: শরীরের হক — "শক্তিশালী মুমিন দুর্বল মুমিনের চেয়ে উত্তম" (মুসলিম ২৬৬৪)
// ইবাদত ভালোভাবে করার জন্য শরীর মজবুত রাখা: ফজরের পর হাঁটা, সপ্তাহে ৩ দিন শক্তির ব্যায়াম,
// কাজের মাঝে উঠে দাঁড়ানো। দলিলগুলো আলেমের রিভিউয়ের তালিকায়।
// =====================================================================

enum class BodyAct(val bn: String, val icon: String) {
    WALK("ফজরের পর হাঁটা", "🚶"),
    STRENGTH("শক্তির ব্যায়াম", "💪"),
    STAND("কাজের মাঝে উঠে দাঁড়ানো", "🧍"),
}

/** তিন ধাপ — অল্প দিয়ে শুরু, ধীরে বাড়ানো */
enum class FitLevel(val bn: String, val pushups: Int, val squats: Int, val plankSec: Int, val walkMin: Int) {
    START("শুরু", 5, 10, 15, 20),
    STEADY("নিয়মিত", 10, 20, 30, 30),
    STRONG("মজবুত", 20, 30, 60, 40),
}

data class Exercise(val name: String, val how: String, val amount: (FitLevel) -> String)

data class BodyProof(val text: String, val ref: String)

object Body {
    /** সপ্তাহে শক্তির ব্যায়ামের লক্ষ্য */
    const val STRENGTH_TARGET = 3

    val PROOFS = listOf(
        BodyProof("শক্তিশালী মুমিন দুর্বল মুমিনের চেয়ে উত্তম ও আল্লাহর কাছে বেশি প্রিয়, তবে উভয়ের মধ্যেই কল্যাণ আছে।", "সহিহ মুসলিম ২৬৬৪"),
        BodyProof("তোমার শরীরেরও তোমার ওপর হক আছে।", "সহিহ বুখারি ১৯৭৫"),
        BodyProof("দুটি নিয়ামতে বেশিরভাগ মানুষ ধোঁকায় থাকে: সুস্থতা ও অবসর।", "সহিহ বুখারি ৬৪১২"),
        BodyProof("তোমরা যথাসাধ্য শক্তি প্রস্তুত রাখো।", "সূরা আল-আনফাল ৮:৬০"),
        BodyProof("নবী ﷺ আয়েশা (রা.)-এর সাথে দৌড় প্রতিযোগিতা করেছেন।", "সুনানে আবু দাউদ ২৫৭৮"),
        BodyProof("যে ঘরে ওযু করে আল্লাহর ঘরে হেঁটে যায়, তার এক কদমে গুনাহ মাফ হয়, আরেক কদমে মর্যাদা বাড়ে।", "সহিহ মুসলিম ৬৬৬"),
        BodyProof("পেটের এক ভাগ খাবার, এক ভাগ পানি, এক ভাগ শ্বাসের জন্য।", "জামে তিরমিযি ২৩৮০"),
        BodyProof("হে আল্লাহ, আমার উম্মতের সকালবেলায় বরকত দিন।", "সুনানে আবু দাউদ ২৬০৬"),
    )

    val EXERCISES = listOf(
        Exercise("বুকডন (পুশ-আপ)", "হাত কাঁধ বরাবর, শরীর সোজা। কষ্ট হলে হাঁটু মাটিতে রেখে করুন।") { "${Bn.num(it.pushups)} বার × ৩" },
        Exercise("বসা-ওঠা (স্কোয়াট)", "পা কাঁধ বরাবর, চেয়ারে বসার মতো নিচে নামুন, হাঁটু পায়ের আঙুল ছাড়াবে না।") { "${Bn.num(it.squats)} বার × ৩" },
        Exercise("প্ল্যাংক", "কনুই আর পায়ের আঙুলে ভর দিয়ে শরীর সোজা রাখুন, কোমর ঝুলবে না।") { "${Bn.num(it.plankSec)} সেকেন্ড × ২" },
        Exercise("দ্রুত হাঁটা", "এমন গতিতে যে কথা বলা যায়, কিন্তু গান গাওয়া কষ্ট।") { "${Bn.num(it.walkMin)} মিনিট" },
    )

    val WHEN = listOf(
        "ফজরের পর — সকালের বরকতের সময়; হাঁটা বা হালকা ব্যায়াম।",
        "আসর থেকে মাগরিবের আগে — খেয়াল রাখুন যেন মাগরিবের জামাত না ছোটে।",
        "ভরা পেটে নয় — খাওয়ার অন্তত দেড়-দুই ঘণ্টা পরে।",
        "ইশার পর তাড়াতাড়ি ঘুম — তাহলে ফজর আর সকালের হাঁটা দুটোই সহজ।",
        "ব্যায়ামের সময়ও সতর ঢাকা: পুরুষের নাভি থেকে হাঁটু, মহিলাদের পর্দা বজায় রেখে।",
        "অসুখ বা ব্যথা থাকলে শুরুর আগে ডাক্তারের পরামর্শ — এটাও শরীরের হক।",
    )

    /** নিয়ত — অভ্যাসকে ইবাদত বানায় */
    const val NIYYAH = "আল্লাহর ইবাদত ভালোভাবে করার জন্য শরীর মজবুত রাখছি।"

    /**
     * সকালের হাঁটার রিমাইন্ডার: জামাতে ফজর হলে জামাতের ২৫ মিনিট পরে, নইলে ওয়াক্ত শুরুর ৩৫ মিনিট পরে;
     * তবে সূর্যোদয়ের ১০ মিনিট আগে পেরোবে না।
     */
    fun walkTime(fajr: PrayerWindow, jamaat: ZonedDateTime?): ZonedDateTime {
        val base = jamaat?.plusMinutes(25) ?: fajr.start.plusMinutes(35)
        val latest = fajr.end.minusMinutes(10)
        return if (base.isAfter(latest)) latest else base
    }

    /** শেষ ৭ দিনে (আজসহ) কত দিন */
    fun daysThisWeek(log: Map<LocalDate, Set<BodyAct>>, act: BodyAct, today: LocalDate): Int =
        (0L..6L).count { act in log[today.minusDays(it)].orEmpty() }

    /** টানা কত দিন (আজ না হলেও গতকাল পর্যন্ত টানা থাকলে সেটা গোনে) */
    fun streak(log: Map<LocalDate, Set<BodyAct>>, act: BodyAct, today: LocalDate): Int {
        var d = if (act in log[today].orEmpty()) today else today.minusDays(1)
        var n = 0
        while (act in log[d].orEmpty()) { n++; d = d.minusDays(1) }
        return n
    }

    /** শেষ ৪ সপ্তাহে ১০ দিন বা বেশি শক্তির ব্যায়াম হলে পরের ধাপের পরামর্শ */
    fun suggestNext(log: Map<LocalDate, Set<BodyAct>>, level: FitLevel, today: LocalDate): FitLevel? {
        if (level == FitLevel.entries.last()) return null
        val n = (0L..27L).count { BodyAct.STRENGTH in log[today.minusDays(it)].orEmpty() }
        return if (n >= 10) FitLevel.entries[level.ordinal + 1] else null
    }

    /** আজকের এক লাইনের উৎসাহ — দিন বদলালে দলিল বদলায় */
    fun proofFor(date: LocalDate): BodyProof = PROOFS[Math.floorMod(date.toEpochDay(), PROOFS.size.toLong()).toInt()]
}
