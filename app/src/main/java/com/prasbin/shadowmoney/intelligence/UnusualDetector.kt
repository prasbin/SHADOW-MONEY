package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction

const val UNUSUAL_RECENT_DAYS = 30L
const val UNUSUAL_BASELINE_MULTIPLIER_NUM = 3
const val UNUSUAL_BASELINE_MULTIPLIER_DEN = 2
const val UNUSUAL_MIN_AMOUNT_MINOR = 50_000L
const val UNUSUAL_MIN_BASELINE_TRANSACTIONS = 2
const val UNUSUAL_TRANSACTION_MULTIPLIER = 3
const val UNUSUAL_MIN_CATEGORY_TRANSACTIONS = 3
const val UNUSUAL_MAX_INSIGHTS = 5

data class CategoryAnomaly(
    val categoryId: Long?,
    val recentTotalMinor: Long,
    val typical30DayMinor: Long
)

data class TransactionAnomaly(
    val transaction: Transaction,
    val categoryAverageMinor: Long
)

class UnusualDetector {

    fun detectCategorySpending(transactions: List<Transaction>, now: Long): List<CategoryAnomaly> {
        val windowStart = AnalysisWindow.start(now)
        val recentStart = now - UNUSUAL_RECENT_DAYS * MILLIS_PER_DAY
        val baselineDays = (recentStart - windowStart) / MILLIS_PER_DAY
        if (baselineDays <= 0L) return emptyList()

        val result = mutableListOf<CategoryAnomaly>()
        val byCategory = transactions
            .filter {
                it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                    it.transactionTimestamp >= windowStart
            }
            .groupBy { it.categoryId }

        for ((categoryId, txs) in byCategory) {
            val baseline = txs.filter { it.transactionTimestamp < recentStart }
            if (baseline.size < UNUSUAL_MIN_BASELINE_TRANSACTIONS) continue
            val baselineTotal = baseline.sumOf { it.amountMinor }
            if (baselineTotal <= 0L) continue
            val typical30 = baselineTotal * UNUSUAL_RECENT_DAYS / baselineDays
            val recentTotal = txs.filter { it.transactionTimestamp >= recentStart }
                .sumOf { it.amountMinor }
            if (recentTotal < UNUSUAL_MIN_AMOUNT_MINOR) continue
            if (recentTotal * UNUSUAL_BASELINE_MULTIPLIER_DEN > typical30 * UNUSUAL_BASELINE_MULTIPLIER_NUM) {
                result.add(CategoryAnomaly(categoryId, recentTotal, typical30))
            }
        }
        return result.sortedByDescending { it.recentTotalMinor }.take(UNUSUAL_MAX_INSIGHTS)
    }

    fun detectLargeTransactions(transactions: List<Transaction>, now: Long): List<TransactionAnomaly> {
        val inWindow = transactions.filter {
            it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                AnalysisWindow.contains(it.transactionTimestamp, now)
        }
        val averages = inWindow
            .groupBy { it.categoryId }
            .mapValues { (_, txs) ->
                if (txs.size >= UNUSUAL_MIN_CATEGORY_TRANSACTIONS) {
                    txs.sumOf { it.amountMinor } / txs.size
                } else {
                    null
                }
            }
        return inWindow
            .filter { tx ->
                val average = averages[tx.categoryId]
                average != null &&
                    tx.amountMinor >= UNUSUAL_MIN_AMOUNT_MINOR &&
                    tx.amountMinor > average * UNUSUAL_TRANSACTION_MULTIPLIER
            }
            .map { TransactionAnomaly(it, averages[it.categoryId]!!) }
            .sortedByDescending { it.transaction.amountMinor }
            .take(UNUSUAL_MAX_INSIGHTS)
    }
}
