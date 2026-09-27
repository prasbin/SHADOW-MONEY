package com.prasbin.shadowmoney.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class BudgetView(
    val budget: Budget,
    val spentMinor: Long,
    val remainingMinor: Long,
    val percentUsed: Int,
    val status: BudgetStatus,
    val categoryName: String?
)

data class BudgetMonthSnapshot(
    val budgets: List<Budget>,
    val categories: List<Category>
)

data class BudgetMonthData(
    val monthKey: String,
    val overall: BudgetView?,
    val categoryBudgets: List<BudgetView>,
    val isEmpty: Boolean
)

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val openHelper: SupportSQLiteOpenHelper
) {

    fun observeMonth(monthKey: String): Flow<BudgetMonthSnapshot> = combine(
        budgetDao.getByMonth(monthKey),
        transactionDao.observeTransactionCount(),
        categoryDao.getAll()
    ) { budgets, _, categories ->
        BudgetMonthSnapshot(budgets, categories)
    }

    fun observeChanges(): Flow<Unit> = combine(
        transactionDao.observeTransactionCount(),
        categoryDao.getAll(),
        budgetDao.observeBudgetCount()
    ) { _, _, _ -> Unit }

    suspend fun loadMonthData(monthKey: String): BudgetMonthData {
        val snapshot = budgetDao.getByMonthOnce(monthKey)
        val categories = categoryDao.getAll().first()
        val categoryNames = categories.associate { it.id to it.name }

        val start = BudgetCalendar.monthStart(monthKey)
        val end = BudgetCalendar.monthEndExclusive(monthKey)
        val overallSpent = transactionDao.getOutflowTotalForPeriod(start, end)
        val categorySpent = queryCategorySpending(start, end)

        val overallBudget = snapshot.firstOrNull { it.categoryId == null }
        val overallView = overallBudget?.let { budget ->
            toView(budget, overallSpent, null)
        }

        val categoryViews = snapshot
            .filter { it.categoryId != null }
            .map { budget ->
                val spent = budget.categoryId?.let { categorySpent[it] } ?: 0L
                toView(budget, spent, budget.categoryId?.let { categoryNames[it] })
            }
            .sortedByDescending { it.budget.amountMinor }

        return BudgetMonthData(
            monthKey = monthKey,
            overall = overallView,
            categoryBudgets = categoryViews,
            isEmpty = snapshot.isEmpty()
        )
    }

    private fun toView(budget: Budget, spentMinor: Long, categoryName: String?): BudgetView {
        return BudgetView(
            budget = budget,
            spentMinor = spentMinor,
            remainingMinor = BudgetMath.remainingMinor(budget.amountMinor, spentMinor),
            percentUsed = BudgetMath.percentUsed(spentMinor, budget.amountMinor),
            status = BudgetMath.statusFor(spentMinor, budget.amountMinor),
            categoryName = categoryName
        )
    }

    private fun queryCategorySpending(start: Long, end: Long): Map<Long, Long> {
        val cursor = openHelper.readableDatabase.query(
            "SELECT categoryId, SUM(amountMinor) AS totalMinor " +
                "FROM transactions WHERE direction = 1 " +
                "AND transactionTimestamp >= ? AND transactionTimestamp < ? " +
                "GROUP BY categoryId",
            arrayOf(start.toString(), end.toString())
        )
        val result = mutableMapOf<Long, Long>()
        cursor.use {
            while (cursor.moveToNext()) {
                if (cursor.isNull(0)) continue
                result[cursor.getLong(0)] = if (cursor.isNull(1)) 0L else cursor.getLong(1)
            }
        }
        return result
    }

    suspend fun findOverallBudgetForMonth(monthKey: String): Budget? =
        budgetDao.getByMonthOnce(monthKey).firstOrNull { it.categoryId == null }

    suspend fun createOverallBudget(monthKey: String, amountMinor: Long): Long {
        validateAmount(amountMinor)
        validateMonthKey(monthKey)
        if (findOverallBudgetForMonth(monthKey) != null) {
            throw IllegalArgumentException("An overall budget for this month already exists")
        }
        val now = System.currentTimeMillis()
        return budgetDao.insert(
            Budget(
                amountMinor = amountMinor,
                monthKey = monthKey,
                categoryId = null,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun createCategoryBudget(monthKey: String, categoryId: Long, amountMinor: Long): Long {
        validateAmount(amountMinor)
        validateMonthKey(monthKey)
        if (categoryDao.getById(categoryId) == null) {
            throw IllegalArgumentException("Category does not exist")
        }
        val existing = budgetDao.getByMonthOnce(monthKey)
            .firstOrNull { it.categoryId == categoryId }
        if (existing != null) {
            throw IllegalArgumentException("A budget for this category and month already exists")
        }
        val now = System.currentTimeMillis()
        return budgetDao.insert(
            Budget(
                amountMinor = amountMinor,
                monthKey = monthKey,
                categoryId = categoryId,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun updateBudgetAmount(budget: Budget, newAmountMinor: Long) {
        validateAmount(newAmountMinor)
        budgetDao.update(budget.copy(amountMinor = newAmountMinor, updatedTimestamp = System.currentTimeMillis()))
    }

    suspend fun deleteBudget(budget: Budget) {
        budgetDao.delete(budget)
    }

    private fun validateAmount(amountMinor: Long) {
        if (amountMinor <= 0L) {
            throw IllegalArgumentException("Budget amount must be positive")
        }
    }

    private fun validateMonthKey(monthKey: String) {
        if (!monthKey.matches(Regex("^\\d{4}-\\d{2}$"))) {
            throw IllegalArgumentException("Invalid month")
        }
        try {
            BudgetCalendar.monthStart(monthKey)
        } catch (error: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Invalid month")
        }
    }
}
