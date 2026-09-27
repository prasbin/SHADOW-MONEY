package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Transaction
import kotlin.math.abs

class IntelligenceEngine(
    private val periodCalculator: PeriodCalculator = PeriodCalculator(),
    private val recurringDetector: RecurringDetector = RecurringDetector(),
    private val unusualDetector: UnusualDetector = UnusualDetector(),
    private val projectionEngine: ProjectionEngine = ProjectionEngine()
) {

    fun analyze(transactions: List<Transaction>, categories: List<Category>, now: Long): IntelligenceReport {
        val period = periodCalculator.periodStats(transactions, now)
        val previous = periodCalculator.previousPeriodStats(transactions, now)
        val categoryNames = categories.associate { it.id to it.name }
        val insights = mutableListOf<Insight>()

        insights.add(
            Insight(
                kind = InsightKind.FACT,
                title = "Analysis window",
                summary = "Analysis covers the $ANALYSIS_WINDOW_DAYS-day window ending now; ${period.transactionCount} transactions in window.",
                periodLabel = "Last $ANALYSIS_WINDOW_DAYS days"
            )
        )
        insights.add(
            Insight(
                kind = InsightKind.CALCULATION,
                title = "Income in window",
                summary = "Total income over the last $ANALYSIS_WINDOW_DAYS days.",
                amountMinor = period.incomeMinor,
                periodLabel = "Last $ANALYSIS_WINDOW_DAYS days"
            )
        )
        insights.add(
            Insight(
                kind = InsightKind.CALCULATION,
                title = "Outflow in window",
                summary = "Total outflow over the last $ANALYSIS_WINDOW_DAYS days.",
                amountMinor = period.outflowMinor,
                periodLabel = "Last $ANALYSIS_WINDOW_DAYS days"
            )
        )
        insights.add(
            Insight(
                kind = InsightKind.CALCULATION,
                title = "Net change in window",
                summary = "Income minus outflow over the last $ANALYSIS_WINDOW_DAYS days.",
                amountMinor = period.netMinor,
                periodLabel = "Last $ANALYSIS_WINDOW_DAYS days"
            )
        )

        val incomeTrend = periodCalculator.trendPercent(period.incomeMinor, previous.incomeMinor)
        if (incomeTrend != null) {
            insights.add(
                Insight(
                    kind = InsightKind.CALCULATION,
                    title = "Income vs previous $ANALYSIS_WINDOW_DAYS days",
                    summary = "Income is ${formatTrend(incomeTrend)} compared with the previous $ANALYSIS_WINDOW_DAYS-day period.",
                    amountMinor = period.incomeMinor - previous.incomeMinor,
                    periodLabel = "vs previous period",
                    evidence = "Previous period income: ${previous.incomeMinor} minor units"
                )
            )
        }
        val outflowTrend = periodCalculator.trendPercent(period.outflowMinor, previous.outflowMinor)
        if (outflowTrend != null) {
            insights.add(
                Insight(
                    kind = InsightKind.CALCULATION,
                    title = "Outflow vs previous $ANALYSIS_WINDOW_DAYS days",
                    summary = "Outflow is ${formatTrend(outflowTrend)} compared with the previous $ANALYSIS_WINDOW_DAYS-day period.",
                    amountMinor = period.outflowMinor - previous.outflowMinor,
                    periodLabel = "vs previous period",
                    evidence = "Previous period outflow: ${previous.outflowMinor} minor units"
                )
            )
        }

        recurringDetector.detect(transactions, now).forEach { pattern ->
            insights.add(
                Insight(
                    kind = InsightKind.ANALYSIS,
                    title = "Recurring outflow: ${pattern.note}",
                    summary = "Detected ${pattern.occurrences} similar outflows of about the same amount at roughly regular intervals. Pattern analysis, not a confirmed future payment.",
                    amountMinor = pattern.typicalAmountMinor,
                    periodLabel = "Last $ANALYSIS_WINDOW_DAYS days",
                    evidence = "${pattern.occurrences} occurrences · typical interval ~${pattern.typicalIntervalDays} days · total ${pattern.totalMinor} minor units"
                )
            )
        }

        unusualDetector.detectCategorySpending(transactions, now).forEach { anomaly ->
            val name = anomaly.categoryId?.let { categoryNames[it] } ?: "Uncategorized"
            insights.add(
                Insight(
                    kind = InsightKind.ANALYSIS,
                    title = "Category spending above baseline: $name",
                    summary = "Spending in this category over the last $UNUSUAL_RECENT_DAYS days is materially above its historical baseline. Statistical comparison, not a judgement.",
                    amountMinor = anomaly.recentTotalMinor,
                    periodLabel = "Last $UNUSUAL_RECENT_DAYS days",
                    evidence = "Recent ${anomaly.recentTotalMinor} vs typical $UNUSUAL_RECENT_DAYS-day ${anomaly.typical30DayMinor} minor units"
                )
            )
        }

        unusualDetector.detectLargeTransactions(transactions, now).forEach { anomaly ->
            insights.add(
                Insight(
                    kind = InsightKind.ANALYSIS,
                    title = "Unusually large transaction",
                    summary = "This outflow is materially larger than the category's average transaction. Statistical comparison, not a judgement.",
                    amountMinor = anomaly.transaction.amountMinor,
                    periodLabel = "Last $ANALYSIS_WINDOW_DAYS days",
                    evidence = "Transaction ${anomaly.transaction.amountMinor} vs category average ${anomaly.categoryAverageMinor} minor units"
                )
            )
        }

        projectionEngine.projectMonthlyOutflow(transactions, now)?.let { projection ->
            insights.add(
                Insight(
                    kind = InsightKind.PROJECTION,
                    title = "Projected monthly outflow",
                    summary = "If the observed daily spending rate continues, outflow over the next $PROJECTION_DAYS days is projected from stored records. Projection, not a guarantee.",
                    amountMinor = projection.projectedMonthlyOutflowMinor,
                    periodLabel = "Next $PROJECTION_DAYS days",
                    evidence = "Based on ${projection.historyDays} days of history across ${projection.transactionCount} outflow transactions",
                    assumptions = listOf(
                        "Assumes the observed daily spending rate continues unchanged",
                        "Based on ${projection.historyDays} days of stored history",
                        "Projection only — not a guarantee of future spending"
                    )
                )
            )
        }

        val sufficientData = period.transactionCount >= 3 &&
            periodCalculator.historyDays(transactions, now) >= PROJECTION_MIN_HISTORY_DAYS

        return IntelligenceReport(
            windowStart = AnalysisWindow.start(now),
            windowEnd = now,
            period = period,
            previousPeriod = previous,
            insights = insights,
            sufficientData = sufficientData
        )
    }

    private fun formatTrend(percent: Long): String {
        val direction = if (percent >= 0) "up" else "down"
        return "$direction ${abs(percent)}%"
    }
}
