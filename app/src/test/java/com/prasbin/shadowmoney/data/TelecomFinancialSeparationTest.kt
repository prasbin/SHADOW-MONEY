package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
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
class TelecomFinancialSeparationTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var telecomDao: TelecomDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var workItemDao: WorkItemDao
    private lateinit var telecomRepository: TelecomRepository
    private lateinit var budgetRepository: BudgetRepository

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        telecomDao = database.telecomDao()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        budgetDao = database.budgetDao()
        workItemDao = database.workItemDao()
        telecomRepository = TelecomRepository(telecomDao)
        budgetRepository = BudgetRepository(budgetDao, transactionDao, categoryDao, database.openHelper)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun creatingPackage_doesNotCreateTransaction() = runBlocking {
        val before = transactionDao.getCount()
        telecomRepository.createPackage("Data 1GB", "Nepal Telecom", "data", 1_500L, 1, "")
        assertEquals(before, transactionDao.getCount())
        assertTrue(transactionDao.getAll().first().isEmpty())
    }

    @Test
    fun creatingSubscription_doesNotCreateTransaction() = runBlocking {
        val simId = telecomRepository.createSim("Sim", "C", "", "")
        val packageId = telecomRepository.createPackage("P", "C", "data", 1_000L, 1, "")
        val before = transactionDao.getCount()
        telecomRepository.createSubscription(simId, packageId, 1_700_000_000_000L, 1_800_000_000_000L, 0L)
        assertEquals(before, transactionDao.getCount())
    }

    @Test
    fun expectedMonthlyCost_doesNotChangeAccountBalance() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10_000L)
        )
        val before = accountDao.getAccountBalanceMinor(accountId)

        val simId = telecomRepository.createSim("Sim", "C", "", "")
        val packageId = telecomRepository.createPackage("P", "C", "data", 9_000L, 2, "")
        telecomRepository.createSubscription(simId, packageId, 1_700_000_000_000L, 1_800_000_000_000L, 0L)

        val after = accountDao.getAccountBalanceMinor(accountId)
        assertEquals(before, after)
        assertEquals(10_000L, after)
    }

    @Test
    fun telecomData_doesNotAffectBudgetSpending() = runBlocking {
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

        val simId = telecomRepository.createSim("Sim", "C", "", "")
        val packageId = telecomRepository.createPackage("P", "C", "data", 9_000L, 1, "")
        telecomRepository.createSubscription(simId, packageId, 1_700_000_000_000L, 1_800_000_000_000L, 0L)

        val after = budgetRepository.loadMonthData("2026-09")
        assertEquals(before.overall?.spentMinor, after.overall?.spentMinor)
        assertEquals(before.overall?.remainingMinor, after.overall?.remainingMinor)
    }

    @Test
    fun telecomRecords_doNotBecomeIncomeOrOutflow() = runBlocking {
        val simId = telecomRepository.createSim("Sim", "C", "", "")
        val packageId = telecomRepository.createPackage("P", "C", "data", 1_000L, 1, "")
        telecomRepository.createSubscription(simId, packageId, 1_700_000_000_000L, 1_800_000_000_000L, 0L)

        assertTrue(transactionDao.getAll().first().isEmpty())
    }

    @Test
    fun telecomData_notInWorkOrFinancialRepositories() = runBlocking {
        telecomRepository.createSim("Sim", "C", "", "")
        telecomRepository.createPackage("P", "C", "data", 1_000L, 1, "")

        assertTrue(workItemDao.observeAll().first().isEmpty())
        assertTrue(budgetDao.getByMonthOnce("2026-09").isEmpty())
    }

    @Test
    fun telecomTables_areSeparateFromFinancialTables() = runBlocking {
        telecomRepository.createSim("Sim", "C", "", "")
        telecomRepository.createPackage("P", "C", "data", 1_000L, 1, "")
        assertEquals(0, transactionDao.getCount())
        assertEquals(0, budgetDao.getByMonthOnce("2026-09").size)
    }
}
