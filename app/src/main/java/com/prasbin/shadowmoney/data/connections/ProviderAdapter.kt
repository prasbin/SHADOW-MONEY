package com.prasbin.shadowmoney.data.connections

/**
 * Read-only interface a real provider integration implements. The production app
 * registers **no** adapters: none of the target providers publish an official public
 * consumer data API (see [ProviderCatalog]). Fakes implementing this interface exist
 * only in test sources to exercise the connection architecture deterministically.
 *
 * Implementations must use official, approved interfaces only. No scraping, no
 * reverse engineering, no credential harvesting, no MFA bypass.
 */
interface ProviderAdapter {
    val provider: Provider
    val capabilities: Set<ConnectionCapability>

    /** Reads the current verified state. Never returns invented data. */
    suspend fun readVerifiedBalance(): ProviderReadResult
}

/** Result of one official read attempt. */
sealed interface ProviderReadResult {
    data class Verified(val snapshot: VerifiedFinancialSnapshot) : ProviderReadResult

    /** The provider has no official consumer interface (or it is not enabled here). */
    data class Unavailable(val reason: String) : ProviderReadResult

    /** Credentials/session expired; the user must re-authenticate at the provider. */
    data class AuthRequired(val reason: String) : ProviderReadResult

    /** The read failed (network/provider error). No data was obtained. */
    data class Failed(val reason: String) : ProviderReadResult
}
