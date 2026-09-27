package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.Opportunity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpportunityDao {

    @Query("""
        SELECT * FROM opportunities
        WHERE (:statusFilter IS NULL OR status = :statusFilter)
        AND (:typeFilter IS NULL OR type = :typeFilter)
        AND (:sourceFilter IS NULL OR source = :sourceFilter)
        AND (
            :search IS NULL
            OR title LIKE '%' || :search || '%'
            OR client LIKE '%' || :search || '%'
            OR description LIKE '%' || :search || '%'
            OR source LIKE '%' || :search || '%'
        )
        ORDER BY createdTimestamp DESC
    """)
    fun observeOpportunities(
        statusFilter: Int?,
        typeFilter: Int?,
        sourceFilter: String?,
        search: String?
    ): Flow<List<Opportunity>>

    @Query("SELECT * FROM opportunities WHERE id = :id")
    fun observeById(id: Long): Flow<Opportunity?>

    @Query("SELECT * FROM opportunities WHERE id = :id")
    suspend fun getById(id: Long): Opportunity?

    @Insert
    suspend fun insert(opportunity: Opportunity): Long

    @Update
    suspend fun update(opportunity: Opportunity)

    @Delete
    suspend fun delete(opportunity: Opportunity)

    @Query("UPDATE opportunities SET status = :status, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, updatedTimestamp: Long)

    @Query("SELECT COUNT(*) FROM opportunities")
    fun observeCount(): Flow<Int>
}
