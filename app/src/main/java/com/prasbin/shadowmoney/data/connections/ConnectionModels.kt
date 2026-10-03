package com.prasbin.shadowmoney.data.connections

import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE

/** A financial institution or wallet a real-money connection could target. */
enum class Provider {
    SANIMA,
    GLOBAL_IME,
    ESEWA,
    OTHER
}

/** Lifecycle of one connection. UNAVAILABLE means no official consumer data interface exists today. */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    STALE,
    ERROR,
    REAUTH_REQUIRED,
    UNAVAILABLE
}

/** What a provider interface could support. Declared capability is never a promise of availability. */
enum class ConnectionCapability {
    BALANCE_READ,
    TRANSACTION_READ,
    PAYMENT_INITIATE,
    TRANSFER_INITIATE
}

/** Where a piece of money data came from. Never merged across tags into a third stored figure. */
enum class Provenance {
    MANUAL_ENTRY,
    IMPORTED,
    CONNECTED_VERIFIED
}

const val TRANSACTION_SOURCE_CONNECTED = "CONNECTED"

/**
 * Maps a stored transaction source string to its provenance tag. Empty source is a
 * manual entry, IMPORT_FILE is user-provided imported data, CONNECTED is verified
 * connected data; any other non-empty value is treated as user-provided imported data.
 */
fun provenanceOfTransactionSource(source: String): Provenance = when {
    source.isEmpty() -> Provenance.MANUAL_ENTRY
    source == TRANSACTION_SOURCE_CONNECTED -> Provenance.CONNECTED_VERIFIED
    source == TRANSACTION_SOURCE_IMPORT_FILE -> Provenance.IMPORTED
    else -> Provenance.IMPORTED
}

/** Human label for a provenance tag, used in UI chips and provenance rows. */
fun Provenance.label(): String = when (this) {
    Provenance.MANUAL_ENTRY -> "MANUAL ENTRY"
    Provenance.IMPORTED -> "IMPORTED"
    Provenance.CONNECTED_VERIFIED -> "CONNECTED VERIFIED"
}

/**
 * One real-money connection: provider, lifecycle status, declared capabilities, the
 * honest availability note from research, and the latest verified snapshot (metadata
 * only — never credentials, tokens, or account identifiers).
 */
data class FinancialConnection(
    val id: Long = 0L,
    val provider: Provider,
    val status: ConnectionStatus,
    val capabilities: Set<ConnectionCapability> = emptySet(),
    val availabilityNote: String = "",
    val lastVerifiedAtMs: Long? = null,
    val verifiedBalanceMinor: Long? = null,
    val updatedAtMs: Long = 0L
)

/** A balance actually read from a connected source, with the moment it was verified. */
data class VerifiedFinancialSnapshot(
    val provider: Provider,
    val balanceMinor: Long,
    val verifiedAtMs: Long
)

/**
 * A normalized money source with its provenance. Connected-verified sources feed the
 * unified actual-money view; manual and imported sources keep their own tags and are
 * never folded into a connected total.
 */
data class NormalizedFinancialSource(
    val provenance: Provenance,
    val balanceMinor: Long,
    val provider: Provider? = null,
    val verifiedAtMs: Long? = null
)

/** Baseline established from connected verified sources only. */
data class BalanceBaseline(
    val provider: Provider,
    val baselineMinor: Long,
    val provenance: Provenance = Provenance.CONNECTED_VERIFIED,
    val setAtMs: Long
)
