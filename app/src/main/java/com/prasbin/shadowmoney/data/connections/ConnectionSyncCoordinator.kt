package com.prasbin.shadowmoney.data.connections

/**
 * Coordinates provider reads into the connection store.
 *
 * Production registers **no** adapters ([PRODUCTION_ADAPTERS] is empty): none of the
 * target providers publish an official public consumer data API, so every provider
 * stays UNAVAILABLE with its honest research note. Test sources register deterministic
 * fakes to exercise the full architecture without ever fabricating production data.
 */
class ConnectionSyncCoordinator(
    private val repository: ConnectionRepository,
    private val adapters: Map<Provider, ProviderAdapter> = PRODUCTION_ADAPTERS,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val staleAfterMs: Long = ActualMoney.DEFAULT_STALE_AFTER_MS
) {

    companion object {
        /** No production adapters exist. Official integrations only — never faked. */
        val PRODUCTION_ADAPTERS: Map<Provider, ProviderAdapter> = emptyMap()
    }

    /**
     * Ensures every known provider has exactly one row, using the honest catalog note
     * when no adapter is registered. Safe to call repeatedly (unique provider row).
     */
    suspend fun ensureCatalog() {
        ProviderCatalog.all().forEach { availability ->
            val existing = repository.getConnectionsOnce()
                .firstOrNull { it.provider == availability.provider }
            if (existing == null) {
                repository.upsertConnection(
                    FinancialConnection(
                        provider = availability.provider,
                        status = availability.status,
                        capabilities = availability.capabilities,
                        availabilityNote = availability.note,
                        updatedAtMs = clock()
                    )
                )
            }
        }
    }

    /** Reads every registered adapter and persists the resulting connection state. */
    suspend fun syncAll(): List<FinancialConnection> =
        ProviderCatalog.all().map { syncProvider(it.provider) }

    suspend fun syncProvider(provider: Provider): FinancialConnection {
        val availability = ProviderCatalog.forProvider(provider)
        val adapter = adapters[provider]
            ?: run {
                val existing = repository.getConnectionsOnce()
                    .firstOrNull { it.provider == provider }
                return existing ?: repository.upsertConnection(
                    FinancialConnection(
                        provider = provider,
                        status = ConnectionStatus.UNAVAILABLE,
                        capabilities = availability?.capabilities ?: emptySet(),
                        availabilityNote = availability?.note
                            ?: "No official interface information available.",
                        updatedAtMs = clock()
                    )
                )
            }

        val now = clock()
        val connection = when (val result = adapter.readVerifiedBalance()) {
            is ProviderReadResult.Verified -> FinancialConnection(
                provider = provider,
                status = if (now - result.snapshot.verifiedAtMs > staleAfterMs) {
                    ConnectionStatus.STALE
                } else {
                    ConnectionStatus.CONNECTED
                },
                capabilities = adapter.capabilities,
                availabilityNote = availability?.note ?: "",
                lastVerifiedAtMs = result.snapshot.verifiedAtMs,
                verifiedBalanceMinor = result.snapshot.balanceMinor,
                updatedAtMs = now
            )
            is ProviderReadResult.AuthRequired -> FinancialConnection(
                provider = provider,
                status = ConnectionStatus.REAUTH_REQUIRED,
                capabilities = adapter.capabilities,
                availabilityNote = result.reason,
                updatedAtMs = now
            )
            is ProviderReadResult.Failed -> FinancialConnection(
                provider = provider,
                status = ConnectionStatus.ERROR,
                capabilities = adapter.capabilities,
                availabilityNote = result.reason,
                updatedAtMs = now
            )
            is ProviderReadResult.Unavailable -> FinancialConnection(
                provider = provider,
                status = ConnectionStatus.UNAVAILABLE,
                capabilities = adapter.capabilities,
                availabilityNote = result.reason,
                updatedAtMs = now
            )
        }
        return repository.upsertConnection(connection)
    }

    /**
     * Downgrades previously connected rows whose last verified reading is older than
     * the staleness threshold, so a stored CONNECTED state can never outlive its data.
     */
    suspend fun refreshStaleness(): List<FinancialConnection> {
        val now = clock()
        return repository.getConnectionsOnce().map { connection ->
            val lastVerified = connection.lastVerifiedAtMs
            if (connection.status == ConnectionStatus.CONNECTED &&
                lastVerified != null && now - lastVerified > staleAfterMs
            ) {
                repository.upsertConnection(connection.copy(status = ConnectionStatus.STALE))
            } else {
                connection
            }
        }
    }

    /** Normalized sources for the unified actual-money view, from stored snapshots. */
    suspend fun connectedSources(): List<NormalizedFinancialSource> =
        repository.getConnectionsOnce()
            .filter { it.status == ConnectionStatus.CONNECTED || it.status == ConnectionStatus.STALE }
            .filter { it.verifiedBalanceMinor != null && it.lastVerifiedAtMs != null }
            .map {
                NormalizedFinancialSource(
                    provenance = Provenance.CONNECTED_VERIFIED,
                    balanceMinor = it.verifiedBalanceMinor!!,
                    provider = it.provider,
                    verifiedAtMs = it.lastVerifiedAtMs
                )
            }
}
