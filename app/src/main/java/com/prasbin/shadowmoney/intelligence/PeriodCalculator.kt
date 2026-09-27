package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction

class PeriodCalculator {

    fun periodStats(transactions: List<Transaction>, now: Long): PeriodStats =
        statsFor(transactions.filter { AnalysisWindow.contains(it.transactionTimestamp, now) })

    fun previousPeriodStats(transactions: List<Transaction>, now: Long): PeriodStats =
        statsFor(
            transactions.filter {
                it.transactionTimestamp in AnalysisWindow.previousStart(now) until AnalysisWindow.start(now)
            }
        )

    private fun statsFor(transactions: List<Transaction>): PeriodStats {
        var income = 0L
        var outflow = 0L
        var incomeCount = 0
        var outflowCount = 0
        for (transaction in transactions) {
            when (transaction.direction) {
                TRANSACTION_DIRECTION_INCOME -> {
                    income += transaction.amountMinor
                    incomeCount++
                }
                TRANSACTION_DIRECTION_OUTFLOW -> {
                    outflow += transaction.amountMinor
                    outflowCount++
                }
            }
        }
        return PeriodStats(
            incomeMinor = income,
            outflowMinor = outflow,
            transactionCount = transactions.size,
            incomeCount = incomeCount,
            outflowCount = outflowCount
        )
    }

    fun categorySpending(transactions: List<Transaction>, now: Long): Map<Long?, Long> =
        transactions
            .filter {
                it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                    AnalysisWindow.contains(it.transactionTimestamp, now)
            }
            .groupBy({ it.categoryId }, { transaction -> transaction.amountMinor })
            .mapValues { (_, amounts) -> amounts.sum() }

    fun accountSpending(transactions: List<Transaction>, now: Long): Map<Long, Long> =
        transactions
            .filter {
                it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                    AnalysisWindow.contains(it.transactionTimestamp, now)
            }
            .groupBy({ it.accountId }, { transaction -> transaction.amountMinor })
            .mapValues { (_, amounts) -> amounts.sum() }

    fun trendPercent(current: Long, previous: Long): Long? =
        if (previous == 0L) null else (current - previous) * 100 / previous

    fun historyDays(transactions: List<Transaction>, now: Long): Long {
        val earliest = transactions
            .filter { AnalysisWindow.contains(it.transactionTimestamp, now) }
            .minOfOrNull { it.transactionTimestamp } ?: return 0L
        return (now - earliest) / MILLIS_PER_DAY
    }
}
