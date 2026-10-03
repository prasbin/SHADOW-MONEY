package com.prasbin.shadowmoney.data.connections

import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Connection model, capability, and provenance-tag semantics: status/capability
 * enums behave as declared, the research catalog stays honest for every target
 * provider, and provenance mapping keeps manual, imported, and connected data
 * strictly distinguishable.
 */
class ConnectionArchitectureTest {

    @Test
    fun connectionStatus_includesEveryLifecycleState() {
        val expected = setOf(
            ConnectionStatus.DISCONNECTED,
            ConnectionStatus.CONNECTING,
            ConnectionStatus.CONNECTED,
            ConnectionStatus.STALE,
            ConnectionStatus.ERROR,
            ConnectionStatus.REAUTH_REQUIRED,
            ConnectionStatus.UNAVAILABLE
        )
        assertEquals(expected, ConnectionStatus.entries.toSet())
    }

    @Test
    fun connectionCapability_includesReadAndInitiateCapabilities() {
        val expected = setOf(
            ConnectionCapability.BALANCE_READ,
            ConnectionCapability.TRANSACTION_READ,
            ConnectionCapability.PAYMENT_INITIATE,
            ConnectionCapability.TRANSFER_INITIATE
        )
        assertEquals(expected, ConnectionCapability.entries.toSet())
    }

    @Test
    fun financialConnection_preservesCapabilitiesAndSnapshot() {
        val connection = FinancialConnection(
            provider = Provider.ESEWA,
            status = ConnectionStatus.CONNECTED,
            capabilities = setOf(ConnectionCapability.BALANCE_READ, ConnectionCapability.PAYMENT_INITIATE),
            availabilityNote = "note",
            lastVerifiedAtMs = 1_000L,
            verifiedBalanceMinor = 50_000L,
            updatedAtMs = 2_000L
        )
        assertEquals(Provider.ESEWA, connection.provider)
        assertEquals(ConnectionStatus.CONNECTED, connection.status)
        assertEquals(
            setOf(ConnectionCapability.BALANCE_READ, ConnectionCapability.PAYMENT_INITIATE),
            connection.capabilities
        )
        assertEquals(50_000L, connection.verifiedBalanceMinor)
    }

    @Test
    fun providerCatalog_allThreeTargetProvidersPresentAndUnavailable() {
        val catalog = ProviderCatalog.all()
        assertEquals(
            listOf(Provider.SANIMA, Provider.GLOBAL_IME, Provider.ESEWA),
            catalog.map { it.provider }
        )
        catalog.forEach { availability ->
            assertEquals(
                "${availability.provider} must be UNAVAILABLE",
                ConnectionStatus.UNAVAILABLE,
                availability.status
            )
        }
    }

    @Test
    fun providerCatalog_noProviderClaimsConsumerBalanceOrTransactionRead() {
        ProviderCatalog.all().forEach { availability ->
            assertEquals(
                ProviderCatalog.NOT_AVAILABLE_CONSUMER_API,
                availability.balanceRead
            )
            assertTrue(
                "transaction read must be consumer-API-unavailable for ${availability.provider}",
                availability.transactionRead == ProviderCatalog.NOT_AVAILABLE_CONSUMER_API ||
                    availability.transactionRead.contains("no consumer", ignoreCase = true)
            )
        }
    }

    @Test
    fun providerCatalog_eSewaDocumentsMerchantApiWithoutImplyingConsumerSync() {
        val esewa = ProviderCatalog.eSewa()
        assertTrue(esewa.officialInterface.contains("developer.esewa.com.np"))
        assertTrue(esewa.note.contains("MERCHANT PAYMENT CATEGORY"))
        assertTrue(esewa.paymentInitiate.contains("approved merchants"))
        assertTrue(esewa.approvalRequired.contains("merchant"))
    }

    @Test
    fun providerCatalog_sanimaAndGlobalImeRequireApprovalNoApi() {
        listOf(ProviderCatalog.sanima(), ProviderCatalog.globalIme()).forEach { availability ->
            assertEquals(ProviderCatalog.NOT_AVAILABLE_CONSUMER_API, availability.paymentInitiate)
            assertTrue(availability.approvalRequired.contains("partnership"))
            assertTrue(availability.authModel.contains("no third-party API"))
        }
    }

    @Test
    fun provenance_emptySourceIsManualEntry() {
        assertEquals(Provenance.MANUAL_ENTRY, provenanceOfTransactionSource(""))
        assertEquals("MANUAL ENTRY", Provenance.MANUAL_ENTRY.label())
    }

    @Test
    fun provenance_importFileSourceIsImported() {
        assertEquals(Provenance.IMPORTED, provenanceOfTransactionSource(TRANSACTION_SOURCE_IMPORT_FILE))
        assertEquals("IMPORTED", Provenance.IMPORTED.label())
    }

    @Test
    fun provenance_connectedSourceIsConnectedVerified() {
        assertEquals(
            Provenance.CONNECTED_VERIFIED,
            provenanceOfTransactionSource(TRANSACTION_SOURCE_CONNECTED)
        )
        assertEquals("CONNECTED VERIFIED", Provenance.CONNECTED_VERIFIED.label())
    }

    @Test
    fun provenance_unknownNonEmptySourceTreatedAsUserProvidedImport() {
        assertEquals(Provenance.IMPORTED, provenanceOfTransactionSource("SOME_BANK_EXPORT"))
    }

    @Test
    fun provenance_manualAndConnectedNeverMapToSameTag() {
        val manual = provenanceOfTransactionSource("")
        val imported = provenanceOfTransactionSource(TRANSACTION_SOURCE_IMPORT_FILE)
        val connected = provenanceOfTransactionSource(TRANSACTION_SOURCE_CONNECTED)
        assertEquals(3, setOf(manual, imported, connected).size)
    }

    @Test
    fun baseline_provenanceDefaultsToConnectedVerified() {
        val baseline = BalanceBaseline(
            provider = Provider.SANIMA,
            baselineMinor = 100_000L,
            setAtMs = 1L
        )
        assertEquals(Provenance.CONNECTED_VERIFIED, baseline.provenance)
        assertEquals(Provenance.CONNECTED_VERIFIED, baseline.provenance)
    }

    @Test
    fun coordinator_productionAdaptersAreEmpty() {
        assertTrue(
            "production must register no adapters until official APIs exist",
            ConnectionSyncCoordinator.PRODUCTION_ADAPTERS.isEmpty()
        )
    }

    @Test
    fun actualMoneyView_connectedTotalNeverInvented() {
        val view = ActualMoney.unifiedActualMoney(emptyList(), nowMs = 1_000L)
        assertEquals(null, view.connectedVerifiedTotalMinor)
        assertFalse(view.isFullyVerified)
    }

    @Test
    fun providerCatalog_enrichmentFieldsArePresentAndHonest() {
        ProviderCatalog.all().forEach { availability ->
            assertTrue(availability.dataSourceType.isNotBlank())
            assertTrue(availability.safeNextAction.isNotBlank())
            assertTrue(availability.supportedCapabilitiesLabel().startsWith("Supported:"))
        }
        listOf(ProviderCatalog.sanima(), ProviderCatalog.globalIme()).forEach { availability ->
            assertTrue(
                "no-consumer-source statement expected for ${availability.provider}",
                availability.dataSourceType.contains("No official consumer data source")
            )
            assertTrue(availability.supportedCapabilitiesLabel().startsWith("Supported: none"))
        }
    }

    @Test
    fun providerCatalog_eSewaDataSourceSaysMerchantApiNotWalletSync() {
        val esewa = ProviderCatalog.eSewa()
        assertEquals(
            "MERCHANT API — NOT A PERSONAL WALLET SYNC INTERFACE",
            esewa.dataSourceType
        )
        assertTrue(esewa.supportedCapabilitiesLabel().contains(ConnectionCapability.PAYMENT_INITIATE.name))
    }

    @Test
    fun moneyVerificationState_hasExactlyThreeStates() {
        assertEquals(
            setOf(
                MoneyVerificationState.FULLY_VERIFIED,
                MoneyVerificationState.PARTIALLY_VERIFIED,
                MoneyVerificationState.NOT_AVAILABLE
            ),
            MoneyVerificationState.entries.toSet()
        )
    }
}
