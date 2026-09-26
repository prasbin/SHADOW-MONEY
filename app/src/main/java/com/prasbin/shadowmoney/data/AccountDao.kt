package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Account
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<Account>>

    @Query("SELECT * FROM accounts ORDER BY name ASC")
    fun getAll(): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): Account?

    @Query("SELECT openingBalanceMinor FROM accounts WHERE id = :id")
    suspend fun getOpeningBalance(id: Long): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Update
    suspend fun update(account: Account)

    @Delete
    suspend fun delete(account: Account)

    @Query("UPDATE accounts SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COALESCE(SUM(openingBalanceMinor), 0) FROM accounts WHERE isActive = 1")
    suspend fun getTotalBalanceMinor(): Long

    @Query("SELECT COALESCE(SUM(openingBalanceMinor), 0) FROM accounts WHERE id = :accountId AND isActive = 1")
    suspend fun getAccountBalanceMinor(accountId: Long): Long
}
