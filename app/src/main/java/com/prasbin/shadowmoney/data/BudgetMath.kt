package com.prasbin.shadowmoney.data

enum class BudgetStatus { NORMAL, APPROACHING, OVER_BUDGET }

const val BUDGET_APPROACHING_THRESHOLD_PERCENT = 50
const val BUDGET_OVER_THRESHOLD_PERCENT = 100

object BudgetMath {

    fun percentUsed(spentMinor: Long, budgetMinor: Long): Int {
        if (budgetMinor <= 0L) return 0
        return ((spentMinor * 100) / budgetMinor).toInt()
    }

    fun statusFor(spentMinor: Long, budgetMinor: Long): BudgetStatus {
        if (budgetMinor <= 0L) {
            return if (spentMinor > 0L) BudgetStatus.OVER_BUDGET else BudgetStatus.NORMAL
        }
        val percent = (spentMinor * 100) / budgetMinor
        return when {
            percent < BUDGET_APPROACHING_THRESHOLD_PERCENT -> BudgetStatus.NORMAL
            percent < BUDGET_OVER_THRESHOLD_PERCENT -> BudgetStatus.APPROACHING
            else -> BudgetStatus.OVER_BUDGET
        }
    }

    fun remainingMinor(budgetMinor: Long, spentMinor: Long): Long =
        budgetMinor - spentMinor
}
