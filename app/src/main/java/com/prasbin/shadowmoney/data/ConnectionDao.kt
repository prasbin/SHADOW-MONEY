package com.prasbin.shadowmoney.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.prasbin.shadowmoney.data.model.BalanceBaselineEntity
import com.prasbin.shadowmoney.data.model.FinancialConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectionDao {

    @Query("SELECT * FROM financial_connections ORDER BY provider")
    fun observeConnections(): Flow<List<FinancialConnectionEntity>>

    @Query("SELECT * FROM financial_connections ORDER BY provider")
    suspend fun getConnectionsOnce(): List<FinancialConnectionEntity>

    @Query("SELECT * FROM financial_connections WHERE provider = :provider LIMIT 1")
    suspend fun getConnection(provider: String): FinancialConnectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConnection(entity: FinancialConnectionEntity): Long

    @Query("DELETE FROM financial_connections WHERE provider = :provider")
    suspend fun deleteConnection(provider: String)

    @Query("SELECT * FROM balance_baselines ORDER BY provider")
    fun observeBaselines(): Flow<List<BalanceBaselineEntity>>

    @Query("SELECT * FROM balance_baselines ORDER BY provider")
    suspend fun getBaselinesOnce(): List<BalanceBaselineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBaseline(entity: BalanceBaselineEntity): Long

    @Query("DELETE FROM balance_baselines")
    suspend fun clearBaselines()

    @Query("DELETE FROM balance_baselines WHERE provider = :provider")
    suspend fun deleteBaseline(provider: String)
}
