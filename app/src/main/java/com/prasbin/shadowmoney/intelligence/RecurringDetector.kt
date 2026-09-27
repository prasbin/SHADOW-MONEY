package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction

const val RECURRING_MIN_OCCURRENCES = 3
const val RECURRING_AMOUNT_TOLERANCE_PERCENT = 10
const val RECURRING_MAX_INTERVAL_RATIO = 2
const val RECURRING_MIN_INTERVAL_DAYS = 1
const val RECURRING_MAX_INSIGHTS = 5

data class RecurringPattern(
    val note: String,
    val occurrences: Int,
    val typicalAmountMinor: Long,
    val typicalIntervalDays: Long,
    val totalMinor: Long,
    val categoryId: Long?
)

class RecurringDetector {

    fun detect(transactions: List<Transaction>, now: Long): List<RecurringPattern> {
        val outflows = transactions
            .filter {
                it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                    AnalysisWindow.contains(it.transactionTimestamp, now)
            }
            .filter { it.note.isNotBlank() }
        return outflows
            .groupBy { normalizeNote(it.note) }
            .values
            .filter { it.size >= RECURRING_MIN_OCCURRENCES }
            .mapNotNull { analyzeGroup(it) }
            .sortedByDescending { it.totalMinor }
            .take(RECURRING_MAX_INSIGHTS)
    }

    private fun analyzeGroup(group: List<Transaction>): RecurringPattern? {
        val amounts = group.map { it.amountMinor }
        val minAmount = amounts.min()
        val maxAmount = amounts.max()
        if (maxAmount * 100 > minAmount * (100 + RECURRING_AMOUNT_TOLERANCE_PERCENT)) return null

        val sorted = group.sortedBy { it.transactionTimestamp }
        val gaps = sorted.zipWithNext { a, b ->
            (b.transactionTimestamp - a.transactionTimestamp) / MILLIS_PER_DAY
        }
        if (gaps.isEmpty()) return null
        val minGap = gaps.min()
        val maxGap = gaps.max()
        if (minGap < RECURRING_MIN_INTERVAL_DAYS) return null
        if (maxGap > minGap * RECURRING_MAX_INTERVAL_RATIO) return null

        return RecurringPattern(
            note = normalizeNote(sorted.first().note),
            occurrences = group.size,
            typicalAmountMinor = median(amounts),
            typicalIntervalDays = median(gaps),
            totalMinor = amounts.sum(),
            categoryId = sorted.first().categoryId
        )
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    private fun normalizeNote(note: String): String =
        note.trim().lowercase().replace(Regex("\\s+"), " ")
}
