package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import org.junit.Test
import org.junit.Assert.*

class RecurringDetectorTest {

    private val now = 1_800_000_000_000L
    private val detector = RecurringDetector()

    private fun rent(id: Long, amount: Long, timestamp: Long, note: String = "Monthly rent") = Transaction(
        id = id,
        accountId = 1L,
        categoryId = 2L,
        amountMinor = amount,
        direction = TRANSACTION_DIRECTION_OUTFLOW,
        transactionTimestamp = timestamp,
        note = note
    )

    @Test
    fun validPattern_detectedWithTypicalValues() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(3, 15_000L, now - 30 * MILLIS_PER_DAY)
        )
        val patterns = detector.detect(transactions, now)
        assertEquals(1, patterns.size)
        val pattern = patterns.first()
        assertEquals("monthly rent", pattern.note)
        assertEquals(3, pattern.occurrences)
        assertEquals(15_000L, pattern.typicalAmountMinor)
        assertEquals(30L, pattern.typicalIntervalDays)
        assertEquals(45_000L, pattern.totalMinor)
    }

    @Test
    fun similarAmountsWithinTolerance_detected() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY),
            rent(2, 15_800L, now - 60 * MILLIS_PER_DAY),
            rent(3, 16_000L, now - 30 * MILLIS_PER_DAY)
        )
        val patterns = detector.detect(transactions, now)
        assertEquals(1, patterns.size)
        assertEquals(15_800L, patterns.first().typicalAmountMinor)
    }

    @Test
    fun insufficientOccurrences_returnsNothing() {
        val transactions = listOf(
            rent(1, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 30 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detect(transactions, now).isEmpty())
    }

    @Test
    fun inconsistentIntervals_returnsNothing() {
        val transactions = listOf(
            rent(1, 15_000L, now - 65 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(3, 15_000L, now - 5 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detect(transactions, now).isEmpty())
    }

    @Test
    fun materiallyDifferentAmounts_returnsNothing() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(3, 50_000L, now - 30 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detect(transactions, now).isEmpty())
    }

    @Test
    fun unrelatedTransactions_returnsNothing() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY, note = "Rent"),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY, note = "Groceries"),
            rent(3, 15_000L, now - 30 * MILLIS_PER_DAY, note = "Fuel")
        )
        assertTrue(detector.detect(transactions, now).isEmpty())
    }

    @Test
    fun sameDayDuplicates_rejectedByMinInterval() {
        val transactions = listOf(
            rent(1, 15_000L, now - 30 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 30 * MILLIS_PER_DAY + 3_600_000L),
            rent(3, 15_000L, now - 29 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detect(transactions, now).isEmpty())
    }

    @Test
    fun transactionsOutsideWindow_ignored() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(3, 15_000L, now - 30 * MILLIS_PER_DAY),
            rent(4, 15_000L, now - 500 * MILLIS_PER_DAY)
        )
        val patterns = detector.detect(transactions, now)
        assertEquals(1, patterns.size)
        assertEquals(3, patterns.first().occurrences)
    }

    @Test
    fun deterministic_repeatedExecutionSameResult() {
        val transactions = listOf(
            rent(1, 15_000L, now - 90 * MILLIS_PER_DAY),
            rent(2, 15_000L, now - 60 * MILLIS_PER_DAY),
            rent(3, 15_000L, now - 30 * MILLIS_PER_DAY),
            rent(4, 9_000L, now - 80 * MILLIS_PER_DAY, note = "Internet"),
            rent(5, 9_000L, now - 50 * MILLIS_PER_DAY, note = "Internet"),
            rent(6, 9_000L, now - 20 * MILLIS_PER_DAY, note = "Internet")
        )
        val first = detector.detect(transactions, now)
        val second = detector.detect(transactions, now)
        assertEquals(first, second)
        assertEquals(2, first.size)
    }
}
