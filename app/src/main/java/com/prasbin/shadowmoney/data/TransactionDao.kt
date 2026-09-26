package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transactionTimestamp DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY transactionTimestamp DESC")
    fun getByAccountId(accountId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY transactionTimestamp DESC")
    fun getByCategoryId(categoryId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY transactionTimestamp DESC, id DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTransactionCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 0 AND accountId IN (SELECT id FROM accounts WHERE isActive = 1)")
    suspend fun getTotalIncomeMinorForActiveAccounts(): Long

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM transactions WHERE direction = 1 AND accountId IN (SELECT id FROM accounts WHERE isActive = 1)")
    suspend fun getTotalOutflowMinorForActiveAccounts(): Long

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
