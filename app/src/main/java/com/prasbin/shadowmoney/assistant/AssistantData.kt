package com.prasbin.shadowmoney.assistant

import com.prasbin.shadowmoney.data.BudgetStatus
import com.prasbin.shadowmoney.intelligence.Insight

/**
 * Immutable read-only snapshot the assistant answers from.
 *
 * Everything here is derived from existing authoritative sources
 * (DashboardRepository, BudgetRepository, GoalRepository, WorkRepository,
 * TelecomRepository, OpportunityRepository, IntelligenceRepository and the
 * transaction DAOs). The assistant never writes, and this model contains no
 * Secret Target value or any reference to secret-target storage.
 */

data class AssistantAccountInfo(
    val name: String,
    val isActive: Boolean,
    val balanceMinor: Long
)

data class AssistantCategorySpend(
    val name: String,
    val totalMinor: Long
)

data class AssistantTransactionInfo(
    val timestamp: Long,
    val amountMinor: Long,
    val direction: Int,
    val note: String,
    val accountName: String,
    val categoryName: String?
)

data class AssistantPeriodTotals(
    val incomeMinor: Long,
    val outflowMinor: Long,
    val transactionCount: Int,
    val incomeCount: Int,
    val outflowCount: Int,
    val categoryBreakdown: List<AssistantCategorySpend>
) {
    val netMinor: Long get() = incomeMinor - outflowMinor
}

data class AssistantBudgetViewInfo(
    val label: String,
    val amountMinor: Long,
    val spentMinor: Long,
    val remainingMinor: Long,
    val percentUsed: Int,
    val status: BudgetStatus
)

data class AssistantBudgetInfo(
    val monthKey: String,
    val monthLabel: String,
    val hasAny: Boolean,
    val overall: AssistantBudgetViewInfo?,
    val categories: List<AssistantBudgetViewInfo>
)

data class AssistantGoalInfo(
    val name: String,
    val targetMinor: Long,
    val currentMinor: Long,
    val percentUsed: Int,
    val accountName: String?
)

data class AssistantWorkInfo(
    val title: String,
    val statusLabel: String,
    val isActive: Boolean,
    val expectedMinor: Long,
    val receivedMinor: Long,
    val remainingExpectedMinor: Long
)

data class AssistantRenewalInfo(
    val simLabel: String,
    val packageName: String,
    val renewalTimestamp: Long
)

data class AssistantTelecomInfo(
    val activeSimCount: Int,
    val activeSubscriptionCount: Int,
    val expectedMonthlyCostMinor: Long,
    val upcomingRenewals: List<AssistantRenewalInfo>
)

data class AssistantOpportunityInfo(
    val activeCount: Int,
    val needsReviewCount: Int,
    val totalExpectedAmountMinor: Long,
    val tracked: List<Pair<String, String>>
)

data class AssistantData(
    val accounts: List<AssistantAccountInfo>,
    val totalBalanceActiveMinor: Long,
    val totalIncomeActiveMinor: Long,
    val totalOutflowActiveMinor: Long,
    val periodLabel: String?,
    val periodRange: AssistantPeriodRange?,
    val periodTotals: AssistantPeriodTotals?,
    val recentTransactions: List<AssistantTransactionInfo>,
    val windowTransactions: List<AssistantTransactionInfo>,
    val budgetCurrent: AssistantBudgetInfo,
    val budgetLast: AssistantBudgetInfo,
    val goals: List<AssistantGoalInfo>,
    val work: List<AssistantWorkInfo>,
    val telecom: AssistantTelecomInfo?,
    val opportunities: AssistantOpportunityInfo?,
    val projectionInsight: Insight?
)
