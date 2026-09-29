package com.prasbin.shadowmoney.presentation.screen.money

import androidx.room.Room
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_BANK
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MoneyViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: com.prasbin.shadowmoney.data.AccountDao
    private lateinit var transactionDao: com.prasbin.shadowmoney.data.TransactionDao

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(): MoneyViewModel = MoneyViewModel(
        accountDao = accountDao,
        transactionDao = transactionDao,
        clock = { now }
    )

    private suspend fun awaitAccounts(
        vm: MoneyViewModel,
        predicate: (List<MoneyAccountView>) -> Boolean
    ): List<MoneyAccountView> = withTimeout(10_000) {
        vm.uiState.first { !it.isLoading && predicate(it.accounts) }.accounts
    }

    private suspend fun awaitAccountCount(expected: Int): List<com.prasbin.shadowmoney.data.model.Account> =
        withTimeout(10_000) {
            accountDao.getAll().first { it.size == expected }
        }

    @Test
    fun createAccount_storesExactOpeningBalanceAndTrimmedName() = runBlocking {
        val vm = viewModel()

        assertTrue(vm.createAccount("  Wallet  ", ACCOUNT_TYPE_WALLET, "100000.50"))
        var rows = awaitAccountCount(1)
        assertEquals("Wallet", rows.single().name)
        assertEquals(ACCOUNT_TYPE_WALLET, rows.single().type)
        assertEquals(10_000_050L, rows.single().openingBalanceMinor)
        assertTrue(rows.single().isActive)
        assertEquals(now, rows.single().createdTimestamp)

        assertTrue(vm.createAccount("Cash", ACCOUNT_TYPE_BANK, ""))
        rows = awaitAccountCount(2)
        val cash = rows.first { it.name == "Cash" }
        assertEquals(0L, cash.openingBalanceMinor)
        assertEquals(ACCOUNT_TYPE_BANK, cash.type)
    }

    @Test
    fun createAccount_rejectsBlankName() = runBlocking {
        val vm = viewModel()

        assertFalse(vm.createAccount("", ACCOUNT_TYPE_WALLET, "10.00"))
        assertEquals("Account name is required", vm.errorMessage.value)
        assertFalse(vm.createAccount("   ", ACCOUNT_TYPE_WALLET, "10.00"))
        assertEquals("Account name is required", vm.errorMessage.value)
        assertEquals(0, accountDao.getAll().first().size)
    }

    @Test
    fun createAccount_rejectsInvalidOpeningAmounts() = runBlocking {
        val vm = viewModel()

        assertFalse(vm.createAccount("Wallet", ACCOUNT_TYPE_WALLET, "12.345"))
        assertNotNull(vm.errorMessage.value)
        assertFalse(vm.createAccount("Wallet", ACCOUNT_TYPE_WALLET, "abc"))
        assertNotNull(vm.errorMessage.value)
        assertEquals(0, accountDao.getAll().first().size)
    }

    @Test
    fun updateAccount_updatesFieldsKeepsIdentityAndActiveFlag() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.createAccount("Wallet", ACCOUNT_TYPE_WALLET, "50.00"))
        val created = awaitAccountCount(1).single()

        assertTrue(vm.updateAccount(created.id, "Bank main", ACCOUNT_TYPE_BANK, "250.00"))
        val updated = awaitAccounts(vm) { accounts ->
            accounts.singleOrNull()?.account?.name == "Bank main"
        }.single()

        assertEquals(created.id, updated.account.id)
        assertEquals("Bank main", updated.account.name)
        assertEquals(ACCOUNT_TYPE_BANK, updated.account.type)
        assertEquals(25_000L, updated.account.openingBalanceMinor)
        assertTrue(updated.account.isActive)
        assertEquals(created.createdTimestamp, updated.account.createdTimestamp)
    }

    @Test
    fun setArchived_togglesAccountActiveFlag() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.createAccount("Wallet", ACCOUNT_TYPE_WALLET, "0"))
        val created = awaitAccountCount(1).single()

        vm.setArchived(created.id, true)
        val archived = awaitAccounts(vm) { accounts ->
            accounts.singleOrNull()?.account?.isActive == false
        }.single()
        assertEquals(created.id, archived.account.id)
        assertFalse(archived.account.isActive)

        vm.setArchived(created.id, false)
        val restored = awaitAccounts(vm) { accounts ->
            accounts.singleOrNull()?.account?.isActive == true
        }.single()
        assertTrue(restored.account.isActive)
        assertEquals(created.id, restored.account.id)
    }

    @Test
    fun uiState_balanceReflectsExactTransactionTotals() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.createAccount("Wallet", ACCOUNT_TYPE_WALLET, "1000.00"))
        val accountId = awaitAccounts(vm) { it.size == 1 }.single().account.id
        assertEquals(100_000L, awaitAccounts(vm) { it.size == 1 }.single().balanceMinor)

        val incomeId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 50_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = now - 1_000L,
                note = "Salary"
            )
        )
        assertTrue(incomeId > 0)
        awaitAccounts(vm) { accounts -> accounts.singleOrNull()?.balanceMinor == 150_000L }

        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 20_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = now,
                note = "Rent"
            )
        )
        val settled = awaitAccounts(vm) { accounts ->
            accounts.singleOrNull()?.balanceMinor == 130_000L
        }.single()
        assertEquals(130_000L, settled.balanceMinor)
        assertEquals(Money.balanceMinor(100_000L, 50_000L, 20_000L), settled.balanceMinor)
        assertEquals(100_000L, settled.account.openingBalanceMinor)
    }

    @Test
    fun uiState_archivedAccountsStillListedWithTheirBalances() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.createAccount("Old wallet", ACCOUNT_TYPE_WALLET, "75.00"))
        val accountId = awaitAccounts(vm) { it.size == 1 }.single().account.id

        vm.setArchived(accountId, true)
        val accounts = awaitAccounts(vm) { list ->
            list.size == 1 && list.single().account.isActive == false
        }
        assertFalse(accounts.single().account.isActive)
        assertEquals(7_500L, accounts.single().balanceMinor)
    }

    @Test
    fun createAccount_trimsTrailingWhitespaceNames() = runBlocking {
        val vm = viewModel()
        assertTrue(vm.createAccount("   Pocket money   ", ACCOUNT_TYPE_WALLET, "0"))
        val rows = awaitAccountCount(1)
        assertEquals("Pocket money", rows.single().name)
    }
}
