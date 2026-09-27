package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BudgetRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var budgetDao: BudgetDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var accountDao: AccountDao
    private lateinit var repository: BudgetRepository

    private val monthKey = "2026-09"
    private val monthStart = BudgetCalendar.monthStart(monthKey)
    private val monthEnd = BudgetCalendar.monthEndExclusive(monthKey)

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        budgetDao = database.budgetDao()
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
        accountDao = database.accountDao()
        repository = BudgetRepository(
            budgetDao = budgetDao,
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            openHelper = database.openHelper
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun seedAccount(name: String = "Wallet"): Long = runBlocking {
        accountDao.insert(Account(name = name, type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 100_000L))
    }

    private fun seedCategory(name: String = "Food"): Long = runBlocking {
        categoryDao.insert(Category(name = name, direction = CATEGORY_DIRECTION_OUTFLOW))
    }

    private fun tx(
        accountId: Long,
        categoryId: Long?,
        amount: Long,
        direction: Int,
        timestamp: Long
    ) = Transaction(
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amount,
        direction = direction,
        transactionTimestamp = timestamp,
        note = "Test"
    )

    @Test
    fun createOverallBudget_persistsWithNullCategory() = runBlocking {
        val id = repository.createOverallBudget(monthKey, 50_000L)
        assertTrue(id > 0)
        val stored = budgetDao.getById(id)
        assertNotNull(stored)
        assertEquals(50_000L, stored!!.amountMinor)
        assertEquals(monthKey, stored.monthKey)
        assertNull(stored.categoryId)
    }

    @Test
    fun createCategoryBudget_persistsWithCategory() = runBlocking {
        val categoryId = seedCategory()
        val id = repository.createCategoryBudget(monthKey, categoryId, 20_000L)
        assertTrue(id > 0)
        val stored = budgetDao.getById(id)
        assertNotNull(stored)
        assertEquals(categoryId, stored!!.categoryId)
    }

    @Test
    fun duplicateOverallBudgetForSameMonth_rejected() = runBlocking {
        repository.createOverallBudget(monthKey, 50_000L)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createOverallBudget(monthKey, 60_000L) }
        }
        Unit
    }

    @Test
    fun duplicateCategoryBudgetForSameMonthAndCategory_rejected() = runBlocking {
        val categoryId = seedCategory()
        repository.createCategoryBudget(monthKey, categoryId, 20_000L)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createCategoryBudget(monthKey, categoryId, 30_000L) }
        }
        Unit
    }

    @Test
    fun sameCategoryBudgetDifferentMonth_allowed() = runBlocking {
        val categoryId = seedCategory()
        repository.createCategoryBudget(monthKey, categoryId, 20_000L)
        val nextMonth = BudgetCalendar.shiftMonth(monthKey, 1)
        val id = repository.createCategoryBudget(nextMonth, categoryId, 25_000L)
        assertTrue(id > 0)
    }

    @Test
    fun overallAndCategoryBudgetForSameMonth_allowed() = runBlocking {
        val categoryId = seedCategory()
        repository.createOverallBudget(monthKey, 100_000L)
        val id = repository.createCategoryBudget(monthKey, categoryId, 20_000L)
        assertTrue(id > 0)
    }

    @Test
    fun updateBudgetAmount_persistsNewAmount() = runBlocking {
        val id = repository.createOverallBudget(monthKey, 50_000L)
        val budget = budgetDao.getById(id)!!
        repository.updateBudgetAmount(budget, 75_000L)
        val updated = budgetDao.getById(id)!!
        assertEquals(75_000L, updated.amountMinor)
        assertTrue(updated.updatedTimestamp >= budget.updatedTimestamp)
    }

    @Test
    fun deleteBudget_removesIt() = runBlocking {
        val id = repository.createOverallBudget(monthKey, 50_000L)
        val budget = budgetDao.getById(id)!!
        repository.deleteBudget(budget)
        assertNull(budgetDao.getById(id))
    }

    @Test
    fun invalidAmount_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createOverallBudget(monthKey, 0L) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createOverallBudget(monthKey, -100L) }
        }
        Unit
    }

    @Test
    fun invalidMonth_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createOverallBudget("2026-13", 1_000L) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createOverallBudget("bad", 1_000L) }
        }
        Unit
    }

    @Test
    fun categoryBudgetForMissingCategory_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createCategoryBudget(monthKey, 999L, 1_000L) }
        }
        Unit
    }

    @Test
    fun spending_overallBudget_sumsOnlyOutflowInMonth() = runBlocking {
        val accountId = seedAccount()
        transactionDao.insertAll(
            listOf(
                tx(accountId, null, 10_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 1_000L),
                tx(accountId, null, 5_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 86_400_000L),
                tx(accountId, null, 99_000L, TRANSACTION_DIRECTION_INCOME, monthStart + 2_000L),
                tx(accountId, null, 7_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart - 1L),
                tx(accountId, null, 8_000L, TRANSACTION_DIRECTION_OUTFLOW, monthEnd)
            )
        )
        repository.createOverallBudget(monthKey, 50_000L)
        val data = repository.loadMonthData(monthKey)
        assertNotNull(data.overall)
        assertEquals(15_000L, data.overall!!.spentMinor)
    }

    @Test
    fun spending_categoryBudget_sumsOnlyMatchingCategory() = runBlocking {
        val accountId = seedAccount()
        val foodId = seedCategory("Food")
        val travelId = seedCategory("Travel")
        transactionDao.insertAll(
            listOf(
                tx(accountId, foodId, 4_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 1_000L),
                tx(accountId, foodId, 6_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 2_000L),
                tx(accountId, travelId, 9_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 3_000L),
                tx(accountId, null, 3_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 4_000L)
            )
        )
        repository.createOverallBudget(monthKey, 50_000L)
        val data = repository.loadMonthData(monthKey)
        assertEquals(22_000L, data.overall?.spentMinor)
    }

    @Test
    fun spending_archivedAccountTransactions_stillCounted() = runBlocking {
        val accountId = seedAccount()
        accountDao.archive(accountId)
        transactionDao.insert(
            tx(accountId, null, 12_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 1_000L)
        )
        repository.createOverallBudget(monthKey, 50_000L)
        val data = repository.loadMonthData(monthKey)
        assertEquals(12_000L, data.overall?.spentMinor)
    }

    @Test
    fun loadMonthData_withBudgets_computesViews() = runBlocking {
        val accountId = seedAccount()
        val foodId = seedCategory("Food")
        transactionDao.insertAll(
            listOf(
                tx(accountId, foodId, 4_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 1_000L),
                tx(accountId, null, 6_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 2_000L)
            )
        )
        repository.createOverallBudget(monthKey, 25_000L)
        repository.createCategoryBudget(monthKey, foodId, 5_000L)

        val data = repository.loadMonthData(monthKey)
        assertFalse(data.isEmpty)
        assertEquals(10_000L, data.overall?.spentMinor)
        assertEquals(15_000L, data.overall?.remainingMinor)
        assertEquals(40, data.overall?.percentUsed)
        assertEquals(BudgetStatus.NORMAL, data.overall?.status)

        val foodView = data.categoryBudgets.first { it.categoryName == "Food" }
        assertEquals(4_000L, foodView.spentMinor)
        assertEquals(1_000L, foodView.remainingMinor)
        assertEquals(80, foodView.percentUsed)
        assertEquals(BudgetStatus.APPROACHING, foodView.status)
    }

    @Test
    fun loadMonthData_emptyMonth_isEmptyWithNoBudgets() = runBlocking {
        val data = repository.loadMonthData(monthKey)
        assertTrue(data.isEmpty)
        assertNull(data.overall)
        assertTrue(data.categoryBudgets.isEmpty())
    }

    @Test
    fun loadMonthData_overBudget_statusAndNegativeRemaining() = runBlocking {
        val accountId = seedAccount()
        transactionDao.insert(
            tx(accountId, null, 25_000L, TRANSACTION_DIRECTION_OUTFLOW, monthStart + 1_000L)
        )
        repository.createOverallBudget(monthKey, 20_000L)
        val data = repository.loadMonthData(monthKey)
        assertEquals(BudgetStatus.OVER_BUDGET, data.overall?.status)
        assertEquals(-5_000L, data.overall?.remainingMinor)
        assertEquals(125, data.overall?.percentUsed)
    }

    @Test
    fun observeMonth_reactiveToBudgetChanges() = runBlocking {
        val initial = repository.observeMonth(monthKey).first()
        assertTrue(initial.budgets.isEmpty())
        repository.createOverallBudget(monthKey, 30_000L)
        val updated = repository.observeMonth(monthKey).first()
        assertEquals(1, updated.budgets.size)
    }
}
