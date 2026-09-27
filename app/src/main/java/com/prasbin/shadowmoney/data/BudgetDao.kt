package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    fun getByMonth(monthKey: String): Flow<List<Budget>>

    @Query("SELECT COUNT(*) FROM budgets")
    fun observeBudgetCount(): Flow<Int>

    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    suspend fun getByMonthOnce(monthKey: String): List<Budget>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): Budget?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
