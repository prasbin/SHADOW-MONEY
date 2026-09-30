package com.prasbin.shadowmoney.presentation.theme

import androidx.compose.ui.graphics.Color
import com.prasbin.shadowmoney.data.BudgetStatus

fun budgetStatusLabel(status: BudgetStatus): String = when (status) {
    BudgetStatus.NORMAL -> "Under budget"
    BudgetStatus.APPROACHING -> "Approaching limit"
    BudgetStatus.OVER_BUDGET -> "Over budget"
}

fun budgetStatusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.NORMAL -> NeonGreen
    BudgetStatus.APPROACHING -> WarningAmber
    BudgetStatus.OVER_BUDGET -> ErrorRed
}
