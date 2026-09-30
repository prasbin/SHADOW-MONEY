package com.prasbin.shadowmoney.dashboard

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.prasbin.shadowmoney.data.*
import com.prasbin.shadowmoney.data.model.*
import com.prasbin.shadowmoney.presentation.screen.dashboard.DashboardUiState
import com.prasbin.shadowmoney.presentation.screen.dashboard.DashboardViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
class DashboardViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var goalDao: GoalDao
    private lateinit var budgetDao: BudgetDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        goalDao = database.goalDao()
        budgetDao = database.budgetDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModelWith(repository: DashboardRepository): DashboardViewModel {
        val store = ViewModelStore()
        return ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return DashboardViewModel(repository) as T
                }
            }
        ).get(DashboardViewModel::class.java)
    }

    private fun budgetRepository(dao: TransactionDao): BudgetRepository =
        BudgetRepository(budgetDao, dao, categoryDao, database.openHelper)

    private fun defaultViewModel(): DashboardViewModel =
        viewModelWith(
            DashboardRepository(
                accountDao,
                categoryDao,
                transactionDao,
                goalDao,
                budgetRepository(transactionDao),
                database.openHelper
            )
        )

    private fun awaitState(
        viewModel: DashboardViewModel,
        predicate: (DashboardUiState) -> Boolean
    ): DashboardUiState = runBlocking {
        withTimeout(10_000) {
            viewModel.uiState.first(predicate)
        }
    }

    @Test
    fun emptyDatabase_emitsEmptyState_notZeroContent() {
        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) { it is DashboardUiState.Empty }
        assertTrue(state is DashboardUiState.Empty)
        assertFalse(state is DashboardUiState.Content)
    }

    @Test
    fun accountBalance_derivesOpeningPlusIncomeMinusOutflow() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10_000L)
        )
        transactionDao.insertAll(
            listOf(
                Transaction(accountId = accountId, amountMinor = 3_000L, direction = TRANSACTION_DIRECTION_INCOME),
                Transaction(accountId = accountId, amountMinor = 2_000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
            )
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.totalBalanceMinor == 11_000L
        } as DashboardUiState.Content

        assertEquals(11_000L, state.totalBalanceMinor)
        assertEquals(3_000L, state.totalIncomeMinor)
        assertEquals(2_000L, state.totalOutflowMinor)
        assertEquals(1, state.accounts.size)
        assertEquals(11_000L, state.accounts.first().balanceMinor)
    }

    @Test
    fun archivedAccount_excludedFromSummaryTotals_butStillListed() = runBlocking {
        val archivedId = accountDao.insert(
            Account(name = "Old Bank", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 1_000_000L)
        )
        transactionDao.insertAll(
            listOf(
                Transaction(accountId = archivedId, amountMinor = 500_000L, direction = TRANSACTION_DIRECTION_INCOME),
                Transaction(accountId = archivedId, amountMinor = 200_000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
            )
        )
        accountDao.archive(archivedId)

        val activeId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 1_000L)
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.totalBalanceMinor == 1_000L
        } as DashboardUiState.Content

        assertEquals(1_000L, state.totalBalanceMinor)
        assertEquals(0L, state.totalIncomeMinor)
        assertEquals(0L, state.totalOutflowMinor)
        assertEquals(2, state.accounts.size)
        val archivedView = state.accounts.first { it.account.id == archivedId }
        assertFalse(archivedView.account.isActive)
        assertEquals(1_300_000L, archivedView.balanceMinor)
        val activeView = state.accounts.first { it.account.id == activeId }
        assertEquals(1_000L, activeView.balanceMinor)
    }

    @Test
    fun recentTransactions_limitedTo20_andOrderedNewestFirst() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val base = 1_700_000_000_000L
        val transactions = (0 until 25).map { index ->
            Transaction(
                accountId = accountId,
                amountMinor = 100L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = base + index * 1_000L
            )
        }
        transactionDao.insertAll(transactions)

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.recentTransactions.size == 20
        } as DashboardUiState.Content

        assertEquals(20, state.recentTransactions.size)
        assertEquals(
            base + 24 * 1_000L,
            state.recentTransactions.first().transaction.transactionTimestamp
        )
        assertEquals(
            base + 5 * 1_000L,
            state.recentTransactions.last().transaction.transactionTimestamp
        )
    }

    @Test
    fun categoryOutflow_aggregatesOnlyOutflow_andHandlesUncategorized() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val foodId = categoryDao.insert(Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW))
        val travelId = categoryDao.insert(Category(name = "Travel", direction = CATEGORY_DIRECTION_OUTFLOW))
        transactionDao.insertAll(
            listOf(
                Transaction(accountId = accountId, categoryId = foodId, amountMinor = 500L, direction = TRANSACTION_DIRECTION_OUTFLOW),
                Transaction(accountId = accountId, categoryId = foodId, amountMinor = 700L, direction = TRANSACTION_DIRECTION_OUTFLOW),
                Transaction(accountId = accountId, categoryId = travelId, amountMinor = 300L, direction = TRANSACTION_DIRECTION_OUTFLOW),
                Transaction(accountId = accountId, categoryId = null, amountMinor = 900L, direction = TRANSACTION_DIRECTION_OUTFLOW),
                Transaction(accountId = accountId, categoryId = foodId, amountMinor = 9_999L, direction = TRANSACTION_DIRECTION_INCOME)
            )
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.categoryOutflow.size == 3
        } as DashboardUiState.Content

        val byName = state.categoryOutflow.associateBy { it.name }
        assertEquals(1_200L, byName["Food"]?.totalMinor)
        assertEquals(300L, byName["Travel"]?.totalMinor)
        assertEquals(900L, byName["Uncategorized"]?.totalMinor)
        assertNotNull(byName["Food"]?.categoryId)
        assertNull(byName["Uncategorized"]?.categoryId)
        assertTrue(state.categoryOutflow.first().totalMinor >= state.categoryOutflow.last().totalMinor)
    }

    @Test
    fun deletedCategory_setNull_doesNotCrash_andMarksUncategorized() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val categoryId = categoryDao.insert(Category(name = "Old Category", direction = CATEGORY_DIRECTION_OUTFLOW))
        transactionDao.insert(
            Transaction(accountId = accountId, categoryId = categoryId, amountMinor = 450L, direction = TRANSACTION_DIRECTION_OUTFLOW)
        )
        categoryDao.delete(categoryDao.getById(categoryId)!!)

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.categoryOutflow.any { view -> view.name == "Uncategorized" }
        } as DashboardUiState.Content

        val uncategorized = state.categoryOutflow.first { it.name == "Uncategorized" }
        assertNull(uncategorized.categoryId)
        assertEquals(450L, uncategorized.totalMinor)
        assertEquals("Uncategorized", state.recentTransactions.first().categoryName)
    }

    @Test
    fun goalProgress_computedFromStoredGoalAndLinkedAccount() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Savings", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 10_000L)
        )
        transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 500L, direction = TRANSACTION_DIRECTION_INCOME)
        )
        goalDao.insert(
            Goal(name = "Emergency Fund", targetAmountMinor = 21_000L, accountId = accountId)
        )
        goalDao.insert(
            Goal(name = "Unassigned Goal", targetAmountMinor = 5_000L, accountId = null)
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.goals.size == 2
        } as DashboardUiState.Content

        val assigned = state.goals.first { it.goal.name == "Emergency Fund" }
        assertEquals(10_500L, assigned.savedMinor)
        assertEquals(50, assigned.progressPercent)

        val unassigned = state.goals.first { it.goal.name == "Unassigned Goal" }
        assertNull(unassigned.savedMinor)
        assertNull(unassigned.progressPercent)
    }

    @Test
    fun databaseError_emitsErrorState_notContentOrSilentZero() {
        val failingDao = object : TransactionDao {
            override fun getAll(): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun getByAccountId(accountId: Long): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun getByCategoryId(categoryId: Long): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override suspend fun getById(id: Long): Transaction? =
                throw RuntimeException("Simulated database failure")
            override suspend fun insert(transaction: Transaction): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun insertAll(transactions: List<Transaction>) =
                throw RuntimeException("Simulated database failure")
            override suspend fun update(transaction: Transaction) =
                throw RuntimeException("Simulated database failure")
            override suspend fun delete(transaction: Transaction) =
                throw RuntimeException("Simulated database failure")
            override suspend fun getTotalIncomeMinor(accountId: Long): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getTotalOutflowMinor(accountId: Long): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getCount(): Int =
                throw RuntimeException("Simulated database failure")
            override fun getRecent(limit: Int): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun getTransactionsInWindow(start: Long, end: Long): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun observeTransactionCount(): Flow<Int> =
                flow { throw RuntimeException("Simulated database failure") }
            override suspend fun getTotalIncomeMinorForActiveAccounts(): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getTotalOutflowMinorForActiveAccounts(): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getOutflowTotalForPeriod(start: Long, end: Long): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getIncomeTotalForPeriod(start: Long, end: Long): Long =
                throw RuntimeException("Simulated database failure")
            override suspend fun getCountInPeriod(start: Long, end: Long): Int =
                throw RuntimeException("Simulated database failure")
            override suspend fun getCountInPeriodByDirection(start: Long, end: Long, direction: Int): Int =
                throw RuntimeException("Simulated database failure")
            override suspend fun getOutflowTotalForCategoryPeriod(categoryId: Long, start: Long, end: Long): Long =
                throw RuntimeException("Simulated database failure")
            override fun observeReceivedForWorkItem(workItemId: Long): Flow<Long> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun observeTransactionsForWorkItem(workItemId: Long): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override fun observeLinkableTransactions(workItemId: Long?): Flow<List<Transaction>> =
                flow { throw RuntimeException("Simulated database failure") }
            override suspend fun setWorkItemId(transactionId: Long, workItemId: Long?) =
                throw RuntimeException("Simulated database failure")
        }

        val viewModel = viewModelWith(
            DashboardRepository(
                accountDao,
                categoryDao,
                failingDao,
                goalDao,
                budgetRepository(failingDao),
                database.openHelper
            )
        )
        val state = awaitState(viewModel) { it is DashboardUiState.Error }
        assertTrue(state is DashboardUiState.Error)
        assertTrue((state as DashboardUiState.Error).message.isNotBlank())
    }

    @Test
    fun reactiveUpdate_newTransactionReflectedInState() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10_000L)
        )
        val viewModel = defaultViewModel()
        awaitState(viewModel) {
            it is DashboardUiState.Content && it.totalBalanceMinor == 10_000L
        }

        transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 5_000L, direction = TRANSACTION_DIRECTION_INCOME)
        )

        val updated = awaitState(viewModel) {
            it is DashboardUiState.Content && it.totalBalanceMinor == 15_000L
        } as DashboardUiState.Content
        assertEquals(15_000L, updated.totalBalanceMinor)
        assertEquals(5_000L, updated.totalIncomeMinor)
        assertEquals(1, updated.recentTransactions.size)
    }

    @Test
    fun recentTransaction_resolvesAccountAndCategoryNames() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "My Bank", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 0L)
        )
        val categoryId = categoryDao.insert(Category(name = "Salary", direction = CATEGORY_DIRECTION_INCOME))
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 25_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                note = "Monthly salary"
            )
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.recentTransactions.size == 1
        } as DashboardUiState.Content

        val row = state.recentTransactions.first()
        assertEquals("My Bank", row.accountName)
        assertEquals("Salary", row.categoryName)
        assertEquals("Monthly salary", row.transaction.note)
    }

    private fun currentMonthKey(): String = BudgetCalendar.currentMonthKey()

    @Test
    fun noBudget_reportsNullOverallBudget_notFakeZeroBudget() = runBlocking {
        accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) { it is DashboardUiState.Content } as DashboardUiState.Content

        assertNull(state.overallBudget)
    }

    @Test
    fun budgetWithNoSpending_spentZero_remainingEqualsBudget() = runBlocking {
        accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        budgetRepository(transactionDao).createOverallBudget(currentMonthKey(), 50_000L)

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.overallBudget != null
        } as DashboardUiState.Content

        val budget = state.overallBudget!!
        assertEquals(50_000L, budget.budget.amountMinor)
        assertEquals(0L, budget.spentMinor)
        assertEquals(50_000L, budget.remainingMinor)
        assertEquals(0, budget.percentUsed)
        assertEquals(BudgetStatus.NORMAL, budget.status)
    }

    @Test
    fun budgetCountsOutflowOnly_withinCurrentMonth() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        budgetRepository(transactionDao).createOverallBudget(currentMonthKey(), 10_000L)
        transactionDao.insertAll(
            listOf(
                Transaction(accountId = accountId, amountMinor = 3_000L, direction = TRANSACTION_DIRECTION_OUTFLOW),
                Transaction(accountId = accountId, amountMinor = 8_000L, direction = TRANSACTION_DIRECTION_INCOME)
            )
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.overallBudget?.spentMinor == 3_000L
        } as DashboardUiState.Content

        val budget = state.overallBudget!!
        assertEquals(3_000L, budget.spentMinor)
        assertEquals(7_000L, budget.remainingMinor)
        assertEquals(30, budget.percentUsed)
        assertEquals(BudgetStatus.NORMAL, budget.status)
        assertEquals(8_000L, state.totalIncomeMinor)
    }

    @Test
    fun income_alone_neverCountsAsBudgetSpending() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        budgetRepository(transactionDao).createOverallBudget(currentMonthKey(), 10_000L)
        transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 90_000L, direction = TRANSACTION_DIRECTION_INCOME)
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.overallBudget != null && it.totalIncomeMinor == 90_000L
        } as DashboardUiState.Content

        val budget = state.overallBudget!!
        assertEquals(0L, budget.spentMinor)
        assertEquals(10_000L, budget.remainingMinor)
        assertEquals(0, budget.percentUsed)
        assertEquals(BudgetStatus.NORMAL, budget.status)
    }

    @Test
    fun overBudget_statusAndNegativeRemainingPreserved() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        budgetRepository(transactionDao).createOverallBudget(currentMonthKey(), 1_000L)
        transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 1_500L, direction = TRANSACTION_DIRECTION_OUTFLOW)
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) {
            it is DashboardUiState.Content && it.overallBudget?.status == BudgetStatus.OVER_BUDGET
        } as DashboardUiState.Content

        val budget = state.overallBudget!!
        assertEquals(1_500L, budget.spentMinor)
        assertEquals(-500L, budget.remainingMinor)
        assertEquals(150, budget.percentUsed)
        assertEquals(BudgetStatus.OVER_BUDGET, budget.status)
    }

    @Test
    fun lastMonthBudget_notReportedAsCurrentBudget() = runBlocking {
        accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        budgetRepository(transactionDao).createOverallBudget(
            BudgetCalendar.shiftMonth(currentMonthKey(), -1),
            100_000L
        )

        val viewModel = defaultViewModel()
        val state = awaitState(viewModel) { it is DashboardUiState.Content } as DashboardUiState.Content

        assertNull(state.overallBudget)
    }

    @Test
    fun budgetCreation_reactivelyUpdatesDashboardState() = runBlocking {
        accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 1_000L)
        )
        val viewModel = defaultViewModel()
        awaitState(viewModel) { it is DashboardUiState.Content && it.overallBudget == null }

        budgetRepository(transactionDao).createOverallBudget(currentMonthKey(), 25_000L)

        val updated = awaitState(viewModel) {
            it is DashboardUiState.Content && it.overallBudget != null
        } as DashboardUiState.Content
        assertEquals(25_000L, updated.overallBudget?.budget?.amountMinor)
        assertEquals(25_000L, updated.overallBudget?.remainingMinor)
    }
}
