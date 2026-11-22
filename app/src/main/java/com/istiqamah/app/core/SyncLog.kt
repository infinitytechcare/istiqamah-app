package com.istiqamah.app.core

import java.time.LocalDate

// =====================================================================
// ফোন ↔ ম্যাক সিঙ্কের ভিত্তি (ম্যাক অ্যাপ v1 থেকে; ফোনে সিঙ্ক আসবে পরের ধাপে)
// প্রতিটি চিহ্ন একটা ঘটনা: কোন দিন, কোন ওয়াক্ত, কী অবস্থা, কখন, কোন যন্ত্রে।
// একই (দিন, ওয়াক্ত)-এর জন্য সবচেয়ে শেষের ঘটনাই টিকে থাকে; মুছে ফেলাও একটা ঘটনা (status = null),
// যাতে অন্য যন্ত্র থেকে পুরনো চিহ্ন ফিরে না আসে।
// শুধু নামাজের চিহ্ন — গুনাহের ট্র্যাকার, যাকাত, দেনা, ওসিয়ত কখনো সিঙ্ক হয় না।
// =====================================================================

data class LogEvent(
    val date: LocalDate,
    val prayer: Prayer,
    /** null = চিহ্ন মুছে ফেলা */
    val status: PrayerStatus?,
    /** ইউনিক্স মিলিসেকেন্ড */
    val ts: Long,
    /** "phone" / "mac" — একই মুহূর্তের দুই ঘটনায় স্থির ক্রমের জন্য */
    val device: String,
)

object SyncLog {
    /** কোন ঘটনা নতুন: সময় বেশি হলে; সমান হলে যন্ত্রের নাম দিয়ে স্থির ক্রম */
    private fun newer(a: LogEvent, b: LogEvent): Boolean = a.ts > b.ts || (a.ts == b.ts && a.device > b.device)

    /** প্রতিটি (দিন, ওয়াক্ত)-এর সর্বশেষ ঘটনা */
    fun latest(events: Iterable<LogEvent>): Map<Pair<LocalDate, Prayer>, LogEvent> {
        val out = HashMap<Pair<LocalDate, Prayer>, LogEvent>()
        for (e in events) {
            val k = e.date to e.prayer
            val cur = out[k]
            if (cur == null || newer(e, cur)) out[k] = e
        }
        return out
    }

    /** ঘটনাগুলো থেকে দিনের লগ (মুছে ফেলা বাদ) */
    fun dayLogs(events: Iterable<LogEvent>): Map<LocalDate, DayLog> =
        latest(events).values.filter { it.status != null }
            .groupBy { it.date }
            .mapValues { (d, es) -> DayLog(d, es.associate { it.prayer to it.status!! }) }

    /** দুই যন্ত্রের ঘটনা মেলানো — একই ঘটনা দুবার রাখি না; ক্রম বা বারবার মেলানোতে ফল বদলায় না */
    fun merge(a: List<LogEvent>, b: List<LogEvent>): List<LogEvent> = (a + b).distinct().sortedWith(compareBy({ it.ts }, { it.device }))

    /** পুরনো ইতিহাস ছেঁটে ফেলা: প্রতিটি (দিন, ওয়াক্ত)-এর শুধু সর্বশেষ ঘটনা রাখি */
    fun compact(events: List<LogEvent>): List<LogEvent> = latest(events).values.sortedWith(compareBy({ it.ts }, { it.device }))
}
