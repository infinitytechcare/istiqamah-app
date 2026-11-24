package com.istiqamah.app.core

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// =====================================================================
// v0.18: সময়মতো কাজে যাওয়া — হালাল রুজি আর আমানতদারির অংশ
// অফিসের দিন: রওনা দেওয়ার ডাক (বের হওয়ার দোয়াসহ), কাজ শুরুর নিয়ত, কাজ শেষে শুকরিয়া ও বাড়ি ফেরা।
// দলিলগুলো আলেমের রিভিউয়ের তালিকায়।
// =====================================================================

/** অফিসের রিমাইন্ডারের পছন্দ */
data class WorkReminders(
    val leave: Boolean = true,
    val start: Boolean = true,
    val end: Boolean = false,
    /** বাসা থেকে অফিসে যেতে কত মিনিট */
    val commuteMin: Int = 30,
)

object Rizq {
    /** রওনার ডাক যাতায়াতের সময়ের আরও এত মিনিট আগে — তৈরি হওয়ার সময় */
    const val READY_MIN = 10L

    val PROOFS = listOf(
        BodyProof("নিজ হাতের উপার্জনের চেয়ে উত্তম খাবার কেউ কখনো খায়নি; আল্লাহর নবী দাউদ (আ.) নিজ হাতে উপার্জন করে খেতেন।", "সহিহ বুখারি ২০৭২"),
        BodyProof("নামাজ শেষ হলে জমিনে ছড়িয়ে পড়ো আর আল্লাহর অনুগ্রহ তালাশ করো, এবং আল্লাহকে বেশি বেশি স্মরণ করো।", "সূরা আল-জুমুআ ৬২:১০"),
        BodyProof("আল্লাহ তোমাদের আদেশ দেন আমানত তার হকদারের কাছে পৌঁছে দিতে।", "সূরা আন-নিসা ৪:৫৮"),
        BodyProof("সত্যবাদী, আমানতদার ব্যবসায়ী থাকবেন নবী, সিদ্দিক ও শহীদদের সাথে।", "জামে তিরমিযি ১২০৯"),
        BodyProof("মুনাফিকের চিহ্ন তিনটি — কথা বললে মিথ্যা বলে, ওয়াদা করলে ভঙ্গ করে, আমানত রাখলে খেয়ানত করে।", "সহিহ বুখারি ৩৩"),
        BodyProof("হে আল্লাহ, আমার উম্মতের সকালবেলায় বরকত দিন। (বর্ণনাকারী সাখর রা. ব্যবসায়ী ছিলেন, ভোরে কাফেলা পাঠাতেন — তাঁর ব্যবসায় বরকত হয়েছিল।)", "সুনানে আবু দাউদ ২৬০৬"),
    )

    /** ঘর থেকে বের হওয়ার দোয়া */
    const val LEAVE_DUA_AR = "بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ، لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ"
    const val LEAVE_DUA_BN = "আল্লাহর নামে বের হলাম, আল্লাহর ওপর ভরসা করলাম; আল্লাহ ছাড়া কোনো শক্তি ও সামর্থ্য নেই।"
    const val LEAVE_DUA_REF = "সুনানে আবু দাউদ ৫০৯৫, জামে তিরমিযি ৩৪২৬"

    const val NIYYAH = "হালাল রুজি দিয়ে নিজের ও পরিবারের হক আদায় করব, আর আমানতদারির সাথে কাজ করব — আল্লাহর সন্তুষ্টির জন্য।"

    /** আজ কি অফিসের দিন (ছুটি/"আজ ঘরে" বাছলে নয়) */
    fun isWorkDay(date: LocalDate, s: WorkSchedule, override: DayOverride): Boolean =
        s.enabled && date.dayOfWeek in s.days && override != DayOverride.HOME

    fun leaveAt(date: LocalDate, s: WorkSchedule, r: WorkReminders, zone: ZoneId): ZonedDateTime =
        date.atTime(s.start).atZone(zone).minusMinutes(r.commuteMin.toLong() + READY_MIN)

    /** দিনের কাজের ডাকগুলো */
    fun plan(date: LocalDate, s: WorkSchedule, r: WorkReminders?, override: DayOverride, zone: ZoneId): List<PlannedNotif> {
        if (r == null || !isWorkDay(date, s, override)) return emptyList()
        val out = ArrayList<PlannedNotif>()
        if (r.leave) out += PlannedNotif(date, null, NotifKind.WORK_LEAVE, leaveAt(date, s, r, zone))
        if (r.start) out += PlannedNotif(date, null, NotifKind.WORK_START, date.atTime(s.start).atZone(zone))
        if (r.end) out += PlannedNotif(date, null, NotifKind.WORK_END, date.atTime(s.end).atZone(zone))
        return out
    }

    fun proofFor(date: LocalDate): BodyProof = PROOFS[Math.floorMod(date.toEpochDay(), PROOFS.size.toLong()).toInt()]
}
