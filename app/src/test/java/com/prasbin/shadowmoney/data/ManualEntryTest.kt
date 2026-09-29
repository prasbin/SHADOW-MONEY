package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.backup.BackupCreateOutcome
import com.prasbin.shadowmoney.data.backup.BackupRepository
import com.prasbin.shadowmoney.data.backup.FakeBackupFileIo
import com.prasbin.shadowmoney.data.backup.RestoreWriteOutcome
import com.prasbin.shadowmoney.data.imports.ImportDate
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_BOTH
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WorkItem
import com.prasbin.shadowmoney.presentation.screen.transactions.TransactionsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ManualEntryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var workItemDao: WorkItemDao
    private lateinit var budgetDao: BudgetDao
    private lateinit var goalDao: GoalDao
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var dashboardRepository: DashboardRepository

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
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        workItemDao = database.workItemDao()
        budgetDao = database.budgetDao()
        goalDao = database.goalDao()
        budgetRepository = BudgetRepository(
            budgetDao = budgetDao,
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            openHelper = database.openHelper
        )
        dashboardRepository = DashboardRepository(
            accountDao = accountDao,
            categoryDao = categoryDao,
            transactionDao = transactionDao,
            goalDao = goalDao,
            openHelper = database.openHelper
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun entryViewModel(): TransactionsViewModel = TransactionsViewModel(
        transactionDao = transactionDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        workItemDao = workItemDao,
        clock = { now }
    )

    private suspend fun awaitLoaded(vm: TransactionsViewModel) {
        withTimeout(10_000) {
            vm.uiState.first { !it.isLoading && it.accounts.isNotEmpty() }
        }
    }

    private suspend fun awaitTransactionCount(expected: Int) {
        withTimeout(10_000) {
            transactionDao.getAll().first { it.size == expected }
        }
    }

    private suspend fun seedAccount(opening: Long = 100_000L): Long =
        accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = opening)
        )

    @Test
    fun manualEntries_changeBalanceByExactMinorUnits() = runBlocking {
        val accountId = seedAccount(100_000L)
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "500.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Payment"
            )
        )
        awaitTransactionCount(1)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "125.50",
                dateText = "2026-09-16",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Groceries"
            )
        )
        awaitTransactionCount(2)

        val income = transactionDao.getTotalIncomeMinor(accountId)
        val outflow = transactionDao.getTotalOutflowMinor(accountId)
        assertEquals(50_000L, income)
        assertEquals(12_550L, outflow)
        assertEquals(
            Money.balanceMinor(100_000L, 50_000L, 12_550L),
            100_000L + 50_000L - 12_550L
        )
        assertEquals(
            137_450L,
            Money.balanceMinor(100_000L, income, outflow)
        )

        val stored = transactionDao.getAll().first()
        assertEquals(50_000L, stored.first { it.direction == TRANSACTION_DIRECTION_INCOME }.amountMinor)
        assertEquals(12_550L, stored.first { it.direction == TRANSACTION_DIRECTION_OUTFLOW }.amountMinor)
    }

    @Test
    fun manualEntry_parsesExactGroupedAmount() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "1,234.56",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        awaitTransactionCount(1)

        assertEquals(123_456L, transactionDao.getAll().first().single().amountMinor)
    }

    @Test
    fun manualEntry_storesDateAccountCategoryWorkItemAndNormalSource() = runBlocking {
        val accountId = seedAccount()
        val categoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val workId = workItemDao.insert(
            WorkItem(
                title = "Logo design",
                expectedAmountMinor = 80_000L,
                status = WORK_STATUS_ACTIVE
            )
        )
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "75.25",
                dateText = "2026-09-20",
                accountId = accountId,
                categoryId = categoryId,
                workItemId = workId,
                note = "  Design lunch  "
            )
        )
        awaitTransactionCount(1)

        val stored = transactionDao.getAll().first().single()
        val expectedDate = when (val parsed = ImportDate.parse("2026-09-20")) {
            is com.prasbin.shadowmoney.data.imports.DateParseResult.Ok -> parsed.epochMillis
            else -> throw AssertionError("date must parse in test")
        }
        assertEquals(expectedDate, stored.transactionTimestamp)
        assertEquals(accountId, stored.accountId)
        assertEquals(categoryId, stored.categoryId)
        assertEquals(workId, stored.workItemId)
        assertEquals("Design lunch", stored.note)
        assertEquals(7_525L, stored.amountMinor)
        assertEquals("", stored.source)
        assertNull(stored.externalRef)
        assertEquals(now, stored.createdTimestamp)
    }

    @Test
    fun manualEntry_isNormalTransaction_notImportFile() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)
        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "10",
                dateText = "2026-09-10",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Cash"
            )
        )
        awaitTransactionCount(1)

        val stored = transactionDao.getAll().first().single()
        assertEquals("", stored.source)
        assertFalse(stored.source == "IMPORT_FILE")
        assertNull(stored.externalRef)
    }

    @Test
    fun manualEntry_rejectsInvalidAndOverpreciseAmounts() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "abc",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Invalid amount: 'abc'", vm.errorMessage.value)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "12.345",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertNotNull(vm.errorMessage.value)

        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualEntry_rejectsZeroAndNegativeAmounts() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "0",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Amount must be greater than zero", vm.errorMessage.value)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "-5.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Amount must be greater than zero", vm.errorMessage.value)

        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualEntry_requiresExistingActiveAccount() = runBlocking {
        val accountId = seedAccount()
        accountDao.archive(accountId)
        val vm = entryViewModel()
        withTimeout(10_000) { vm.uiState.first { !it.isLoading } }

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Select an existing active account", vm.errorMessage.value)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = 999_999L,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualEntry_rejectsDirectionConflictingCategory_acceptsBothAndNone() = runBlocking {
        val accountId = seedAccount()
        val incomeOnly = categoryDao.insert(
            Category(name = "Salary", direction = CATEGORY_DIRECTION_INCOME)
        )
        val both = categoryDao.insert(
            Category(name = "Misc", direction = CATEGORY_DIRECTION_BOTH)
        )
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = incomeOnly,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Category does not match the selected direction", vm.errorMessage.value)
        assertEquals(0, transactionDao.getCount())

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = both,
                workItemId = null,
                note = ""
            )
        )
        awaitTransactionCount(1)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "20.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = ""
            )
        )
        awaitTransactionCount(2)
    }

    @Test
    fun manualEntry_rejectsInvalidDates() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)

        listOf("01/02/2026", "2026-02-30", "not-a-date", "").forEach { bad ->
            assertFalse(
                "date '$bad' must be rejected",
                vm.addTransaction(
                    direction = TRANSACTION_DIRECTION_OUTFLOW,
                    amountText = "10.00",
                    dateText = bad,
                    accountId = accountId,
                    categoryId = null,
                    workItemId = null,
                    note = ""
                )
            )
        }
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualEntry_rejectsUnknownWorkItem() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = 999_999L,
                note = ""
            )
        )
        assertEquals("Selected work item is not available", vm.errorMessage.value)
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualEntry_databaseForeignKeys_areEnforced() = runBlocking {
        var accountBlocked = false
        try {
            transactionDao.insert(
                Transaction(
                    accountId = 999_999L,
                    amountMinor = 1_000L,
                    direction = TRANSACTION_DIRECTION_OUTFLOW,
                    transactionTimestamp = monthStart + 1_000L
                )
            )
        } catch (e: Exception) {
            accountBlocked = true
        }
        assertTrue("accountId foreign key must be enforced", accountBlocked)

        val accountId = seedAccount()
        var categoryBlocked = false
        try {
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    categoryId = 999_999L,
                    amountMinor = 1_000L,
                    direction = TRANSACTION_DIRECTION_OUTFLOW,
                    transactionTimestamp = monthStart + 1_000L
                )
            )
        } catch (e: Exception) {
            categoryBlocked = true
        }
        assertTrue("categoryId foreign key must be enforced", categoryBlocked)
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun manualOutflow_countsInBudgetSpending() = runBlocking {
        val accountId = seedAccount()
        val categoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val vm = entryViewModel()
        awaitLoaded(vm)
        budgetRepository.createOverallBudget(monthKey, 500_000L)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "300.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = categoryId,
                workItemId = null,
                note = "Dinner"
            )
        )
        awaitTransactionCount(1)

        val month = budgetRepository.loadMonthData(monthKey)
        assertEquals(30_000L, month.overall?.spentMinor)
        assertEquals(470_000L, month.overall?.remainingMinor)
    }

    @Test
    fun manualEntries_reflectedInDashboardTotals() = runBlocking {
        val accountId = seedAccount(100_000L)
        val vm = entryViewModel()
        awaitLoaded(vm)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "500.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Salary"
            )
        )
        awaitTransactionCount(1)
        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "200.00",
                dateText = "2026-09-16",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Rent"
            )
        )
        awaitTransactionCount(2)

        val snapshot = dashboardRepository.observeSnapshot().first()
        val data = dashboardRepository.loadDashboardData(snapshot)
        assertEquals(130_000L, data.totalBalanceMinor)
        assertEquals(50_000L, data.totalIncomeMinor)
        assertEquals(20_000L, data.totalOutflowMinor)
        assertFalse(data.isEmpty)
        assertTrue(data.recentTransactions.any { it.transaction.note == "Salary" })
        assertTrue(data.recentTransactions.any { it.transaction.note == "Rent" })
    }

    @Test
    fun manualEntries_feedIntelligencePeriodQueries() = runBlocking {
        val accountId = seedAccount()
        val vm = entryViewModel()
        awaitLoaded(vm)
        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "400.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Project payment"
            )
        )
        awaitTransactionCount(1)

        val end = BudgetCalendar.monthStart("2026-10")
        assertEquals(
            40_000L,
            transactionDao.getIncomeTotalForPeriod(monthStart, end)
        )
        assertEquals(
            0L,
            transactionDao.getOutflowTotalForPeriod(monthStart, end)
        )
    }

    @Test
    fun manualEntry_roundTripsThroughBackupRestore() = runBlocking {
        val accountId = seedAccount()
        val categoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val workId = workItemDao.insert(
            WorkItem(
                title = "Poster",
                expectedAmountMinor = 10_000L,
                status = WORK_STATUS_ACTIVE
            )
        )
        val vm = entryViewModel()
        awaitLoaded(vm)
        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "88.88",
                dateText = "2026-09-21",
                accountId = accountId,
                categoryId = categoryId,
                workItemId = workId,
                note = "Manual backup roundtrip"
            )
        )
        awaitTransactionCount(1)
        val original = transactionDao.getAll().first().single()

        val exportRepository = BackupRepository(database, FakeBackupFileIo(), clock = { now })
        val outcome = exportRepository.createBackupJson()
        assertTrue("export must succeed but was $outcome", outcome is BackupCreateOutcome.Success)
        val json = (outcome as BackupCreateOutcome.Success).json

        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val secondDb = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val restoreRepository = BackupRepository(secondDb, FakeBackupFileIo(), clock = { now })
            val restored = restoreRepository.restoreFromText(json)
            assertTrue("restore must succeed but was $restored", restored is RestoreWriteOutcome.Success)

            val rows = secondDb.transactionDao().getAll().first()
            assertEquals(1, rows.size)
            val roundtripped = rows.single()
            assertEquals(original.id, roundtripped.id)
            assertEquals(original.accountId, roundtripped.accountId)
            assertEquals(original.categoryId, roundtripped.categoryId)
            assertEquals(original.workItemId, roundtripped.workItemId)
            assertEquals(8_888L, roundtripped.amountMinor)
            assertEquals(original.amountMinor, roundtripped.amountMinor)
            assertEquals(original.direction, roundtripped.direction)
            assertEquals(original.transactionTimestamp, roundtripped.transactionTimestamp)
            assertEquals(original.note, roundtripped.note)
            assertEquals("", roundtripped.source)
            assertEquals(original.source, roundtripped.source)
            assertNull(roundtripped.externalRef)
            assertEquals(original.externalRef, roundtripped.externalRef)
        } finally {
            secondDb.close()
        }
    }
}
