package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import org.junit.Test
import org.junit.Assert.*

class UnusualDetectorTest {

    private val now = 1_800_000_000_000L
    private val detector = UnusualDetector()

    private fun tx(
        id: Int,
        amount: Long,
        timestamp: Long,
        categoryId: Long? = 1L,
        note: String = "Spending"
    ) = Transaction(
        id = id.toLong(),
        accountId = 1L,
        categoryId = categoryId,
        amountMinor = amount,
        direction = TRANSACTION_DIRECTION_OUTFLOW,
        transactionTimestamp = timestamp,
        note = note
    )

    @Test
    fun normalSpending_noCategoryAnomaly() {
        val transactions = (0 until 10).map { index ->
            tx(index + 1, 10_000L, now - (40L + index * 35) * MILLIS_PER_DAY)
        }
        assertTrue(detector.detectCategorySpending(transactions, now).isEmpty())
    }

    @Test
    fun genuineOutlier_flaggedAgainstBaseline() {
        val transactions = (0 until 10).map { index ->
            tx(index + 1, 10_000L, now - (40L + index * 35) * MILLIS_PER_DAY)
        } + listOf(
            tx(11, 100_000L, now - 5 * MILLIS_PER_DAY),
            tx(12, 110_000L, now - 3 * MILLIS_PER_DAY)
        )
        val anomalies = detector.detectCategorySpending(transactions, now)
        assertEquals(1, anomalies.size)
        assertEquals(210_000L, anomalies.first().recentTotalMinor)
    }

    @Test
    fun insufficientBaseline_returnsNothing() {
        val transactions = listOf(
            tx(1, 10_000L, now - 10 * MILLIS_PER_DAY),
            tx(2, 500_000L, now - 2 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detectCategorySpending(transactions, now).isEmpty())
    }

    @Test
    fun categorySpecificBehavior_onlySpikingCategoryFlagged() {
        val normal = (0 until 8).map { index ->
            tx(index + 1, 10_000L, now - (40L + index * 40) * MILLIS_PER_DAY, categoryId = 1L)
        }
        val spiking = (0 until 8).map { index ->
            tx(index + 100, 10_000L, now - (40L + index * 40) * MILLIS_PER_DAY, categoryId = 2L)
        } + listOf(
            tx(200, 300_000L, now - 4 * MILLIS_PER_DAY, categoryId = 2L),
            tx(201, 320_000L, now - 2 * MILLIS_PER_DAY, categoryId = 2L)
        )
        val anomalies = detector.detectCategorySpending(normal + spiking, now)
        assertEquals(1, anomalies.size)
        assertEquals(2L, anomalies.first().categoryId)
    }

    @Test
    fun largeTransaction_flaggedAgainstCategoryAverage() {
        val transactions = listOf(
            tx(1, 10_000L, now - 90 * MILLIS_PER_DAY),
            tx(2, 11_000L, now - 60 * MILLIS_PER_DAY),
            tx(3, 9_500L, now - 30 * MILLIS_PER_DAY),
            tx(4, 100_000L, now - 5 * MILLIS_PER_DAY)
        )
        val anomalies = detector.detectLargeTransactions(transactions, now)
        assertEquals(1, anomalies.size)
        assertEquals(100_000L, anomalies.first().transaction.amountMinor)
        assertEquals(32_625L, anomalies.first().categoryAverageMinor)
    }

    @Test
    fun categoryWithTooFewTransactions_notFlagged() {
        val transactions = listOf(
            tx(1, 10_000L, now - 60 * MILLIS_PER_DAY),
            tx(2, 100_000L, now - 5 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detectLargeTransactions(transactions, now).isEmpty())
    }

    @Test
    fun smallAmountBelowMinimum_notFlagged() {
        val transactions = listOf(
            tx(1, 100L, now - 90 * MILLIS_PER_DAY),
            tx(2, 100L, now - 60 * MILLIS_PER_DAY),
            tx(3, 100L, now - 30 * MILLIS_PER_DAY),
            tx(4, 2_000L, now - 5 * MILLIS_PER_DAY)
        )
        assertTrue(detector.detectLargeTransactions(transactions, now).isEmpty())
    }

    @Test
    fun deterministic_repeatedExecutionSameResult() {
        val transactions = (0 until 10).map { index ->
            tx(index + 1, 10_000L + index * 100L, now - (40L + index * 35) * MILLIS_PER_DAY)
        } + tx(99, 250_000L, now - 2 * MILLIS_PER_DAY)
        val first = detector.detectLargeTransactions(transactions, now)
        val second = detector.detectLargeTransactions(transactions, now)
        assertEquals(first, second)
    }
}
