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
class AccountDatabaseTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: AccountDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        accountDao = database.accountDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetAccount() = runBlocking {
        val account = Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        val id = accountDao.insert(account)
        assertTrue(id > 0)

        val fetched = accountDao.getById(id)
        assertNotNull(fetched)
        assertEquals("Wallet", fetched?.name)
        assertEquals(ACCOUNT_TYPE_WALLET, fetched?.type)
        assertEquals(5000L, fetched?.openingBalanceMinor)
    }

    @Test
    fun emptyAccountBalance_returnsZero() = runBlocking {
        val balance = accountDao.getTotalBalanceMinor()
        assertEquals(0L, balance)
    }

    @Test
    fun accountBalanceCalculation_works() = runBlocking {
        val account = Account(name = "Bank", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 10000L)
        val id = accountDao.insert(account)

        val balance = accountDao.getAccountBalanceMinor(id)
        assertEquals(10000L, balance)
    }

    @Test
    fun archiveAccount_setsInactive() = runBlocking {
        val account = Account(name = "Cash", type = ACCOUNT_TYPE_CASH, openingBalanceMinor = 1000L)
        val id = accountDao.insert(account)

        accountDao.archive(id)
        val fetched = accountDao.getById(id)
        assertNotNull(fetched)
        assertFalse(fetched!!.isActive)
    }

    @Test
    fun getAllAccounts_returnsEmptyInitially() = runBlocking {
        val accounts = accountDao.getAll().first()
        assertTrue(accounts.isEmpty())
    }
}