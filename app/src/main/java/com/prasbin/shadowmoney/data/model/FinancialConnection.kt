package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.prasbin.shadowmoney.data.connections.ConnectionCapability
import com.prasbin.shadowmoney.data.connections.ConnectionStatus
import com.prasbin.shadowmoney.data.connections.FinancialConnection
import com.prasbin.shadowmoney.data.connections.Provenance
import com.prasbin.shadowmoney.data.connections.Provider

/**
 * Persisted connection metadata: provider, lifecycle status, declared capabilities,
 * the honest availability note, and the latest verified snapshot. Stores never any
 * credential, token, PIN, OTP, session, or provider account identifier.
 */
@Entity(
    tableName = "financial_connections",
    indices = [Index(value = ["provider"], unique = true)]
)
data class FinancialConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    val status: String,
    val capabilities: String = "",
    val availabilityNote: String = "",
    val lastVerifiedAtMs: Long? = null,
    val verifiedBalanceMinor: Long? = null,
    val updatedAtMs: Long = 0L
)

/**
 * A baseline established from connected verified sources only. Never derived from
 * manual entries or imported rows.
 */
@Entity(
    tableName = "balance_baselines",
    indices = [Index(value = ["provider"], unique = true)]
)
data class BalanceBaselineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String,
    val baselineMinor: Long,
    val provenance: String,
    val setAtMs: Long,
    val sourceVerifiedAtMs: Long? = null,
    val sourceSet: String? = null
)

fun FinancialConnectionEntity.toDomain(): FinancialConnection = FinancialConnection(
    id = id,
    provider = runCatching { Provider.valueOf(provider) }.getOrDefault(Provider.OTHER),
    status = runCatching { ConnectionStatus.valueOf(status) }.getOrDefault(ConnectionStatus.UNAVAILABLE),
    capabilities = capabilities.split(',')
        .filter { it.isNotBlank() }
        .mapNotNull { name -> runCatching { ConnectionCapability.valueOf(name) }.getOrNull() }
        .toSet(),
    availabilityNote = availabilityNote,
    lastVerifiedAtMs = lastVerifiedAtMs,
    verifiedBalanceMinor = verifiedBalanceMinor,
    updatedAtMs = updatedAtMs
)

fun FinancialConnection.toEntity(): FinancialConnectionEntity = FinancialConnectionEntity(
    id = id,
    provider = provider.name,
    status = status.name,
    capabilities = capabilities.joinToString(",") { it.name },
    availabilityNote = availabilityNote,
    lastVerifiedAtMs = lastVerifiedAtMs,
    verifiedBalanceMinor = verifiedBalanceMinor,
    updatedAtMs = updatedAtMs
)

fun BalanceBaselineEntity.toDomain(): com.prasbin.shadowmoney.data.connections.BalanceBaseline =
    com.prasbin.shadowmoney.data.connections.BalanceBaseline(
        provider = runCatching { Provider.valueOf(provider) }.getOrDefault(Provider.OTHER),
        baselineMinor = baselineMinor,
        provenance = runCatching { Provenance.valueOf(provenance) }.getOrDefault(Provenance.CONNECTED_VERIFIED),
        setAtMs = setAtMs,
        sourceVerifiedAtMs = sourceVerifiedAtMs,
        sourceSet = sourceSet
    )

fun com.prasbin.shadowmoney.data.connections.BalanceBaseline.toEntity(): BalanceBaselineEntity =
    BalanceBaselineEntity(
        id = 0,
        provider = provider.name,
        baselineMinor = baselineMinor,
        provenance = provenance.name,
        setAtMs = setAtMs,
        sourceVerifiedAtMs = sourceVerifiedAtMs,
        sourceSet = sourceSet
    )
