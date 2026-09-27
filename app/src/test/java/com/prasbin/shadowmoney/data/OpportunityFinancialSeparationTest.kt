package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
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
class OpportunityFinancialSeparationTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var opportunityDao: OpportunityDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var opportunityRepository: OpportunityRepository
    private lateinit var budgetRepository: BudgetRepository

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        opportunityDao = database.opportunityDao()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        budgetDao = database.budgetDao()
        opportunityRepository = OpportunityRepository(opportunityDao)
        budgetRepository = BudgetRepository(budgetDao, transactionDao, categoryDao, database.openHelper)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun creatingOpportunity_doesNotCreateTransaction() = runBlocking {
        val before = transactionDao.getCount()
        opportunityRepository.create("Website", "Build", 0, "Upwork", "", 50_000L, 0L, "Acme")
        assertEquals(before, transactionDao.getCount())
        assertTrue(transactionDao.getAll().first().isEmpty())
    }

    @Test
    fun expectedAmount_doesNotChangeAccountBalance() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10_000L)
        )
        val before = accountDao.getAccountBalanceMinor(accountId)

        opportunityRepository.create("Big project", "d", 0, "", "", 1_000_000L, 0L, "")

        val after = accountDao.getAccountBalanceMinor(accountId)
        assertEquals(before, after)
        assertEquals(10_000L, after)
    }

    @Test
    fun expectedAmount_doesNotCountAsIncome() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 5_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                note = "Salary"
            )
        )
        val before = transactionDao.getTotalIncomeMinorForActiveAccounts()

        opportunityRepository.create("Project", "d", 0, "", "", 500_000L, 0L, "")

        assertEquals(before, transactionDao.getTotalIncomeMinorForActiveAccounts())
    }

    @Test
    fun expectedAmount_doesNotAffectBudgets() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
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
                amountMinor = 3_000L,
                direction = com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW,
                note = "Lunch"
            )
        )
        budgetRepository.createOverallBudget("2026-09", 50_000L)
        val before = budgetRepository.loadMonthData("2026-09")

        opportunityRepository.create("Project", "d", 0, "", "", 900_000L, 0L, "")

        val after = budgetRepository.loadMonthData("2026-09")
        assertEquals(before.overall?.spentMinor, after.overall?.spentMinor)
        assertEquals(before.overall?.remainingMinor, after.overall?.remainingMinor)
    }

    @Test
    fun wonStatus_doesNotBecomeIncomeAutomatically() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val id = opportunityRepository.create("Project", "d", 0, "", "", 100_000L, 0L, "")
        opportunityDao.updateStatus(id, OPPORTUNITY_STATUS_WON, 1_800_000_000_000L)

        assertEquals(0L, transactionDao.getTotalIncomeMinorForActiveAccounts())
        assertTrue(transactionDao.getAll().first().isEmpty())
    }

    @Test
    fun statusChanges_doNotCreateFinancialRecords() = runBlocking {
        val id = opportunityRepository.create("Project", "d", 0, "", "", 10_000L, 0L, "")
        opportunityDao.updateStatus(id, com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_APPLIED, 1_800_000_000_000L)
        opportunityDao.updateStatus(id, com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS, 1_800_000_000_000L)
        opportunityDao.updateStatus(id, OPPORTUNITY_STATUS_WON, 1_800_000_000_000L)

        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun opportunityAmount_isNeverStoredAsFinancialTruth() = runBlocking {
        opportunityRepository.create("Project", "d", 0, "", "", 777_777L, 0L, "")

        assertTrue(transactionDao.getAll().first().isEmpty())
        assertTrue(budgetDao.getByMonthOnce("2026-09").isEmpty())
    }
}
