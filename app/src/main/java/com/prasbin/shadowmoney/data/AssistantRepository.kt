package com.prasbin.shadowmoney.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.prasbin.shadowmoney.assistant.*
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.intelligence.InsightKind
import kotlinx.coroutines.flow.first

/**
 * Read-only data source for the Local Financial Assistant (Phase 11).
 *
 * Answers are assembled exclusively from existing authoritative sources:
 * [DashboardRepository] (balances), [BudgetRepository] (budget math),
 * [GoalRepository] (goal progress), [WorkRepository] (expected vs received),
 * [TelecomRepository] (expected monthly cost), [OpportunityRepository]
 * summaries and [IntelligenceRepository] (Phase 4 projections). Period
 * aggregates reuse the existing transaction DAO queries plus two scalar
 * aggregates added for this phase (income/count per period — genuinely
 * missing before). The per-category period breakdown is the same
 * deterministic SQL aggregation [BudgetRepository] uses for monthly
 * spending, parameterized for arbitrary assistant periods.
 *
 * There are NO write methods here. The class never reads or writes
 * secret-target storage in any way — that boundary lives elsewhere and is
 * never crossed by assistant code.
 */
class AssistantRepository(
    private val dashboardRepository: DashboardRepository,
    private val budgetRepository: BudgetRepository,
    private val goalRepository: GoalRepository,
    private val workRepository: WorkRepository,
    private val telecomRepository: TelecomRepository,
    private val opportunityRepository: OpportunityRepository,
    private val intelligenceRepository: IntelligenceRepository,
    private val transactionDao: TransactionDao,
    private val workItemDao: WorkItemDao,
    private val opportunityDao: OpportunityDao,
    private val telecomDao: TelecomDao,
    private val openHelper: SupportSQLiteOpenHelper,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun load(question: ClassifiedQuestion): AssistantData {
        val now = clock()
        val period = question.resolvedPeriod()
        val range = period?.let { AssistantTime.resolve(it, now) }

        val snapshot = dashboardRepository.observeSnapshot().first()
        val dashboard = dashboardRepository.loadDashboardData(snapshot)

        val categoryNames = snapshot.categories.associate { it.id to it.name }
        val accountNames = snapshot.accounts.associate { it.id to it.name }

        val periodTotals = range?.let { r ->
            AssistantPeriodTotals(
                incomeMinor = transactionDao.getIncomeTotalForPeriod(r.startInclusive, r.endExclusive),
                outflowMinor = transactionDao.getOutflowTotalForPeriod(r.startInclusive, r.endExclusive),
                transactionCount = transactionDao.getCountInPeriod(r.startInclusive, r.endExclusive),
                incomeCount = transactionDao.getCountInPeriodByDirection(
                    r.startInclusive, r.endExclusive, TRANSACTION_DIRECTION_INCOME
                ),
                outflowCount = transactionDao.getCountInPeriodByDirection(
                    r.startInclusive, r.endExclusive, TRANSACTION_DIRECTION_OUTFLOW
                ),
                categoryBreakdown = categoryOutflowForPeriod(
                    r.startInclusive, r.endExclusive, categoryNames
                )
            )
        }

        val windowTransactions = range?.let { r ->
            transactionDao.getTransactionsInWindow(r.startInclusive, r.endExclusive - 1).first()
                .map { it.toAssistantInfo(accountNames, categoryNames) }
        } ?: emptyList()

        val goalViews = goalRepository.loadGoalViews(snapshot.goals)

        val workItems = workItemDao.observeWorkItems(null, null).first()
        val workViews = workRepository.loadViews(workItems)

        val telecomInfo = loadTelecomInfo()

        val opportunities = opportunityDao.observeOpportunities(null, null, null, null).first()
        val opportunityInfo = if (opportunities.isEmpty()) {
            null
        } else {
            val summary = opportunityRepository.loadSummary()
            AssistantOpportunityInfo(
                activeCount = summary.activeCount,
                needsReviewCount = summary.needsReviewCount,
                totalExpectedAmountMinor = summary.totalExpectedAmountMinor,
                tracked = opportunities.map { it.title to OpportunityMath.statusLabel(it.status) }
            )
        }

        val currentKey = BudgetCalendar.currentMonthKey(now)
        val lastKey = BudgetCalendar.shiftMonth(currentKey, -1)

        return AssistantData(
            accounts = dashboard.accounts.map {
                AssistantAccountInfo(
                    name = it.account.name,
                    isActive = it.account.isActive,
                    balanceMinor = it.balanceMinor
                )
            },
            totalBalanceActiveMinor = dashboard.totalBalanceMinor,
            totalIncomeActiveMinor = dashboard.totalIncomeMinor,
            totalOutflowActiveMinor = dashboard.totalOutflowMinor,
            periodLabel = range?.label,
            periodRange = range,
            periodTotals = periodTotals,
            recentTransactions = dashboard.recentTransactions.map {
                it.transaction.toAssistantInfo(accountNames, categoryNames)
            },
            windowTransactions = windowTransactions,
            budgetCurrent = loadBudgetInfo(currentKey),
            budgetLast = loadBudgetInfo(lastKey),
            goals = goalViews.map { view ->
                AssistantGoalInfo(
                    name = view.goal.name,
                    targetMinor = view.goal.targetAmountMinor,
                    currentMinor = view.currentBalanceMinor,
                    percentUsed = view.percentUsed,
                    accountName = view.accountName
                )
            },
            work = workViews.map { view ->
                AssistantWorkInfo(
                    title = view.workItem.title,
                    statusLabel = WorkMath.statusLabel(view.workItem.status),
                    isActive = view.workItem.status == WORK_STATUS_ACTIVE,
                    expectedMinor = view.workItem.expectedAmountMinor,
                    receivedMinor = view.receivedMinor,
                    remainingExpectedMinor = view.remainingExpectedMinor
                )
            },
            telecom = telecomInfo,
            opportunities = opportunityInfo,
            projectionInsight = intelligenceRepository.loadReport(now)
                .insights.firstOrNull { it.kind == InsightKind.PROJECTION }
        )
    }

    private suspend fun loadBudgetInfo(monthKey: String): AssistantBudgetInfo {
        val data = budgetRepository.loadMonthData(monthKey)
        return AssistantBudgetInfo(
            monthKey = monthKey,
            monthLabel = BudgetCalendar.monthLabel(monthKey),
            hasAny = !data.isEmpty,
            overall = data.overall?.let { view ->
                toBudgetViewInfo("Overall", view)
            },
            categories = data.categoryBudgets.map { view ->
                toBudgetViewInfo(view.categoryName ?: "Category", view)
            }
        )
    }

    private fun toBudgetViewInfo(label: String, view: BudgetView): AssistantBudgetViewInfo =
        AssistantBudgetViewInfo(
            label = label,
            amountMinor = view.budget.amountMinor,
            spentMinor = view.spentMinor,
            remainingMinor = view.remainingMinor,
            percentUsed = view.percentUsed,
            status = view.status
        )

    private suspend fun loadTelecomInfo(): AssistantTelecomInfo? {
        val sims = telecomDao.observeSims().first()
        val packages = telecomDao.observePackages().first()
        val subscriptions = telecomDao.observeSubscriptions().first()
        if (sims.isEmpty() && packages.isEmpty() && subscriptions.isEmpty()) return null
        val summary = telecomRepository.loadSummaryFrom(sims, packages, subscriptions)
        return AssistantTelecomInfo(
            activeSimCount = summary.activeSimCount,
            activeSubscriptionCount = summary.activeSubscriptionCount,
            expectedMonthlyCostMinor = summary.expectedMonthlyCostMinor,
            upcomingRenewals = summary.upcomingRenewals.map {
                AssistantRenewalInfo(
                    simLabel = it.simLabel,
                    packageName = it.packageName,
                    renewalTimestamp = it.renewalTimestamp
                )
            }
        )
    }

    private fun com.prasbin.shadowmoney.data.model.Transaction.toAssistantInfo(
        accountNames: Map<Long, String>,
        categoryNames: Map<Long, String>
    ): AssistantTransactionInfo =
        AssistantTransactionInfo(
            timestamp = transactionTimestamp,
            amountMinor = amountMinor,
            direction = direction,
            note = note,
            accountName = accountNames[accountId] ?: "Unknown account",
            categoryName = categoryId?.let { categoryNames[it] }
        )

    private fun categoryOutflowForPeriod(
        start: Long,
        end: Long,
        categoryNames: Map<Long, String>
    ): List<AssistantCategorySpend> {
        val cursor = openHelper.readableDatabase.query(
            "SELECT categoryId, SUM(amountMinor) AS totalMinor " +
                "FROM transactions WHERE direction = 1 " +
                "AND transactionTimestamp >= ? AND transactionTimestamp < ? " +
                "GROUP BY categoryId",
            arrayOf(start.toString(), end.toString())
        )
        val rows = mutableListOf<AssistantCategorySpend>()
        cursor.use {
            while (cursor.moveToNext()) {
                val categoryId = if (cursor.isNull(0)) null else cursor.getLong(0)
                val total = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                rows.add(
                    AssistantCategorySpend(
                        name = categoryId?.let { categoryNames[it] } ?: "Uncategorized",
                        totalMinor = total
                    )
                )
            }
        }
        return rows.sortedByDescending { it.totalMinor }
    }
}
