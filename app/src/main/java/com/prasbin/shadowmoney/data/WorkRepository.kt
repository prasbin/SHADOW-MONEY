package com.prasbin.shadowmoney.data

import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.WorkItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class WorkItemView(
    val workItem: WorkItem,
    val receivedMinor: Long,
    val remainingExpectedMinor: Long,
    val deadlineStatus: DeadlineStatus
)

data class WorkDetailData(
    val workItem: WorkItem,
    val receivedMinor: Long,
    val remainingExpectedMinor: Long,
    val deadlineStatus: DeadlineStatus,
    val transactions: List<Transaction>,
    val linkableTransactions: List<Transaction>
)

class WorkRepository(
    private val workItemDao: WorkItemDao,
    private val transactionDao: TransactionDao,
    private val openHelper: SupportSQLiteOpenHelper,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun observeWorkItems(statusFilter: Int?, search: String?): Flow<List<WorkItem>> =
        workItemDao.observeWorkItems(statusFilter, search)

    fun observeChanges(): Flow<Unit> = combine(
        workItemDao.observeCount(),
        transactionDao.observeTransactionCount()
    ) { _, _ -> Unit }

    fun observeWorkDetail(id: Long): Flow<WorkDetailData?> = combine(
        workItemDao.observeById(id),
        transactionDao.observeTransactionsForWorkItem(id),
        transactionDao.observeLinkableTransactions(id),
        transactionDao.observeTransactionCount()
    ) { workItem, transactions, linkable, _ ->
        if (workItem == null) return@combine null
        val received = transactions.filter { it.direction == com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME }
            .sumOf { it.amountMinor }
        WorkDetailData(
            workItem = workItem,
            receivedMinor = received,
            remainingExpectedMinor = WorkMath.remainingExpectedMinor(
                workItem.expectedAmountMinor,
                received
            ),
            deadlineStatus = WorkMath.deadlineStatus(workItem.deadlineTimestamp, clock()),
            transactions = transactions,
            linkableTransactions = linkable
        )
    }

    suspend fun loadViews(items: List<WorkItem>): List<WorkItemView> {
        val receivedMap = loadReceivedTotals()
        val now = clock()
        return items.map { item ->
            val received = receivedMap[item.id] ?: 0L
            WorkItemView(
                workItem = item,
                receivedMinor = received,
                remainingExpectedMinor = WorkMath.remainingExpectedMinor(item.expectedAmountMinor, received),
                deadlineStatus = WorkMath.deadlineStatus(item.deadlineTimestamp, now)
            )
        }
    }

    private fun loadReceivedTotals(): Map<Long, Long> {
        val cursor = openHelper.readableDatabase.query(
            "SELECT workItemId, SUM(amountMinor) AS totalMinor " +
                "FROM transactions WHERE workItemId IS NOT NULL AND direction = 0 " +
                "GROUP BY workItemId"
        )
        val result = mutableMapOf<Long, Long>()
        cursor.use {
            while (cursor.moveToNext()) {
                result[cursor.getLong(0)] = if (cursor.isNull(1)) 0L else cursor.getLong(1)
            }
        }
        return result
    }

    suspend fun create(
        title: String,
        description: String,
        expectedAmountMinor: Long,
        deadlineTimestamp: Long,
        client: String
    ): Long {
        validateTitle(title)
        validateAmount(expectedAmountMinor)
        val now = clock()
        return workItemDao.insert(
            WorkItem(
                title = title,
                description = description,
                expectedAmountMinor = expectedAmountMinor,
                deadlineTimestamp = deadlineTimestamp,
                client = client,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun update(
        workItem: WorkItem,
        title: String,
        description: String,
        expectedAmountMinor: Long,
        deadlineTimestamp: Long,
        client: String
    ) {
        validateTitle(title)
        validateAmount(expectedAmountMinor)
        workItemDao.update(
            workItem.copy(
                title = title,
                description = description,
                expectedAmountMinor = expectedAmountMinor,
                deadlineTimestamp = deadlineTimestamp,
                client = client,
                updatedTimestamp = clock()
            )
        )
    }

    suspend fun archive(id: Long) {
        workItemDao.updateStatus(id, WORK_STATUS_ARCHIVED, clock())
    }

    suspend fun delete(workItem: WorkItem) {
        workItemDao.delete(workItem)
    }

    suspend fun linkTransaction(transactionId: Long, workItemId: Long?) {
        transactionDao.setWorkItemId(transactionId, workItemId)
    }

    private fun validateTitle(title: String) {
        if (title.isBlank()) {
            throw IllegalArgumentException("Title is required")
        }
    }

    private fun validateAmount(amountMinor: Long) {
        if (amountMinor < 0L) {
            throw IllegalArgumentException("Amount cannot be negative")
        }
    }
}
