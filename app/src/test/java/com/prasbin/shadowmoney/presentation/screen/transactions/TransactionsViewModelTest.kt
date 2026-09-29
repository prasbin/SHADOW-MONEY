package com.prasbin.shadowmoney.presentation.screen.transactions

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.imports.ImportDate
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_PAUSED
import com.prasbin.shadowmoney.data.model.WorkItem
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TransactionsViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var transactionDao: com.prasbin.shadowmoney.data.TransactionDao
    private lateinit var accountDao: com.prasbin.shadowmoney.data.AccountDao
    private lateinit var categoryDao: com.prasbin.shadowmoney.data.CategoryDao
    private lateinit var workItemDao: com.prasbin.shadowmoney.data.WorkItemDao

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        workItemDao = database.workItemDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(): TransactionsViewModel = TransactionsViewModel(
        transactionDao = transactionDao,
        accountDao = accountDao,
        categoryDao = categoryDao,
        workItemDao = workItemDao,
        clock = { now }
    )

    private suspend fun awaitState(
        vm: TransactionsViewModel,
        predicate: suspend (TransactionsUiState) -> Boolean
    ): TransactionsUiState = withTimeout(10_000) {
        vm.uiState.first { predicate(it) }
    }

    private suspend fun awaitTransactionCount(expected: Int) {
        withTimeout(10_000) {
            transactionDao.getAll().first { it.size == expected }
        }
    }

    @Test
    fun state_exposesOnlyActiveAccountsCategoriesAndWorkItems() = runBlocking {
        val activeAccount = accountDao.insert(
            Account(name = "Active", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val archivedAccount = accountDao.insert(
            Account(
                name = "Archived",
                type = ACCOUNT_TYPE_WALLET,
                openingBalanceMinor = 0L,
                isActive = false
            )
        )
        val activeCategory = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val archivedCategory = categoryDao.insert(
            Category(
                name = "Old category",
                direction = CATEGORY_DIRECTION_OUTFLOW,
                isActive = false
            )
        )
        val activeWork = workItemDao.insert(
            WorkItem(title = "Active job", status = WORK_STATUS_ACTIVE)
        )
        val pausedWork = workItemDao.insert(
            WorkItem(title = "Paused job", status = WORK_STATUS_PAUSED)
        )

        val vm = viewModel()
        val state = awaitState(vm) { !it.isLoading }

        assertEquals(1, state.accounts.size)
        assertEquals(activeAccount, state.accounts.single().id)
        assertFalse(state.accounts.any { it.id == archivedAccount })

        assertEquals(1, state.categories.size)
        assertEquals(activeCategory, state.categories.single().id)
        assertFalse(state.categories.any { it.id == archivedCategory })

        assertEquals(1, state.workItems.size)
        assertEquals(activeWork, state.workItems.single().id)
        assertFalse(state.workItems.any { it.id == pausedWork })
    }

    @Test
    fun addTransaction_success_clearsErrorAndPopulatesRecentView() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5_000L)
        )
        val categoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val vm = viewModel()
        awaitState(vm) { !it.isLoading && it.accounts.isNotEmpty() }

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "not-a-number",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = categoryId,
                workItemId = null,
                note = ""
            )
        )
        assertNotNull(vm.errorMessage.value)

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "42.75",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = categoryId,
                workItemId = null,
                note = "  Noodles  "
            )
        )
        assertNull("a successful save must clear the previous error", vm.errorMessage.value)
        awaitTransactionCount(1)

        val listState = awaitState(vm) { state ->
            state.recent.any { it.transaction.note == "Noodles" }
        }
        val view = listState.recent.first { it.transaction.note == "Noodles" }
        assertEquals("Wallet", view.accountName)
        assertEquals("Food", view.categoryName)
        assertEquals(4_275L, view.transaction.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, view.transaction.direction)
        assertEquals("", view.transaction.source)
        assertEquals(accountId, view.transaction.accountId)
        assertEquals(categoryId, view.transaction.categoryId)
        assertNull(view.transaction.workItemId)
        assertNull(view.transaction.externalRef)
        assertEquals(now, view.transaction.createdTimestamp)
    }

    @Test
    fun addTransaction_storesDateFromKathmanduIsoText() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val vm = viewModel()
        awaitState(vm) { !it.isLoading && it.accounts.isNotEmpty() }

        assertTrue(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_INCOME,
                amountText = "900",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = null,
                workItemId = null,
                note = "Rent received"
            )
        )
        awaitTransactionCount(1)

        val expected = when (val parsed = ImportDate.parse("2026-09-15")) {
            is com.prasbin.shadowmoney.data.imports.DateParseResult.Ok -> parsed.epochMillis
            else -> throw AssertionError("date must parse in test")
        }
        assertEquals(expected, transactionDao.getAll().first().single().transactionTimestamp)
    }

    @Test
    fun addTransaction_rejectsArchivedCategory() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val archivedCategory = categoryDao.insert(
            Category(
                name = "Retired",
                direction = CATEGORY_DIRECTION_OUTFLOW,
                isActive = false
            )
        )
        val vm = viewModel()
        awaitState(vm) { !it.isLoading && it.accounts.isNotEmpty() }

        assertFalse(
            vm.addTransaction(
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                amountText = "10.00",
                dateText = "2026-09-15",
                accountId = accountId,
                categoryId = archivedCategory,
                workItemId = null,
                note = ""
            )
        )
        assertEquals("Selected category is not available", vm.errorMessage.value)
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun addTransaction_rejectsArchivedAccount() = runBlocking {
        val accountId = accountDao.insert(
            Account(
                name = "Old wallet",
                type = ACCOUNT_TYPE_WALLET,
                openingBalanceMinor = 0L,
                isActive = false
            )
        )
        val vm = viewModel()
        awaitState(vm) { !it.isLoading }

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
        assertEquals(0, transactionDao.getCount())
    }

    @Test
    fun defaultDateText_isCurrentKathmanduIsoDate() {
        val vm = viewModel()
        val expected = LocalDate.now(ZoneId.of("Asia/Kathmandu")).toString()
        assertEquals(expected, vm.defaultDateText)
        assertTrue(
            "default date must be yyyy-MM-dd",
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(vm.defaultDateText)
        )
    }

    @Test
    fun isIncome_mapsDirectionConstants() {
        val vm = viewModel()
        assertTrue(vm.isIncome(TRANSACTION_DIRECTION_INCOME))
        assertFalse(vm.isIncome(TRANSACTION_DIRECTION_OUTFLOW))
    }

    @Test
    fun clearError_afterFailure_resetsMessage() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val vm = viewModel()
        awaitState(vm) { !it.isLoading && it.accounts.isNotEmpty() }

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
        assertNotNull(vm.errorMessage.value)
        vm.clearError()
        assertNull(vm.errorMessage.value)
    }
}
