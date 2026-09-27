package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import org.junit.Test
import org.junit.Assert.*

class IntelligenceEngineTest {

    private val now = 1_800_000_000_000L
    private val engine = IntelligenceEngine()

    private fun tx(
        id: Int,
        amount: Long,
        direction: Int,
        timestamp: Long,
        categoryId: Long? = 1L,
        note: String = "Spending"
    ) = Transaction(
        id = id.toLong(),
        accountId = 1L,
        categoryId = categoryId,
        amountMinor = amount,
        direction = direction,
        transactionTimestamp = timestamp,
        note = note
    )

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        direction = com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
    )

    @Test
    fun emptyInput_noAnalysisOrProjection_insufficientFlag() {
        val report = engine.analyze(emptyList(), emptyList(), now)
        assertFalse(report.sufficientData)
        val kinds = report.insights.map { it.kind }.toSet()
        assertTrue(kinds.contains(InsightKind.FACT))
        assertTrue(kinds.contains(InsightKind.CALCULATION))
        assertFalse(kinds.contains(InsightKind.ANALYSIS))
        assertFalse(kinds.contains(InsightKind.PROJECTION))
        assertEquals(0L, report.period.incomeMinor)
        assertEquals(0L, report.period.outflowMinor)
    }

    @Test
    fun windowExclusion_oldTransactionsDoNotAffectPeriod() {
        val transactions = listOf(
            tx(1, 999_999L, TRANSACTION_DIRECTION_OUTFLOW, now - 500 * MILLIS_PER_DAY),
            tx(2, 1_000L, TRANSACTION_DIRECTION_OUTFLOW, now - 10 * MILLIS_PER_DAY)
        )
        val report = engine.analyze(transactions, emptyList(), now)
        assertEquals(1_000L, report.period.outflowMinor)
        assertEquals(1, report.period.transactionCount)
    }

    @Test
    fun classification_factCalculationAnalysisProjection() {
        val transactions = mutableListOf<Transaction>()
        for (index in 0 until 10) {
            transactions += tx(
                index + 1,
                10_000L,
                TRANSACTION_DIRECTION_OUTFLOW,
                now - (40L + index * 35) * MILLIS_PER_DAY,
                note = "Monthly rent"
            )
        }
        transactions += tx(100, 5_000L, TRANSACTION_DIRECTION_INCOME, now - 20 * MILLIS_PER_DAY)
        transactions += tx(101, 5_000L, TRANSACTION_DIRECTION_INCOME, now - 10 * MILLIS_PER_DAY)

        val report = engine.analyze(transactions, listOf(category(1L, "Rent")), now)

        assertTrue(report.sufficientData)
        val kinds = report.insights.map { it.kind }.toSet()
        assertTrue(kinds.contains(InsightKind.FACT))
        assertTrue(kinds.contains(InsightKind.CALCULATION))
        assertTrue(kinds.contains(InsightKind.ANALYSIS))
        assertTrue(kinds.contains(InsightKind.PROJECTION))

        val recurring = report.insights.first { it.title.startsWith("Recurring outflow") }
        assertEquals(InsightKind.ANALYSIS, recurring.kind)
        assertTrue(recurring.summary.contains("Pattern analysis"))

        val projection = report.insights.first { it.kind == InsightKind.PROJECTION }
        assertTrue(projection.assumptions.isNotEmpty())
        assertTrue(projection.summary.contains("Projection, not a guarantee"))

        val net = report.insights.first { it.title == "Net change in window" }
        assertEquals(InsightKind.CALCULATION, net.kind)
    }

    @Test
    fun recurringInsight_classifiedAsAnalysis_notFact() {
        val transactions = (0 until 5).map { index ->
            tx(index + 1, 12_000L, TRANSACTION_DIRECTION_OUTFLOW, now - (30L + index * 30) * MILLIS_PER_DAY, note = "Gym membership")
        }
        val report = engine.analyze(transactions, emptyList(), now)
        val recurring = report.insights.filter { it.kind == InsightKind.ANALYSIS }
        assertTrue(recurring.isNotEmpty())
        assertTrue(recurring.all { it.title.startsWith("Recurring outflow") })
    }

    @Test
    fun unusualInsights_classifiedAsAnalysis_withNeutralWording() {
        val transactions = (0 until 10).map { index ->
            tx(index + 1, 10_000L, TRANSACTION_DIRECTION_OUTFLOW, now - (40L + index * 35) * MILLIS_PER_DAY)
        } + tx(50, 400_000L, TRANSACTION_DIRECTION_OUTFLOW, now - 2 * MILLIS_PER_DAY)
        val report = engine.analyze(transactions, emptyList(), now)
        val unusual = report.insights.filter { it.title == "Unusually large transaction" }
        assertEquals(1, unusual.size)
        assertEquals(InsightKind.ANALYSIS, unusual.first().kind)
        assertFalse(unusual.first().summary.contains("bad"))
        assertFalse(unusual.first().summary.contains("reckless"))
        assertFalse(unusual.first().summary.contains("wasteful"))
    }

    @Test
    fun deterministic_repeatedExecutionSameReport() {
        val transactions = (0 until 12).map { index ->
            tx(index + 1, 10_000L + index * 500L, TRANSACTION_DIRECTION_OUTFLOW, now - (40L + index * 30) * MILLIS_PER_DAY, note = "Streaming service")
        } + tx(50, 200_000L, TRANSACTION_DIRECTION_OUTFLOW, now - 3 * MILLIS_PER_DAY)
        val first = engine.analyze(transactions, listOf(category(1L, "Streaming")), now)
        val second = engine.analyze(transactions, listOf(category(1L, "Streaming")), now)
        assertEquals(first, second)
    }

    @Test
    fun previousPeriodTrend_computedWhenPreviousExists() {
        val transactions = listOf(
            tx(1, 10_000L, TRANSACTION_DIRECTION_OUTFLOW, now - 450 * MILLIS_PER_DAY),
            tx(2, 20_000L, TRANSACTION_DIRECTION_OUTFLOW, now - 10 * MILLIS_PER_DAY)
        )
        val report = engine.analyze(transactions, emptyList(), now)
        val trend = report.insights.first { it.title == "Outflow vs previous 400 days" }
        assertEquals(InsightKind.CALCULATION, trend.kind)
        assertEquals(10_000L, trend.amountMinor)
    }
}
