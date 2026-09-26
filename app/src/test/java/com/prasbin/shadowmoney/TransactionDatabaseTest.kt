package com.prasbin.shadowmoney

import androidx.room.Room
import com.prasbin.shadowmoney.data.*
import com.prasbin.shadowmoney.data.model.*
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
class TransactionDatabaseTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var accountDao: AccountDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = database.transactionDao()
        accountDao = database.accountDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertIncomeTransaction_persists() = runBlocking {
        val account = Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        val accountId = accountDao.insert(account)

        val transaction = Transaction(
            accountId = accountId,
            amountMinor = 3000L,
            direction = TRANSACTION_DIRECTION_INCOME,
            categoryId = null
        )
        val id = transactionDao.insert(transaction)
        assertTrue(id > 0)

        val fetched = transactionDao.getById(id)
        assertNotNull(fetched)
        assertEquals(TRANSACTION_DIRECTION_INCOME, fetched?.direction)
        assertEquals(3000L, fetched?.amountMinor)
    }

    @Test
    fun insertOutflowTransaction_persists() = runBlocking {
        val account = Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        val accountId = accountDao.insert(account)

        val transaction = Transaction(
            accountId = accountId,
            amountMinor = 1500L,
            direction = TRANSACTION_DIRECTION_OUTFLOW
        )
        val id = transactionDao.insert(transaction)
        assertTrue(id > 0)

        val fetched = transactionDao.getById(id)
        assertNotNull(fetched)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, fetched?.direction)
        assertEquals(1500L, fetched?.amountMinor)
    }

    @Test
    fun multipleTransactions_calculatedCorrectly() = runBlocking {
        val account = Account(name = "Bank", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 10000L)
        val accountId = accountDao.insert(account)

        val income = Transaction(accountId = accountId, amountMinor = 5000L, direction = TRANSACTION_DIRECTION_INCOME)
        val outflow = Transaction(accountId = accountId, amountMinor = 2000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
        transactionDao.insertAll(listOf(income, outflow))

        val incomeTotal = transactionDao.getTotalIncomeMinor(accountId)
        val outflowTotal = transactionDao.getTotalOutflowMinor(accountId)
        assertEquals(5000L, incomeTotal)
        assertEquals(2000L, outflowTotal)
    }

    @Test
    fun getBalanceMinor_calculatesCorrectly() = runBlocking {
        val account = Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10000L)
        val accountId = accountDao.insert(account)

        val income = Transaction(accountId = accountId, amountMinor = 3000L, direction = TRANSACTION_DIRECTION_INCOME)
        val outflow = Transaction(accountId = accountId, amountMinor = 2000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
        transactionDao.insertAll(listOf(income, outflow))

        val balance = TransactionRepository(transactionDao, accountDao).getBalanceMinor(accountId)
        assertEquals(11000L, balance)
    }

    @Test
    fun emptyAccount_getBalanceReturnsOpening() = runBlocking {
        val account = Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 7500L)
        val accountId = accountDao.insert(account)

        val balance = TransactionRepository(transactionDao, accountDao).getBalanceMinor(accountId)
        assertEquals(7500L, balance)
    }
}