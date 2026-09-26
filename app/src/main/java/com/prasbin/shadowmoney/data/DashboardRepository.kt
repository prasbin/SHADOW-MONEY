package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.Transaction
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

const val DASHBOARD_RECENT_LIMIT = 20

data class AccountBalanceView(
    val account: Account,
    val balanceMinor: Long
)

data class CategorySpendView(
    val categoryId: Long?,
    val name: String,
    val totalMinor: Long
)

data class GoalProgressView(
    val goal: Goal,
    val savedMinor: Long?,
    val progressPercent: Int?
)

data class RecentTransactionView(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String
)

data class DashboardSnapshot(
    val accounts: List<Account>,
    val categories: List<Category>,
    val goals: List<Goal>
)

data class DashboardData(
    val totalBalanceMinor: Long,
    val totalIncomeMinor: Long,
    val totalOutflowMinor: Long,
    val accounts: List<AccountBalanceView>,
    val recentTransactions: List<RecentTransactionView>,
    val categoryOutflow: List<CategorySpendView>,
    val goals: List<GoalProgressView>,
    val isEmpty: Boolean
)

class DashboardRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val goalDao: GoalDao,
    private val openHelper: SupportSQLiteOpenHelper
) {

    fun observeSnapshot(): Flow<DashboardSnapshot> = combine(
        accountDao.getAll(),
        transactionDao.observeTransactionCount(),
        categoryDao.getAll(),
        goalDao.getAllActive()
    ) { accounts, _, categories, goals ->
        DashboardSnapshot(accounts, categories, goals)
    }

    suspend fun loadDashboardData(snapshot: DashboardSnapshot): DashboardData {
        val activeAccounts = snapshot.accounts.filter { it.isActive }

        val totalIncome = transactionDao.getTotalIncomeMinorForActiveAccounts()
        val totalOutflow = transactionDao.getTotalOutflowMinorForActiveAccounts()
        val openingTotal = activeAccounts.sumOf { it.openingBalanceMinor }
        val totalBalance = Money.balanceMinor(openingTotal, totalIncome, totalOutflow)

        val accountNames = snapshot.accounts.associate { it.id to it.name }
        val categoryNames = snapshot.categories.associate { it.id to it.name }

        val accountBalances = snapshot.accounts.map { account ->
            val income = transactionDao.getTotalIncomeMinor(account.id)
            val outflow = transactionDao.getTotalOutflowMinor(account.id)
            AccountBalanceView(
                account = account,
                balanceMinor = Money.balanceMinor(account.openingBalanceMinor, income, outflow)
            )
        }

        val recent = transactionDao.getRecent(DASHBOARD_RECENT_LIMIT).first()
        val recentViews = recent.map { transaction ->
            RecentTransactionView(
                transaction = transaction,
                accountName = accountNames[transaction.accountId] ?: "Unknown account",
                categoryName = transaction.categoryId?.let { categoryNames[it] } ?: "Uncategorized"
            )
        }

        val categoryOutflow = queryCategoryOutflow(categoryNames)

        val goalViews = snapshot.goals.map { goal ->
            val saved = goal.accountId?.let { accountId ->
                val account = snapshot.accounts.firstOrNull { it.id == accountId } ?: return@let null
                Money.balanceMinor(
                    account.openingBalanceMinor,
                    transactionDao.getTotalIncomeMinor(accountId),
                    transactionDao.getTotalOutflowMinor(accountId)
                )
            }
            val percent = when {
                goal.targetAmountMinor <= 0 -> null
                saved == null -> null
                else -> ((saved * 100) / goal.targetAmountMinor).toInt().coerceIn(0, 100)
            }
            GoalProgressView(goal = goal, savedMinor = saved, progressPercent = percent)
        }

        return DashboardData(
            totalBalanceMinor = totalBalance,
            totalIncomeMinor = totalIncome,
            totalOutflowMinor = totalOutflow,
            accounts = accountBalances,
            recentTransactions = recentViews,
            categoryOutflow = categoryOutflow,
            goals = goalViews,
            isEmpty = snapshot.accounts.isEmpty() && recent.isEmpty() && snapshot.goals.isEmpty()
        )
    }

    private fun queryCategoryOutflow(
        categoryNames: Map<Long, String>
    ): List<CategorySpendView> {
        val cursor = openHelper.readableDatabase.query(
            "SELECT categoryId, SUM(amountMinor) AS totalMinor " +
                "FROM transactions WHERE direction = 1 GROUP BY categoryId"
        )
        val rows = mutableListOf<CategorySpendView>()
        cursor.use {
            while (cursor.moveToNext()) {
                val categoryId = if (cursor.isNull(0)) null else cursor.getLong(0)
                val totalMinor = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                rows.add(
                    CategorySpendView(
                        categoryId = categoryId,
                        name = categoryId?.let { categoryNames[it] } ?: "Uncategorized",
                        totalMinor = totalMinor
                    )
                )
            }
        }
        return rows.sortedByDescending { it.totalMinor }
    }
}
