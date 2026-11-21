package com.istiqamah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ZakatTest {
    // উদাহরণের দাম (প্রতি ভরি) — আসল দাম ইউজার দেন
    private val goldP = 150_000L
    private val silverP = 2_500L

    @Test fun mixedAssetsUseSilverNisab() {
        val r = Zakat.calculate(ZakatInput(cash = 100_000, goldBhori = 2.0, goldPricePerBhori = goldP, silverPricePerBhori = silverP, debts = 20_000))
        assertEquals(300_000, r.goldValue)
        assertEquals(400_000, r.totalAssets)
        assertEquals(380_000, r.net)
        assertEquals(NisabBasis.SILVER, r.basisUsed)
        assertEquals(131_250L, r.nisab) // ৫২.৫ × ২৫০০
        assertTrue(r.reachesNisab)
        assertEquals(9_500, r.due) // ২.৫%
    }

    @Test fun onlyGoldUsesGoldNisabByWeight() {
        val below = Zakat.calculate(ZakatInput(goldBhori = 5.0, goldPricePerBhori = goldP))
        assertEquals(NisabBasis.GOLD, below.basisUsed)
        assertEquals(0, below.due) // ৭.৫ ভরির কম
        val above = Zakat.calculate(ZakatInput(goldBhori = 8.0, goldPricePerBhori = goldP))
        assertEquals(30_000, above.due) // ৮ × ১,৫০,০০০ × ২.৫%
        // দাম না দিলেও ওজনে নিসাব বোঝা যায়, কিন্তু টাকার অঙ্ক শূন্য
        assertEquals(0, Zakat.calculate(ZakatInput(goldBhori = 8.0)).due)
    }

    @Test fun belowNisabOrDebtsCancel() {
        assertEquals(0, Zakat.calculate(ZakatInput(cash = 100_000, silverPricePerBhori = silverP)).due)
        assertEquals(0, Zakat.calculate(ZakatInput(cash = 500_000, debts = 600_000, silverPricePerBhori = silverP)).net)
        // রুপার দাম না জানলে নিসাব অজানা — যাকাত দেখায় না
        val r = Zakat.calculate(ZakatInput(cash = 500_000))
        assertNull(r.nisab)
        assertFalse(r.reachesNisab)
    }

    @Test fun explicitGoldBasis() {
        val r = Zakat.calculate(ZakatInput(cash = 500_000, goldPricePerBhori = goldP, basis = NisabBasis.GOLD))
        assertEquals(1_125_000L, r.nisab)
        assertEquals(0, r.due)
    }

    @Test fun hajjSavings() {
        assertEquals(10, HajjPlan.monthsToGoal(700_000, 200_000, 50_000))
        assertEquals(0, HajjPlan.monthsToGoal(700_000, 800_000, 0))
        assertNull(HajjPlan.monthsToGoal(700_000, 0, 0))
    }

    @Test fun charityReminders() {
        val zone = ZoneId.of("Asia/Dhaka")
        val loc = Location(23.8103, 90.4125, zone)
        val fri = LocalDate.of(2026, 11, 20)
        val ws = PrayerTimesCalculator.windows(PrayerTimesCalculator.compute(fri, loc), PrayerTimesCalculator.compute(fri.plusDays(1), loc))
        val hijri = mapOf(
            fri to HijriDay(1448, 11, 29), fri.plusDays(1) to HijriDay(1448, 12, 1),
            fri.plusDays(7) to HijriDay(1448, 12, 7),
        )
        fun input(z: Pair<Int, Int>?) = PlannerInput(
            fri.atStartOfDay(zone), mapOf(fri to ws), Intensity.LIGHT, emptySet(), emptySet(), emptySet(),
            extras = ExtraReminders(dailyAyahAt = null, azkar = false, muhasabaAt = null, jumuah = false, weekly = false,
                zakatHijri = z, sadaqahDay = DayOfWeek.FRIDAY, dhulHijjah = true),
            hijri = hijri,
        )
        val soon = Planner.charityFor(fri, ws, input(12 to 7)).map { it.kind }
        assertTrue(NotifKind.ZAKAT_SOON in soon && NotifKind.ZAKAT_DAY !in soon)
        assertTrue(NotifKind.SADAQAH_WEEKLY in soon)
        assertTrue(NotifKind.DHUL_HIJJAH_EVE in soon) // কাল ১ জিলহজ
        assertTrue(NotifKind.ZAKAT_DAY in Planner.charityFor(fri, ws, input(11 to 29)).map { it.kind })
        assertTrue(Planner.charityFor(fri, ws, input(null)).none { it.kind == NotifKind.ZAKAT_DAY || it.kind == NotifKind.ZAKAT_SOON })
    }
}
