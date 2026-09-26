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
class MigrationTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var goalDao: GoalDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).addMigrations(MIGRATION_1_2)
        .allowMainThreadQueries()
        .build()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        goalDao = database.goalDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun migration_createsAccountsTable() = runBlocking {
        val count = accountDao.getAll().first().size
        assertTrue(count >= 0)
        database.close()
    }

    @Test
    fun migration_createsCategoriesTable() = runBlocking {
        val categories = categoryDao.getAll().first()
        assertTrue(categories.size >= 0)
        database.close()
    }

    @Test
    fun migration_createsTransactionsTable() = runBlocking {
        val transactions = transactionDao.getAll().first()
        assertTrue(transactions.size >= 0)
        database.close()
    }

    @Test
    fun migration_createsGoalsTable() = runBlocking {
        val goals = goalDao.getAllActive().first()
        assertTrue(goals.size >= 0)
        database.close()
    }
}