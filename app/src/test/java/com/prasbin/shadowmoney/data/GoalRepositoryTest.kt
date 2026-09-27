package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_BANK
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
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
class GoalRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var goalDao: GoalDao
    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var repository: GoalRepository

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        goalDao = database.goalDao()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
        repository = GoalRepository(goalDao, accountDao, transactionDao)
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun seedAccount(name: String, opening: Long = 0L): Long = runBlocking {
        accountDao.insert(Account(name = name, type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = opening))
    }

    private fun tx(accountId: Long, amount: Long, direction: Int) = Transaction(
        accountId = accountId,
        amountMinor = amount,
        direction = direction,
        note = "Test"
    )

    private fun goal(name: String, target: Long, accountId: Long? = null) = com.prasbin.shadowmoney.data.model.Goal(
        name = name,
        targetAmountMinor = target,
        accountId = accountId
    )

    @Test
    fun create_persistsGoal() = runBlocking {
        val accountId = seedAccount("Savings", opening = 50_000L)
        val id = repository.create(goal("Emergency fund", 100_000L, accountId))
        assertTrue(id > 0)
        val stored = goalDao.getById(id)
        assertNotNull(stored)
        assertEquals("Emergency fund", stored!!.name)
        assertEquals(100_000L, stored.targetAmountMinor)
        assertEquals(accountId, stored.accountId)
    }

    @Test
    fun create_blankName_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(goal("  ", 1_000L)) }
        }
        Unit
    }

    @Test
    fun create_zeroOrNegativeTarget_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(goal("G", 0L)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(goal("G", -5L)) }
        }
        Unit
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = repository.create(goal("Old", 1_000L))
        val existing = goalDao.getById(id)!!
        repository.update(existing, "New", 2_000L, null, 0L)
        val updated = goalDao.getById(id)!!
        assertEquals("New", updated.name)
        assertEquals(2_000L, updated.targetAmountMinor)
        assertNull(updated.accountId)
    }

    @Test
    fun archive_andDelete_work() = runBlocking {
        val id = repository.create(goal("G", 1_000L))
        repository.archive(id)
        assertFalse(goalDao.getById(id)!!.isActive)
        repository.delete(goalDao.getById(id)!!)
        assertNull(goalDao.getById(id))
    }

    @Test
    fun progress_derivedFromLinkedAccountBalance() = runBlocking {
        val accountId = seedAccount("Bank", opening = 100_000L)
        transactionDao.insertAll(
            listOf(
                tx(accountId, 30_000L, TRANSACTION_DIRECTION_INCOME),
                tx(accountId, 10_000L, TRANSACTION_DIRECTION_OUTFLOW)
            )
        )
        val goalId = repository.create(goal("Target", 200_000L, accountId))

        val views = repository.loadGoalViews(listOf(goalDao.getById(goalId)!!))
        assertEquals(120_000L, views.first().currentBalanceMinor)
        assertEquals(80_000L, views.first().remainingMinor)
        assertEquals(60, views.first().percentUsed)
        assertEquals("Bank", views.first().accountName)
    }

    @Test
    fun progress_unlinkedGoal_zeroWithNoAccount() = runBlocking {
        val goalId = repository.create(goal("No account", 50_000L, null))
        val views = repository.loadGoalViews(listOf(goalDao.getById(goalId)!!))
        assertEquals(0L, views.first().currentBalanceMinor)
        assertEquals(50_000L, views.first().remainingMinor)
        assertEquals(0, views.first().percentUsed)
        assertNull(views.first().accountName)
    }

    @Test
    fun progress_exceedsTarget_truthfulValues() = runBlocking {
        val accountId = seedAccount("Rich", opening = 500_000L)
        val goalId = repository.create(goal("Small", 100_000L, accountId))
        val views = repository.loadGoalViews(listOf(goalDao.getById(goalId)!!))
        assertEquals(500_000L, views.first().currentBalanceMinor)
        assertEquals(-400_000L, views.first().remainingMinor)
        assertEquals(500, views.first().percentUsed)
    }

    @Test
    fun progress_archivedAccount_stillDerivesFromRecords() = runBlocking {
        val accountId = seedAccount("Old", opening = 80_000L)
        accountDao.archive(accountId)
        val goalId = repository.create(goal("G", 100_000L, accountId))
        val views = repository.loadGoalViews(listOf(goalDao.getById(goalId)!!))
        assertEquals(80_000L, views.first().currentBalanceMinor)
        assertEquals("Old", views.first().accountName)
    }

    @Test
    fun accountDeletion_goalAccountBecomesNull_noCrash() = runBlocking {
        val accountId = seedAccount("Doomed", opening = 10_000L)
        val goalId = repository.create(goal("G", 50_000L, accountId))

        accountDao.delete(accountDao.getById(accountId)!!)

        val views = repository.loadGoalViews(listOf(goalDao.getById(goalId)!!))
        assertNull(views.first().accountName)
        assertEquals(0L, views.first().currentBalanceMinor)
    }

    @Test
    fun observeAll_includesActiveAndArchived() = runBlocking {
        repository.create(goal("Active", 1_000L))
        val archivedId = repository.create(goal("Archived", 2_000L))
        repository.archive(archivedId)

        val all = repository.observeAll().first()
        assertEquals(2, all.size)
        assertTrue(all.any { it.isActive })
        assertTrue(all.any { !it.isActive })
    }
}
