package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /**
     * Deliberate FULL-DATASET query — loads every transaction in timestamp order.
     *
     * Known production callers are one-shot, user-initiated full scans only:
     * - [com.prasbin.shadowmoney.data.imports.ImportRepository.loadReference] (CSV preview
     *   duplicate fingerprinting must compare against every existing record to be correct)
     *
     * Exposed through `TransactionRepository.getAll()` for tests/facades.
     *
     * Every interactive/UI path must stay bounded instead: Dashboard uses [getRecent] (20),
     * the Transactions screen uses [getRecent] (100), intelligence/assistant use
     * [getTransactionsInWindow] or SQL aggregates, and backup export uses the dedicated
     * full-table reads in `BackupDao`. Do NOT wire this query into a screen collection.
     */
    @Query("SELECT * FROM transactions ORDER BY transactionTimestamp DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY transactionTimestamp DESC")
    fun getByAccountId(accountId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY transactionTimestamp DESC")
    fun getByCategoryId(categoryId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY transactionTimestamp DESC, id DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE transactionTimestamp >= :start AND transactionTimestamp <= :end ORDER BY transactionTimestamp DESC")
    fun getTransactionsInWindow(start: Long, end: Long): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTransactionCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 0 AND accountId IN (SELECT id FROM accounts WHERE isActive = 1)")
    suspend fun getTotalIncomeMinorForActiveAccounts(): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 1 AND accountId IN (SELECT id FROM accounts WHERE isActive = 1)")
    suspend fun getTotalOutflowMinorForActiveAccounts(): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 1 AND transactionTimestamp >= :start AND transactionTimestamp < :end")
    suspend fun getOutflowTotalForPeriod(start: Long, end: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 0 AND transactionTimestamp >= :start AND transactionTimestamp < :end")
    suspend fun getIncomeTotalForPeriod(start: Long, end: Long): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE transactionTimestamp >= :start AND transactionTimestamp < :end")
    suspend fun getCountInPeriod(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE direction = :direction AND transactionTimestamp >= :start AND transactionTimestamp < :end")
    suspend fun getCountInPeriodByDirection(start: Long, end: Long, direction: Int): Int

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 1 AND categoryId = :categoryId AND transactionTimestamp >= :start AND transactionTimestamp < :end")
    suspend fun getOutflowTotalForCategoryPeriod(categoryId: Long, start: Long, end: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE workItemId = :workItemId AND direction = 0")
    fun observeReceivedForWorkItem(workItemId: Long): Flow<Long>

    @Query("SELECT * FROM transactions WHERE workItemId = :workItemId ORDER BY transactionTimestamp DESC")
    fun observeTransactionsForWorkItem(workItemId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE (workItemId IS NULL OR workItemId = :workItemId) ORDER BY transactionTimestamp DESC")
    fun observeLinkableTransactions(workItemId: Long?): Flow<List<Transaction>>

    @Query("UPDATE transactions SET workItemId = :workItemId WHERE id = :transactionId")
    suspend fun setWorkItemId(transactionId: Long, workItemId: Long?)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND direction = 0")
    suspend fun getTotalIncomeMinor(accountId: Long): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE accountId = :accountId AND direction = 1")
    suspend fun getTotalOutflowMinor(accountId: Long): Long

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int
}
