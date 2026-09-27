package com.prasbin.shadowmoney.intelligence

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction

const val PROJECTION_DAYS = 30L
const val PROJECTION_MIN_HISTORY_DAYS = 7L

data class MonthlyProjection(
    val projectedMonthlyOutflowMinor: Long,
    val historyDays: Long,
    val transactionCount: Int
)

class ProjectionEngine {

    fun projectMonthlyOutflow(transactions: List<Transaction>, now: Long): MonthlyProjection? {
        val inWindow = transactions.filter {
            it.direction == TRANSACTION_DIRECTION_OUTFLOW &&
                AnalysisWindow.contains(it.transactionTimestamp, now)
        }
        if (inWindow.isEmpty()) return null
        val historyDays = (now - inWindow.minOf { it.transactionTimestamp }) / MILLIS_PER_DAY
        if (historyDays < PROJECTION_MIN_HISTORY_DAYS) return null
        val total = inWindow.sumOf { it.amountMinor }
        return MonthlyProjection(
            projectedMonthlyOutflowMinor = total * PROJECTION_DAYS / historyDays,
            historyDays = historyDays,
            transactionCount = inWindow.size
        )
    }
}
