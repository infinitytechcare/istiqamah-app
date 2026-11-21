package com.istiqamah.app.core

/**
 * v0.8 — যাকাত, সদকা ও হজ।
 *
 * যাকাত: (যাকাতযোগ্য সম্পদ − পরিশোধযোগ্য ঋণ) নিসাব ছুঁলে আর এক চন্দ্রবছর পূর্ণ হলে ২.৫%।
 * নিসাব: রুপা ৫২.৫ তোলা/ভরি (৬১২.৩৬ গ্রাম) বা সোনা ৭.৫ তোলা/ভরি (৮৭.৪৮ গ্রাম)।
 * বাংলাদেশে প্রচলিত হানাফি মত: শুধু সোনা থাকলে সোনার নিসাব; সোনা/রুপা/টাকা মিশ্র হলে রুপার নিসাব
 * (গরিবের জন্য বেশি উপকারী)। নিজের বসতবাড়ি, ব্যবহারের গাড়ি-আসবাব যাকাতযোগ্য নয়।
 * এটা হিসাবের সাহায্য — চূড়ান্ত মাসআলা আলেমের কাছে।
 */
object Nisab {
    /** ১ ভরি (তোলা) = ১১.৬৬৪ গ্রাম */
    const val GRAM_PER_BHORI = 11.664
    const val GOLD_BHORI = 7.5
    const val SILVER_BHORI = 52.5
    const val RATE = 0.025
}

enum class NisabBasis(val code: Int, val bn: String) {
    AUTO(0, "নিজে থেকে (শুধু সোনা হলে সোনা, নইলে রুপা)"),
    SILVER(1, "রুপার নিসাব (৫২.৫ ভরি)"),
    GOLD(2, "সোনার নিসাব (৭.৫ ভরি)");

    companion object {
        fun fromCode(c: Int) = entries.firstOrNull { it.code == c } ?: AUTO
    }
}

/** যাকাতের হিসাবের ইনপুট — সব টাকা (৳) */
data class ZakatInput(
    val cash: Long = 0,          // হাতে ও ব্যাংকে নগদ
    val savings: Long = 0,       // ডিপিএস, এফডিআর, সঞ্চয়পত্র, মোবাইল ব্যাংকিং
    val goldBhori: Double = 0.0,
    val goldPricePerBhori: Long = 0,
    val silverBhori: Double = 0.0,
    val silverPricePerBhori: Long = 0,
    val business: Long = 0,      // ব্যবসার মাল (বিক্রয়মূল্যে)
    val receivable: Long = 0,    // পাওনা টাকা যা ফেরত পাওয়ার আশা আছে
    val investments: Long = 0,   // শেয়ার, বিক্রির উদ্দেশ্যে কেনা জমি/ফ্ল্যাট ইত্যাদি
    val debts: Long = 0,         // এখন পরিশোধযোগ্য ঋণ/বকেয়া
    val basis: NisabBasis = NisabBasis.AUTO,
)

data class ZakatResult(
    val goldValue: Long,
    val silverValue: Long,
    val totalAssets: Long,
    val net: Long,
    val basisUsed: NisabBasis,
    /** নিসাবের টাকার মান; দাম না জানলে null */
    val nisab: Long?,
    val due: Long,
) {
    val reachesNisab: Boolean get() = nisab != null && net >= nisab
}

object Zakat {
    fun calculate(i: ZakatInput): ZakatResult {
        val gold = Math.round(i.goldBhori.coerceAtLeast(0.0) * i.goldPricePerBhori.coerceAtLeast(0))
        val silver = Math.round(i.silverBhori.coerceAtLeast(0.0) * i.silverPricePerBhori.coerceAtLeast(0))
        val others = listOf(i.cash, i.savings, i.business, i.receivable, i.investments).sumOf { it.coerceAtLeast(0) }
        val total = gold + silver + others
        val net = (total - i.debts.coerceAtLeast(0)).coerceAtLeast(0)
        val onlyGold = i.goldBhori > 0 && silver == 0L && others == 0L
        val basis = when (i.basis) {
            NisabBasis.AUTO -> if (onlyGold) NisabBasis.GOLD else NisabBasis.SILVER
            else -> i.basis
        }
        val nisab = when (basis) {
            NisabBasis.GOLD -> i.goldPricePerBhori.takeIf { it > 0 }?.let { Math.round(Nisab.GOLD_BHORI * it) }
            else -> i.silverPricePerBhori.takeIf { it > 0 }?.let { Math.round(Nisab.SILVER_BHORI * it) }
        }
        // শুধু সোনা: দামের দরকার নেই — ওজনেই নিসাব বোঝা যায়
        val reaches = if (basis == NisabBasis.GOLD && onlyGold) i.goldBhori >= Nisab.GOLD_BHORI else nisab != null && net >= nisab
        val due = if (reaches) Math.round(net * Nisab.RATE) else 0L
        return ZakatResult(gold, silver, total, net, basis, nisab, due)
    }
}

/** হজ/উমরাহর সঞ্চয়: কত মাস লাগবে */
object HajjPlan {
    fun monthsToGoal(target: Long, saved: Long, perMonth: Long): Int? {
        val left = (target - saved).coerceAtLeast(0)
        if (left == 0L) return 0
        if (perMonth <= 0) return null
        return Math.ceil(left / perMonth.toDouble()).toInt()
    }

    val CHECKLIST = listOf(
        "হজ/উমরাহর নিয়ত করেছি এবং দোয়া করছি",
        "হালাল টাকায় সঞ্চয় শুরু করেছি",
        "ইহরামের নিয়ম ও নিষেধ শিখেছি",
        "তালবিয়া মুখস্থ করেছি",
        "তাওয়াফের নিয়ম শিখেছি",
        "সাঈ (সাফা-মারওয়া) শিখেছি",
        "আরাফা, মুযদালিফা ও মিনার কাজ শিখেছি",
        "কুরবানি, মাথা মুণ্ডানো ও তাওয়াফুল বিদা শিখেছি",
        "ঋণ, আমানত ও মানুষের হক মিটিয়ে নিচ্ছি",
        "পাসপোর্ট ও নিবন্ধনের খোঁজ নিয়েছি",
    )
}
