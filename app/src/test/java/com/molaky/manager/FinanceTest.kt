package com.molaky.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinanceTest {
    private val today = LocalDate.of(2026, 11, 13)

    private fun unit(rent: Double = 1000.0, track: String = "2026-09") =
        UnitEntity(id = 1, rent = rent, trackFrom = track, dueDay = 5)

    private fun pay(month: String, amount: Double) =
        PaymentEntity(unitId = 1, monthKey = month, amount = amount, paidAt = "2026-11-01")

    @Test
    fun unpaidMonthsAreOverdue() {
        val s = summarize(unit(), emptyList(), emptyList(), today)
        assertEquals(3, s.overdueMonths.size)
        assertEquals(3000.0, s.overdueTotal, 0.001)
        assertEquals(UnitState.OVERDUE, s.state)
    }

    @Test
    fun paymentReducesOverdue() {
        val s = summarize(unit(), emptyList(), listOf(pay("2026-09", 1000.0)), today)
        assertEquals(2, s.overdueMonths.size)
    }

    @Test
    fun currentMonthNotDueBeforeDueDay() {
        val s = summarize(unit(), emptyList(), emptyList(), LocalDate.of(2026, 11, 3))
        assertEquals(2, s.overdueMonths.size)
        assertEquals(UnitState.OVERDUE, s.state)
    }

    @Test
    fun rentChangeAppliesFromItsMonth() {
        val changes = listOf(
            RentChangeEntity(unitId = 1, fromMonth = "2026-09", amount = 1000.0),
            RentChangeEntity(unitId = 1, fromMonth = "2026-11", amount = 1200.0)
        )
        val s = summarize(unit(1200.0), changes, emptyList(), today)
        assertEquals(3200.0, s.overdueTotal, 0.001)
    }

    @Test
    fun fullyPaidIsPaid() {
        val pays = listOf(pay("2026-09", 1000.0), pay("2026-10", 1000.0), pay("2026-11", 1000.0))
        val s = summarize(unit(), emptyList(), pays, today)
        assertEquals(UnitState.PAID, s.state)
    }

    @Test
    fun arabicDigitsAreParsed() {
        assertEquals(1500.5, parseNum("١٥٠٠٫٥")!!, 0.001)
        assertEquals(2500.0, parseNum("2,500")!!, 0.001)
    }

    @Test
    fun whatsappNumberForEgypt() {
        assertEquals("201012345678", waNumber("0101 234 5678"))
        assertEquals("201012345678", waNumber("+20 101 234 5678"))
    }

    @Test
    fun increaseCalculation() {
        val pct = UnitEntity(rent = 1000.0, increaseType = INC_PERCENT, increaseValue = 10.0)
        val fixed = UnitEntity(rent = 1000.0, increaseType = INC_FIXED, increaseValue = 150.0)
        assertEquals(1100.0, pct.rentAfterIncrease(), 0.001)
        assertEquals(1150.0, fixed.rentAfterIncrease(), 0.001)
        assertTrue(money(1500.0).contains("1,500"))
    }
}
