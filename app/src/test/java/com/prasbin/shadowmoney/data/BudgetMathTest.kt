package com.prasbin.shadowmoney.data

import org.junit.Test
import org.junit.Assert.*

class BudgetMathTest {

    @Test
    fun statusBelow50Percent_isNormal() {
        assertEquals(BudgetStatus.NORMAL, BudgetMath.statusFor(49L, 100L))
        assertEquals(BudgetStatus.NORMAL, BudgetMath.statusFor(0L, 100L))
    }

    @Test
    fun statusExactly50Percent_isApproaching() {
        assertEquals(BudgetStatus.APPROACHING, BudgetMath.statusFor(50L, 100L))
    }

    @Test
    fun statusBetween50And100_isApproaching() {
        assertEquals(BudgetStatus.APPROACHING, BudgetMath.statusFor(51L, 100L))
        assertEquals(BudgetStatus.APPROACHING, BudgetMath.statusFor(99L, 100L))
    }

    @Test
    fun statusExactly100Percent_isOver() {
        assertEquals(BudgetStatus.OVER_BUDGET, BudgetMath.statusFor(100L, 100L))
    }

    @Test
    fun statusAbove100Percent_isOver() {
        assertEquals(BudgetStatus.OVER_BUDGET, BudgetMath.statusFor(150L, 100L))
    }

    @Test
    fun zeroBudget_zeroSpending_isNormal() {
        assertEquals(BudgetStatus.NORMAL, BudgetMath.statusFor(0L, 0L))
    }

    @Test
    fun zeroBudget_anySpending_isOver() {
        assertEquals(BudgetStatus.OVER_BUDGET, BudgetMath.statusFor(1L, 0L))
    }

    @Test
    fun negativeBudget_treatedAsInvalid() {
        assertEquals(BudgetStatus.OVER_BUDGET, BudgetMath.statusFor(1L, -100L))
        assertEquals(BudgetStatus.NORMAL, BudgetMath.statusFor(0L, -100L))
    }

    @Test
    fun percentUsed_exactIntegerArithmetic() {
        assertEquals(0, BudgetMath.percentUsed(0L, 10_000L))
        assertEquals(50, BudgetMath.percentUsed(5_000L, 10_000L))
        assertEquals(33, BudgetMath.percentUsed(3_333L, 10_000L))
        assertEquals(100, BudgetMath.percentUsed(10_000L, 10_000L))
        assertEquals(150, BudgetMath.percentUsed(15_000L, 10_000L))
    }

    @Test
    fun percentUsed_zeroBudget_returnsZero_noDivideByZero() {
        assertEquals(0, BudgetMath.percentUsed(5_000L, 0L))
        assertEquals(0, BudgetMath.percentUsed(5_000L, -100L))
    }

    @Test
    fun remainingMinor_canGoNegativeWhenOver() {
        assertEquals(5_000L, BudgetMath.remainingMinor(10_000L, 5_000L))
        assertEquals(0L, BudgetMath.remainingMinor(10_000L, 10_000L))
        assertEquals(-5_000L, BudgetMath.remainingMinor(10_000L, 15_000L))
    }
}
