package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WorkItem
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkItemDao {

    @Query("""
        SELECT * FROM work_items
        WHERE (:statusFilter IS NULL OR status = :statusFilter)
        AND (:search IS NULL OR title LIKE '%' || :search || '%')
        ORDER BY createdTimestamp DESC
    """)
    fun observeWorkItems(statusFilter: Int?, search: String?): Flow<List<WorkItem>>

    @Query("SELECT * FROM work_items ORDER BY createdTimestamp DESC")
    fun observeAll(): Flow<List<WorkItem>>

    @Query("SELECT * FROM work_items WHERE id = :id")
    fun observeById(id: Long): Flow<WorkItem?>

    @Query("SELECT * FROM work_items WHERE id = :id")
    suspend fun getById(id: Long): WorkItem?

    @Insert
    suspend fun insert(workItem: WorkItem): Long

    @Update
    suspend fun update(workItem: WorkItem)

    @Delete
    suspend fun delete(workItem: WorkItem)

    @Query("UPDATE work_items SET status = :status, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, updatedTimestamp: Long)

    @Query("SELECT COUNT(*) FROM work_items")
    fun observeCount(): Flow<Int>
}
