package com.prasbin.shadowmoney.data.connections

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Coordinator + repository over a real in-memory Room database, driven by
 * deterministic fake adapters (test-only). Covers connection status flow, capability
 * preservation, failed/auth/unavailable handling, duplicate-source prevention,
 * staleness refresh, baseline establishment and honest refusal, and the connected
 * sources feeding the actual-money view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConnectionSyncCoordinatorTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: ConnectionRepository

    private var now = 10_000_000L

    private class FakeAdapter(
        override val provider: Provider,
        override val capabilities: Set<ConnectionCapability>,
        private val result: () -> ProviderReadResult
    ) : ProviderAdapter {
        override suspend fun readVerifiedBalance(): ProviderReadResult = result()
    }

    private fun verifiedAdapter(
        provider: Provider = Provider.SANIMA,
        balanceMinor: Long,
        verifiedAtMs: Long,
        capabilities: Set<ConnectionCapability> = setOf(ConnectionCapability.BALANCE_READ)
    ) = FakeAdapter(provider, capabilities) {
        ProviderReadResult.Verified(
            VerifiedFinancialSnapshot(provider, balanceMinor, verifiedAtMs)
        )
    }

    private fun coordinator(adapters: Map<Provider, ProviderAdapter>) =
        ConnectionSyncCoordinator(
            repository = repository,
            adapters = adapters,
            clock = { now },
            staleAfterMs = ActualMoney.DEFAULT_STALE_AFTER_MS
        )

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, ShadowMoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ConnectionRepository(database.connectionDao())
    }

    @After
    fun closeDb() {
        if (this::database.isInitialized && database.isOpen) database.close()
    }

    @Test
    fun ensureCatalog_createsExactlyOneRowPerTargetProvider_allUnavailable() = runBlocking {
        val subject = coordinator(emptyMap())
        subject.ensureCatalog()
        subject.ensureCatalog()

        val connections = repository.getConnectionsOnce()
        assertEquals(3, connections.size)
        assertEquals(
            setOf(Provider.SANIMA, Provider.GLOBAL_IME, Provider.ESEWA),
            connections.map { it.provider }.toSet()
        )
        connections.forEach { connection ->
            assertEquals(ConnectionStatus.UNAVAILABLE, connection.status)
            assertTrue(connection.availabilityNote.isNotBlank())
            assertNull(connection.verifiedBalanceMinor)
            assertNull(connection.lastVerifiedAtMs)
        }
    }

    @Test
    fun syncProvider_verifiedAdapter_marksConnectedWithSnapshotAndCapabilities() = runBlocking {
        val capabilities =
            setOf(ConnectionCapability.BALANCE_READ, ConnectionCapability.TRANSACTION_READ)
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 250_000L, verifiedAtMs = now, capabilities = capabilities))
        )

        val connection = subject.syncProvider(Provider.SANIMA)

        assertEquals(ConnectionStatus.CONNECTED, connection.status)
        assertEquals(250_000L, connection.verifiedBalanceMinor)
        assertEquals(now, connection.lastVerifiedAtMs)
        assertEquals(capabilities, connection.capabilities)
    }

    @Test
    fun syncProvider_failedAdapter_marksErrorWithoutBalance() = runBlocking {
        val subject = coordinator(
            mapOf(
                Provider.SANIMA to FakeAdapter(
                    Provider.SANIMA,
                    setOf(ConnectionCapability.BALANCE_READ)
                ) { ProviderReadResult.Failed("provider timeout") }
            )
        )

        val connection = subject.syncProvider(Provider.SANIMA)

        assertEquals(ConnectionStatus.ERROR, connection.status)
        assertNull(connection.verifiedBalanceMinor)
        assertEquals("provider timeout", connection.availabilityNote)
    }

    @Test
    fun syncProvider_authRequired_marksReauth() = runBlocking {
        val subject = coordinator(
            mapOf(
                Provider.GLOBAL_IME to FakeAdapter(Provider.GLOBAL_IME, emptySet()) {
                    ProviderReadResult.AuthRequired("session expired")
                }
            )
        )

        val connection = subject.syncProvider(Provider.GLOBAL_IME)

        assertEquals(ConnectionStatus.REAUTH_REQUIRED, connection.status)
    }

    @Test
    fun syncProvider_unavailableResult_keepsHonestUnavailable() = runBlocking {
        val subject = coordinator(
            mapOf(
                Provider.ESEWA to FakeAdapter(Provider.ESEWA, emptySet()) {
                    ProviderReadResult.Unavailable("no consumer API")
                }
            )
        )

        val connection = subject.syncProvider(Provider.ESEWA)

        assertEquals(ConnectionStatus.UNAVAILABLE, connection.status)
        assertEquals("no consumer API", connection.availabilityNote)
    }

    @Test
    fun syncProvider_withoutAdapter_usesCatalogRowAndNeverFabricatesData() = runBlocking {
        val subject = coordinator(emptyMap())

        val connection = subject.syncProvider(Provider.SANIMA)

        assertEquals(ConnectionStatus.UNAVAILABLE, connection.status)
        assertNull(connection.verifiedBalanceMinor)
        assertTrue(connection.availabilityNote.contains("NOT AVAILABLE"))
    }

    @Test
    fun duplicateSources_upsertKeepsSingleRowPerProvider() = runBlocking {
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 100_000L, verifiedAtMs = now))
        )

        val first = subject.syncProvider(Provider.SANIMA)
        now += 60_000L
        val second = subject.syncProvider(Provider.SANIMA)
        subject.ensureCatalog()

        val rows = repository.getConnectionsOnce().filter { it.provider == Provider.SANIMA }
        assertEquals(1, rows.size)
        assertEquals(first.id, second.id)
        assertEquals(100_000L, rows.first().verifiedBalanceMinor)
    }

    @Test
    fun staleSnapshot_syncedAsStaleConnection() = runBlocking {
        val staleAt = now - (ActualMoney.DEFAULT_STALE_AFTER_MS + 1L)
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 90_000L, verifiedAtMs = staleAt))
        )

        val connection = subject.syncProvider(Provider.SANIMA)

        assertEquals(ConnectionStatus.STALE, connection.status)
        assertEquals(90_000L, connection.verifiedBalanceMinor)
    }

    @Test
    fun refreshStaleness_downgradesOldConnectedRowToStale() = runBlocking {
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 100_000L, verifiedAtMs = now))
        )
        subject.syncProvider(Provider.SANIMA)
        assertEquals(ConnectionStatus.CONNECTED, repository.getConnectionsOnce().first().status)

        now += ActualMoney.DEFAULT_STALE_AFTER_MS + 1L
        subject.refreshStaleness()

        assertEquals(ConnectionStatus.STALE, repository.getConnectionsOnce().first().status)
    }

    @Test
    fun connectedSources_reflectsStoredSnapshotsAsConnectedVerified() = runBlocking {
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 120_000L, verifiedAtMs = now))
        )
        subject.syncProvider(Provider.SANIMA)

        val sources = subject.connectedSources()

        assertEquals(1, sources.size)
        assertEquals(Provenance.CONNECTED_VERIFIED, sources.first().provenance)
        assertEquals(Provider.SANIMA, sources.first().provider)
        assertEquals(120_000L, sources.first().balanceMinor)
    }

    @Test
    fun actualMoneyView_fromConnectedSources_totalsVerifiedBalance() = runBlocking {
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 120_000L, verifiedAtMs = now))
        )
        subject.syncProvider(Provider.SANIMA)

        val view = ActualMoney.unifiedActualMoney(subject.connectedSources(), now)

        assertEquals(120_000L, view.connectedVerifiedTotalMinor)
        assertTrue(view.isFullyVerified)
    }

    @Test
    fun baseline_establishedFromFreshConnectedSources() = runBlocking {
        val subject = coordinator(
            mapOf(
                Provider.SANIMA to verifiedAdapter(balanceMinor = 120_000L, verifiedAtMs = now),
                Provider.ESEWA to verifiedAdapter(
                    provider = Provider.ESEWA,
                    balanceMinor = 30_000L,
                    verifiedAtMs = now
                )
            )
        )
        subject.syncAll()

        val result = BaselineCalculator.setFromConnectedSources(subject.connectedSources(), now)

        assertTrue(result is BaselineResult.Established)
        val baselines = (result as BaselineResult.Established).baselines
        assertEquals(2, baselines.size)
        assertEquals(120_000L, baselines.first { it.provider == Provider.SANIMA }.baselineMinor)
        assertEquals(30_000L, baselines.first { it.provider == Provider.ESEWA }.baselineMinor)
        baselines.forEach { assertEquals(Provenance.CONNECTED_VERIFIED, it.provenance) }

        repository.setBaselines(baselines)
        assertEquals(2, repository.getBaselinesOnce().size)
    }

    @Test
    fun baseline_refusedWithoutConnectedSources() = runBlocking {
        val result = BaselineCalculator.setFromConnectedSources(emptyList(), now)
        assertTrue(result is BaselineResult.Refused)
        assertTrue((result as BaselineResult.Refused).reason.contains("not currently available"))
    }

    @Test
    fun baseline_refusedFromManualOrImportedSources() = runBlocking {
        val result = BaselineCalculator.setFromConnectedSources(
            listOf(
                NormalizedFinancialSource(Provenance.MANUAL_ENTRY, 500_000L, Provider.SANIMA, now),
                NormalizedFinancialSource(Provenance.IMPORTED, 400_000L, Provider.SANIMA, now)
            ),
            now
        )
        assertTrue(result is BaselineResult.Refused)
    }

    @Test
    fun baseline_refusedWhenConnectedSourceIsStale() = runBlocking {
        val staleAt = now - (ActualMoney.DEFAULT_STALE_AFTER_MS + 1L)
        val result = BaselineCalculator.setFromConnectedSources(
            listOf(
                NormalizedFinancialSource(
                    provenance = Provenance.CONNECTED_VERIFIED,
                    balanceMinor = 100_000L,
                    provider = Provider.SANIMA,
                    verifiedAtMs = staleAt
                )
            ),
            now
        )
        assertTrue(result is BaselineResult.Refused)
        assertTrue((result as BaselineResult.Refused).reason.contains("stale"))
    }

    @Test
    fun baseline_refusedWhenVerificationTimeMissing() = runBlocking {
        val result = BaselineCalculator.setFromConnectedSources(
            listOf(
                NormalizedFinancialSource(
                    provenance = Provenance.CONNECTED_VERIFIED,
                    balanceMinor = 100_000L,
                    provider = Provider.SANIMA,
                    verifiedAtMs = null
                )
            ),
            now
        )
        assertTrue(result is BaselineResult.Refused)
    }

    @Test
    fun discrepancy_pipeline_endToEndWithFakeConnection() = runBlocking {
        val subject = coordinator(
            mapOf(Provider.SANIMA to verifiedAdapter(balanceMinor = 100_000L, verifiedAtMs = now))
        )
        subject.syncProvider(Provider.SANIMA)
        val sources = subject.connectedSources()
        val baseline = (BaselineCalculator.setFromConnectedSources(sources, now) as BaselineResult.Established)
            .baselines.first()

        val expected = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = sources.size,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = baseline.baselineMinor,
                recordedNetChangeMinor = -20_000L,
                latestVerifiedBalanceMinor = 80_000L
            )
        )
        assertEquals(DiscrepancyState.EXPECTED_CHANGE, expected.state)

        val unexplained = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = sources.size,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = baseline.baselineMinor,
                recordedNetChangeMinor = -20_000L,
                latestVerifiedBalanceMinor = 60_000L
            )
        )
        assertEquals(DiscrepancyState.UNEXPLAINED_REDUCTION, unexplained.state)
        assertEquals(-20_000L, unexplained.deltaMinor)
    }
}
