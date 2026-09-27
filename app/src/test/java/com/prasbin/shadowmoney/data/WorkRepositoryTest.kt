package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
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
class WorkRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var workItemDao: WorkItemDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var accountDao: AccountDao
    private lateinit var repository: WorkRepository

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
        categoryDao = database.categoryDao()
        accountDao = database.accountDao()
        repository = WorkRepository(
            workItemDao = workItemDao,
            transactionDao = transactionDao,
            openHelper = database.openHelper,
            clock = { now }
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun seedAccount(): Long = runBlocking {
        accountDao.insert(Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))
    }

    private fun tx(
        accountId: Long,
        amount: Long,
        direction: Int,
        timestamp: Long = now - 10_000L
    ) = Transaction(
        accountId = accountId,
        amountMinor = amount,
        direction = direction,
        transactionTimestamp = timestamp,
        note = "Test"
    )

    @Test
    fun create_persistsWorkItem() = runBlocking {
        val id = repository.create("Website", "Client website build", 100_000L, 0L, "Acme")
        assertTrue(id > 0)
        val stored = workItemDao.getById(id)
        assertNotNull(stored)
        assertEquals("Website", stored!!.title)
        assertEquals("Client website build", stored.description)
        assertEquals(100_000L, stored.expectedAmountMinor)
        assertEquals(WORK_STATUS_ACTIVE, stored.status)
        assertEquals("Acme", stored.client)
    }

    @Test
    fun create_blankTitle_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("  ", "desc", 1_000L, 0L, "") }
        }
        Unit
    }

    @Test
    fun create_negativeAmount_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("Title", "desc", -1L, 0L, "") }
        }
        Unit
    }

    @Test
    fun create_zeroAmount_allowed() = runBlocking {
        val id = repository.create("Title", "desc", 0L, 0L, "")
        assertTrue(id > 0)
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = repository.create("Title", "desc", 1_000L, 0L, "")
        val item = workItemDao.getById(id)!!
        repository.update(item, "New title", "new desc", 2_000L, now + 86_400_000L, "Client")
        val updated = workItemDao.getById(id)!!
        assertEquals("New title", updated.title)
        assertEquals(2_000L, updated.expectedAmountMinor)
        assertEquals(now + 86_400_000L, updated.deadlineTimestamp)
        assertTrue(updated.updatedTimestamp >= item.updatedTimestamp)
    }

    @Test
    fun archive_setsArchivedStatus() = runBlocking {
        val id = repository.create("Title", "desc", 1_000L, 0L, "")
        repository.archive(id)
        assertEquals(WORK_STATUS_ARCHIVED, workItemDao.getById(id)!!.status)
    }

    @Test
    fun delete_removesWorkItem() = runBlocking {
        val id = repository.create("Title", "desc", 1_000L, 0L, "")
        repository.delete(workItemDao.getById(id)!!)
        assertNull(workItemDao.getById(id))
    }

    @Test
    fun linking_incomeTransaction_countsTowardReceived() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 40_000L, TRANSACTION_DIRECTION_INCOME))

        repository.linkTransaction(txId, workId)

        val views = repository.loadViews(workItemDao.getById(workId)?.let { listOf(it) } ?: emptyList())
        assertEquals(40_000L, views.first().receivedMinor)
        assertEquals(60_000L, views.first().remainingExpectedMinor)
    }

    @Test
    fun linking_outflowTransaction_notCountedAsReceived() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 30_000L, TRANSACTION_DIRECTION_OUTFLOW))

        repository.linkTransaction(txId, workId)

        val views = repository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(0L, views.first().receivedMinor)
    }

    @Test
    fun received_sumsMultipleLinkedIncomeTransactions() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val first = transactionDao.insert(tx(accountId, 20_000L, TRANSACTION_DIRECTION_INCOME))
        val second = transactionDao.insert(tx(accountId, 35_000L, TRANSACTION_DIRECTION_INCOME))
        val unrelated = transactionDao.insert(tx(accountId, 99_000L, TRANSACTION_DIRECTION_INCOME))

        repository.linkTransaction(first, workId)
        repository.linkTransaction(second, workId)

        val views = repository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(55_000L, views.first().receivedMinor)
    }

    @Test
    fun received_canExceedExpected_negativeRemaining() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 50_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 80_000L, TRANSACTION_DIRECTION_INCOME))
        repository.linkTransaction(txId, workId)

        val views = repository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(80_000L, views.first().receivedMinor)
        assertEquals(-30_000L, views.first().remainingExpectedMinor)
    }

    @Test
    fun received_noLinkedIncome_isZero() = runBlocking {
        val workId = repository.create("Project", "", 50_000L, 0L, "")
        val views = repository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(0L, views.first().receivedMinor)
    }

    @Test
    fun unlink_removesReceivedButKeepsTransaction() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 40_000L, TRANSACTION_DIRECTION_INCOME))
        repository.linkTransaction(txId, workId)
        repository.linkTransaction(txId, null)

        val views = repository.loadViews(listOf(workItemDao.getById(workId)!!))
        assertEquals(0L, views.first().receivedMinor)
        val transaction = transactionDao.getById(txId)
        assertNotNull(transaction)
        assertNull(transaction!!.workItemId)
    }

    @Test
    fun deletingWorkItem_transactionSurvivesWithNullWorkItemId() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 40_000L, TRANSACTION_DIRECTION_INCOME))
        repository.linkTransaction(txId, workId)

        repository.delete(workItemDao.getById(workId)!!)

        val transaction = transactionDao.getById(txId)
        assertNotNull(transaction)
        assertNull(transaction!!.workItemId)
        assertEquals(40_000L, transaction.amountMinor)
    }

    @Test
    fun linking_doesNotChangeTransactionAmount() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(tx(accountId, 40_000L, TRANSACTION_DIRECTION_INCOME))
        repository.linkTransaction(txId, workId)
        repository.linkTransaction(txId, null)
        val transaction = transactionDao.getById(txId)!!
        assertEquals(40_000L, transaction.amountMinor)
    }

    @Test
    fun statusFilter_andSearch_workCorrectly() = runBlocking {
        repository.create("Website build", "", 1_000L, 0L, "")
        repository.create("Logo design", "", 2_000L, 0L, "")
        repository.create("Website copywriting", "", 3_000L, 0L, "")

        val active = repository.observeWorkItems(WORK_STATUS_ACTIVE, null).first()
        assertEquals(3, active.size)

        val search = repository.observeWorkItems(null, "website").first()
        assertEquals(2, search.size)

        val combined = repository.observeWorkItems(WORK_STATUS_ACTIVE, "logo").first()
        assertEquals(1, combined.size)

        val archived = repository.observeWorkItems(WORK_STATUS_ARCHIVED, null).first()
        assertTrue(archived.isEmpty())
    }

    @Test
    fun observeWorkDetail_includesLinkedAndLinkableTransactions() = runBlocking {
        val accountId = seedAccount()
        val workId = repository.create("Project", "", 100_000L, 0L, "")
        val linked = transactionDao.insert(tx(accountId, 40_000L, TRANSACTION_DIRECTION_INCOME))
        val unlinked = transactionDao.insert(tx(accountId, 10_000L, TRANSACTION_DIRECTION_INCOME))
        repository.linkTransaction(linked, workId)

        val detail = repository.observeWorkDetail(workId).first()
        assertNotNull(detail)
        assertEquals(40_000L, detail!!.receivedMinor)
        assertEquals(1, detail.transactions.size)
        assertTrue(detail.linkableTransactions.any { it.id == unlinked })
    }
}
