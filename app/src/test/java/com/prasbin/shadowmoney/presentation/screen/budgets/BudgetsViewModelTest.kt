package com.prasbin.shadowmoney.presentation.screen.budgets

import androidx.room.Room
import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.BudgetRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class BudgetsViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: BudgetRepository
    private lateinit var transactionDao: com.prasbin.shadowmoney.data.TransactionDao
    private lateinit var categoryDao: com.prasbin.shadowmoney.data.CategoryDao
    private lateinit var accountDao: com.prasbin.shadowmoney.data.AccountDao

    private val monthKey = "2026-09"
    private val monthStart = BudgetCalendar.monthStart(monthKey)

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = BudgetRepository(
            budgetDao = database.budgetDao(),
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            openHelper = database.openHelper
        )
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
        accountDao = database.accountDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun seedAccount(): Long = runBlocking {
        accountDao.insert(Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))
    }

    private fun seedCategory(name: String): Long = runBlocking {
        categoryDao.insert(Category(name = name, direction = CATEGORY_DIRECTION_OUTFLOW))
    }

    private fun tx(accountId: Long, categoryId: Long?, amount: Long, timestamp: Long) = Transaction(
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amount,
        direction = TRANSACTION_DIRECTION_OUTFLOW,
        transactionTimestamp = timestamp,
        note = "Test"
    )

    private fun viewModel(): BudgetsViewModel = BudgetsViewModel(repository)

    @Test
    fun emptyMonth_emitsEmptyState() = runBlocking {
        val viewModel = viewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Empty }
        }
        assertTrue(state is BudgetsUiState.Empty)
    }

    @Test
    fun createOverallBudget_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }

        viewModel.createOverallBudget(monthKey, 50_000L)

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.overall != null }
        } as BudgetsUiState.Content
        assertEquals(50_000L, state.month.overall?.budget?.amountMinor)
        assertEquals(0L, state.month.overall?.spentMinor)
    }

    @Test
    fun createCategoryBudget_reflectedInContent() = runBlocking {
        val categoryId = seedCategory("Food")
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }

        viewModel.createCategoryBudget(monthKey, categoryId, 10_000L)

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.categoryBudgets.isNotEmpty() }
        } as BudgetsUiState.Content
        assertEquals(1, state.month.categoryBudgets.size)
        assertEquals("Food", state.month.categoryBudgets.first().categoryName)
    }

    @Test
    fun transactionChange_refreshesSpentAmount() = runBlocking {
        val accountId = seedAccount()
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }

        viewModel.createOverallBudget(monthKey, 50_000L)
        withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.overall != null }
        }

        transactionDao.insert(tx(accountId, null, 8_000L, monthStart + 1_000L))

        val state = withTimeout(10_000) {
            viewModel.uiState.first {
                it is BudgetsUiState.Content && it.month.overall?.spentMinor == 8_000L
            }
        } as BudgetsUiState.Content
        assertEquals(8_000L, state.month.overall?.spentMinor)
        assertEquals(42_000L, state.month.overall?.remainingMinor)
        assertEquals(16, state.month.overall?.percentUsed)
    }

    @Test
    fun updateBudget_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }
        viewModel.createOverallBudget(monthKey, 50_000L)
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.overall != null }
        } as BudgetsUiState.Content

        viewModel.updateBudgetAmount(created.month.overall!!.budget, 80_000L)

        val updated = withTimeout(10_000) {
            viewModel.uiState.first {
                it is BudgetsUiState.Content && it.month.overall?.budget?.amountMinor == 80_000L
            }
        } as BudgetsUiState.Content
        assertEquals(80_000L, updated.month.overall?.budget?.amountMinor)
    }

    @Test
    fun deleteBudget_removesItFromContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }
        viewModel.createOverallBudget(monthKey, 50_000L)
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.overall != null }
        } as BudgetsUiState.Content

        viewModel.deleteBudget(created.month.overall!!.budget)

        val after = withTimeout(10_000) {
            viewModel.uiState.first {
                it is BudgetsUiState.Empty ||
                    (it is BudgetsUiState.Content && it.month.overall == null)
            }
        }
        assertTrue(after is BudgetsUiState.Empty || (after as? BudgetsUiState.Content)?.month?.overall == null)
    }

    @Test
    fun monthSelection_switchesDisplayedMonth() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }

        val previousMonth = BudgetCalendar.shiftMonth(monthKey, -1)
        viewModel.createOverallBudget(previousMonth, 20_000L)
        viewModel.selectMonth(previousMonth)

        val state = withTimeout(10_000) {
            viewModel.uiState.first {
                it is BudgetsUiState.Content && it.month.monthKey == previousMonth
            }
        } as BudgetsUiState.Content
        assertEquals(previousMonth, state.month.monthKey)
        assertNotNull(state.month.overall)
    }

    @Test
    fun duplicateBudgetError_surfacesErrorMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }
        viewModel.createOverallBudget(monthKey, 50_000L)
        withTimeout(10_000) {
            viewModel.uiState.first { it is BudgetsUiState.Content && it.month.overall != null }
        }

        viewModel.createOverallBudget(monthKey, 60_000L)

        val error = withTimeout(10_000) {
            viewModel.errorMessage.first { it != null }
        }
        assertNotNull(error)
        assertTrue(error!!.contains("already exists"))
    }

    @Test
    fun invalidAmountError_surfacesErrorMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is BudgetsUiState.Empty } }

        viewModel.createOverallBudget(monthKey, 0L)

        val error = withTimeout(10_000) {
            viewModel.errorMessage.first { it != null }
        }
        assertNotNull(error)
        assertTrue(error!!.contains("positive"))
    }
}
