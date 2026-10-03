package com.prasbin.shadowmoney.data.connections

import com.prasbin.shadowmoney.data.ConnectionDao
import com.prasbin.shadowmoney.data.model.toDomain
import com.prasbin.shadowmoney.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persistence for connection metadata and baselines. Stores only provider/status/
 * capability/snapshot metadata — never credentials, tokens, sessions, or provider
 * account identifiers. Connections and baselines are separate rows from accounts and
 * transactions; this repository never writes to the money tables.
 */
class ConnectionRepository(
    private val connectionDao: ConnectionDao
) {

    fun observeConnections(): Flow<List<FinancialConnection>> =
        connectionDao.observeConnections().map { list -> list.map { it.toDomain() } }

    fun observeBaselines(): Flow<List<BalanceBaseline>> =
        connectionDao.observeBaselines().map { list -> list.map { it.toDomain() } }

    suspend fun getConnectionsOnce(): List<FinancialConnection> =
        connectionDao.getConnectionsOnce().map { it.toDomain() }

    suspend fun getBaselinesOnce(): List<BalanceBaseline> =
        connectionDao.getBaselinesOnce().map { it.toDomain() }

    /**
     * Inserts or updates the row for its provider, keeping a stable row id so a
     * provider can never appear twice (unique index on provider).
     */
    suspend fun upsertConnection(connection: FinancialConnection): FinancialConnection {
        val existing = connectionDao.getConnection(connection.provider.name)
        val toStore = connection.copy(id = existing?.id ?: 0L)
        val rowId = connectionDao.upsertConnection(toStore.toEntity())
        return toStore.copy(id = if (toStore.id == 0L) rowId else toStore.id)
    }

    /**
     * Replaces the whole baseline set atomically (clear + insert in one transaction):
     * the stored set is always exactly the sources used for the current original
     * balance, never a union with a previous set.
     */
    suspend fun setBaselines(baselines: List<BalanceBaseline>) {
        connectionDao.replaceBaselines(baselines.map { it.toEntity() })
    }

    suspend fun clearBaselines() = connectionDao.clearBaselines()
}
