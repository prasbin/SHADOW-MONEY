package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import org.junit.Test
import org.junit.Assert.*

class PeriodCalculatorTest {

    private val now = 1_800_000_000_000L
    private val calculator = PeriodCalculator()

    private fun tx(
        id: Long,
        amount: Long,
        direction: Int,
        timestamp: Long,
        categoryId: Long? = 1L,
        accountId: Long = 1L
    ) = Transaction(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amount,
        direction = direction,
        transactionTimestamp = timestamp
    )

    @Test
    fun windowBoundary_inclusiveStart_excludesOutside() {
        val windowStart = AnalysisWindow.start(now)
        val transactions = listOf(
            tx(1, 100L, TRANSACTION_DIRECTION_INCOME, windowStart),
            tx(2, 200L, TRANSACTION_DIRECTION_INCOME, windowStart - 1),
            tx(3, 300L, TRANSACTION_DIRECTION_INCOME, now),
            tx(4, 400L, TRANSACTION_DIRECTION_INCOME, now + 1)
        )
        val stats = calculator.periodStats(transactions, now)
        assertEquals(400L, stats.incomeMinor)
        assertEquals(2, stats.incomeCount)
    }

    @Test
    fun incomeOutflowTotals_areExact() {
        val transactions = listOf(
            tx(1, 1_000L, TRANSACTION_DIRECTION_INCOME, now - 10 * MILLIS_PER_DAY),
            tx(2, 2_500L, TRANSACTION_DIRECTION_INCOME, now - 5 * MILLIS_PER_DAY),
            tx(3, 700L, TRANSACTION_DIRECTION_OUTFLOW, now - 8 * MILLIS_PER_DAY),
            tx(4, 300L, TRANSACTION_DIRECTION_OUTFLOW, now - 2 * MILLIS_PER_DAY)
        )
        val stats = calculator.periodStats(transactions, now)
        assertEquals(3_500L, stats.incomeMinor)
        assertEquals(1_000L, stats.outflowMinor)
        assertEquals(2_500L, stats.netMinor)
        assertEquals(4, stats.transactionCount)
        assertEquals(2, stats.incomeCount)
        assertEquals(2, stats.outflowCount)
    }

    @Test
    fun emptyPeriod_returnsZeroes() {
        val stats = calculator.periodStats(emptyList(), now)
        assertEquals(0L, stats.incomeMinor)
        assertEquals(0L, stats.outflowMinor)
        assertEquals(0, stats.transactionCount)
    }

    @Test
    fun previousPeriod_comparableWindow() {
        val windowStart = AnalysisWindow.start(now)
        val prevStart = AnalysisWindow.previousStart(now)
        val transactions = listOf(
            tx(1, 9_000L, TRANSACTION_DIRECTION_OUTFLOW, prevStart + MILLIS_PER_DAY),
            tx(2, 1_000L, TRANSACTION_DIRECTION_OUTFLOW, windowStart + MILLIS_PER_DAY)
        )
        val previous = calculator.previousPeriodStats(transactions, now)
        assertEquals(9_000L, previous.outflowMinor)
        assertEquals(1, previous.outflowCount)
        val current = calculator.periodStats(transactions, now)
        assertEquals(1_000L, current.outflowMinor)
    }

    @Test
    fun categorySpending_groupsByCategoryIncludingNull() {
        val transactions = listOf(
            tx(1, 500L, TRANSACTION_DIRECTION_OUTFLOW, now - 10 * MILLIS_PER_DAY, categoryId = 1L),
            tx(2, 700L, TRANSACTION_DIRECTION_OUTFLOW, now - 9 * MILLIS_PER_DAY, categoryId = 1L),
            tx(3, 300L, TRANSACTION_DIRECTION_OUTFLOW, now - 8 * MILLIS_PER_DAY, categoryId = null),
            tx(4, 9_999L, TRANSACTION_DIRECTION_OUTFLOW, now - 7 * MILLIS_PER_DAY, categoryId = 2L),
            tx(5, 9_999L, TRANSACTION_DIRECTION_INCOME, now - 6 * MILLIS_PER_DAY, categoryId = 2L)
        )
        val spending = calculator.categorySpending(transactions, now)
        assertEquals(1_200L, spending[1L])
        assertEquals(300L, spending[null])
        assertEquals(9_999L, spending[2L])
    }

    @Test
    fun accountSpending_groupsByAccount() {
        val transactions = listOf(
            tx(1, 400L, TRANSACTION_DIRECTION_OUTFLOW, now - 10 * MILLIS_PER_DAY, accountId = 1L),
            tx(2, 600L, TRANSACTION_DIRECTION_OUTFLOW, now - 9 * MILLIS_PER_DAY, accountId = 1L),
            tx(3, 250L, TRANSACTION_DIRECTION_OUTFLOW, now - 8 * MILLIS_PER_DAY, accountId = 2L)
        )
        val spending = calculator.accountSpending(transactions, now)
        assertEquals(1_000L, spending[1L])
        assertEquals(250L, spending[2L])
    }

    @Test
    fun trendPercent_upDownAndZeroPrevious() {
        assertEquals(50L, calculator.trendPercent(150L, 100L))
        assertEquals(-50L, calculator.trendPercent(50L, 100L))
        assertNull(calculator.trendPercent(100L, 0L))
        assertEquals(0L, calculator.trendPercent(100L, 100L))
    }

    @Test
    fun historyDays_measuredFromEarliestInWindow() {
        val windowStart = AnalysisWindow.start(now)
        val transactions = listOf(
            tx(1, 100L, TRANSACTION_DIRECTION_OUTFLOW, now),
            tx(2, 100L, TRANSACTION_DIRECTION_OUTFLOW, windowStart)
        )
        assertEquals(400L, calculator.historyDays(transactions, now))
        assertEquals(0L, calculator.historyDays(emptyList(), now))
    }
}
