package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WorkItem
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
class FinancialIntegrityTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var workItemDao: WorkItemDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var workRepository: WorkRepository
    private lateinit var budgetRepository: BudgetRepository

    private val monthKey = "2026-09"
    private val monthStart = BudgetCalendar.monthStart(monthKey)
    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        workItemDao = database.workItemDao()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        budgetDao = database.budgetDao()
        workRepository = WorkRepository(
            workItemDao = workItemDao,
            transactionDao = transactionDao,
            openHelper = database.openHelper,
            clock = { now }
        )
        budgetRepository = BudgetRepository(
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

    private fun seedAccount(opening: Long = 100_000L): Long = runBlocking {
        accountDao.insert(Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = opening))
    }

    @Test
    fun expectedAmount_doesNotChangeAccountBalance() = runBlocking {
        val accountId = seedAccount(100_000L)
        val before = accountDao.getAccountBalanceMinor(accountId)

        workRepository.create("Big project", "", 1_000_000L, 0L, "Client")

        val after = accountDao.getAccountBalanceMinor(accountId)
        assertEquals(before, after)
        assertEquals(100_000L, after)
    }

    @Test
    fun expectedAmount_doesNotChangeIncomeTotals() = runBlocking {
        val accountId = seedAccount()
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 20_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = monthStart + 1_000L,
                note = "Salary"
            )
        )
        val before = transactionDao.getTotalIncomeMinorForActiveAccounts()

        workRepository.create("Project", "", 500_000L, 0L, "")

        assertEquals(before, transactionDao.getTotalIncomeMinorForActiveAccounts())
    }

    @Test
    fun expectedAmount_doesNotAffectBudget() = runBlocking {
        val accountId = seedAccount()
        val categoryId = categoryDao.insert(
            com.prasbin.shadowmoney.data.model.Category(
                name = "Food",
                direction = com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 10_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = monthStart + 1_000L,
                note = "Lunch"
            )
        )
        budgetRepository.createOverallBudget(monthKey, 50_000L)
        val before = budgetRepository.loadMonthData(monthKey)

        workRepository.create("Project with expected income", "", 900_000L, 0L, "")

        val after = budgetRepository.loadMonthData(monthKey)
        assertEquals(before.overall?.spentMinor, after.overall?.spentMinor)
        assertEquals(before.overall?.remainingMinor, after.overall?.remainingMinor)
        assertEquals(before.overall?.percentUsed, after.overall?.percentUsed)
    }

    @Test
    fun linkedActualIncome_remainsNormalFinancialIncome() = runBlocking {
        val accountId = seedAccount()
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 40_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = monthStart + 1_000L,
                note = "Project payment"
            )
        )
        workRepository.linkTransaction(txId, workId)

        assertEquals(40_000L, transactionDao.getTotalIncomeMinor(accountId))
        assertEquals(40_000L, transactionDao.getTotalIncomeMinorForActiveAccounts())

        val views = workRepository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(40_000L, views.first().receivedMinor)
    }

    @Test
    fun linkedIncome_countsAsBudgetIncome_notSpending() = runBlocking {
        val accountId = seedAccount()
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 40_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = monthStart + 1_000L,
                note = "Project payment"
            )
        )
        workRepository.linkTransaction(txId, workId)
        budgetRepository.createOverallBudget(monthKey, 50_000L)

        val data = budgetRepository.loadMonthData(monthKey)
        assertEquals(0L, data.overall?.spentMinor)
    }

    @Test
    fun linkedOutflow_remainsBudgetSpending() = runBlocking {
        val accountId = seedAccount()
        val categoryId = categoryDao.insert(
            com.prasbin.shadowmoney.data.model.Category(
                name = "Software",
                direction = com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
            )
        )
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 15_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = monthStart + 1_000L,
                note = "License"
            )
        )
        workRepository.linkTransaction(txId, workId)
        budgetRepository.createOverallBudget(monthKey, 50_000L)

        val data = budgetRepository.loadMonthData(monthKey)
        assertEquals(15_000L, data.overall?.spentMinor)

        val views = workRepository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(0L, views.first().receivedMinor)
    }

    @Test
    fun deletingWorkItem_doesNotDeleteLinkedTransactions() = runBlocking {
        val accountId = seedAccount()
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 40_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = monthStart + 1_000L,
                note = "Payment"
            )
        )
        workRepository.linkTransaction(txId, workId)

        workRepository.delete(workItemDao.getById(workId)!!)

        assertEquals(40_000L, transactionDao.getTotalIncomeMinor(accountId))
        assertNotNull(transactionDao.getById(txId))
    }

    @Test
    fun archivedWorkItem_doesNotEraseFinancialHistory() = runBlocking {
        val accountId = seedAccount()
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 40_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = monthStart + 1_000L,
                note = "Payment"
            )
        )
        workRepository.linkTransaction(txId, workId)

        workRepository.archive(workId)

        assertEquals(40_000L, transactionDao.getTotalIncomeMinor(accountId))
        val views = workRepository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(40_000L, views.first().receivedMinor)
    }
}
