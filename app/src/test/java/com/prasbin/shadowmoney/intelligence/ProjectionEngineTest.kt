package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import org.junit.Test
import org.junit.Assert.*

class ProjectionEngineTest {

    private val now = 1_800_000_000_000L
    private val engine = ProjectionEngine()

    private fun tx(id: Long, amount: Long, timestamp: Long) = Transaction(
        id = id,
        accountId = 1L,
        categoryId = 1L,
        amountMinor = amount,
        direction = TRANSACTION_DIRECTION_OUTFLOW,
        transactionTimestamp = timestamp,
        note = "Spending"
    )

    @Test
    fun correctCalculation_dailyRateTimesThirty() {
        val transactions = listOf(
            tx(1, 100_000L, now - 100 * MILLIS_PER_DAY),
            tx(2, 100_000L, now - 50 * MILLIS_PER_DAY),
            tx(3, 100_000L, now)
        )
        val projection = engine.projectMonthlyOutflow(transactions, now)
        assertNotNull(projection)
        assertEquals(90_000L, projection!!.projectedMonthlyOutflowMinor)
        assertEquals(100L, projection.historyDays)
        assertEquals(3, projection.transactionCount)
    }

    @Test
    fun exactArithmetic_truncatesTowardZero() {
        val transactions = listOf(
            tx(1, 100_000L, now - 7 * MILLIS_PER_DAY),
            tx(2, 100_000L, now)
        )
        val projection = engine.projectMonthlyOutflow(transactions, now)
        assertNotNull(projection)
        assertEquals(857_142L, projection!!.projectedMonthlyOutflowMinor)
    }

    @Test
    fun insufficientHistory_returnsNull() {
        val transactions = listOf(
            tx(1, 100_000L, now - 3 * MILLIS_PER_DAY),
            tx(2, 100_000L, now)
        )
        assertNull(engine.projectMonthlyOutflow(transactions, now))
    }

    @Test
    fun noOutflow_returnsNull() {
        val transactions = listOf(
            Transaction(
                id = 1,
                accountId = 1L,
                categoryId = 1L,
                amountMinor = 100_000L,
                direction = com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = now - 10 * MILLIS_PER_DAY,
                note = "Income"
            )
        )
        assertNull(engine.projectMonthlyOutflow(transactions, now))
    }

    @Test
    fun transactionsOutsideWindow_ignored() {
        val transactions = listOf(
            tx(1, 100_000L, now - 500 * MILLIS_PER_DAY),
            tx(2, 100_000L, now - 100 * MILLIS_PER_DAY),
            tx(3, 100_000L, now)
        )
        val projection = engine.projectMonthlyOutflow(transactions, now)
        assertNotNull(projection)
        assertEquals(2, projection!!.transactionCount)
        assertEquals(60_000L, projection.projectedMonthlyOutflowMinor)
    }
}
