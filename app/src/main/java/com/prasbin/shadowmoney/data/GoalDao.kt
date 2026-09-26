package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Goal
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActive(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Query("SELECT * FROM goals WHERE isCompleted = 0 AND isActive = 1")
    fun getActiveNotCompleted(): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: Goal): Long

    @Update
    suspend fun update(goal: Goal)

    @Delete
    suspend fun delete(goal: Goal)

    @Query("UPDATE goals SET isCompleted = 1 WHERE id = :id")
    suspend fun markComplete(id: Long)

    @Query("UPDATE goals SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)
}
