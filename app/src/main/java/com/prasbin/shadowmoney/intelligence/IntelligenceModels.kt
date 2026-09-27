package com.prasbin.shadowmoney.intelligence

enum class InsightKind { FACT, CALCULATION, ANALYSIS, PROJECTION }

data class Insight(
    val kind: InsightKind,
    val title: String,
    val summary: String,
    val amountMinor: Long? = null,
    val periodLabel: String? = null,
    val evidence: String? = null,
    val assumptions: List<String> = emptyList()
)

data class PeriodStats(
    val incomeMinor: Long,
    val outflowMinor: Long,
    val transactionCount: Int,
    val incomeCount: Int,
    val outflowCount: Int
) {
    val netMinor: Long get() = incomeMinor - outflowMinor
}

data class IntelligenceReport(
    val windowStart: Long,
    val windowEnd: Long,
    val period: PeriodStats,
    val previousPeriod: PeriodStats?,
    val insights: List<Insight>,
    val sufficientData: Boolean
)
