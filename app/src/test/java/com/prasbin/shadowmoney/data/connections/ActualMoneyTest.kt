package com.prasbin.shadowmoney.data.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unified actual-money view: connected verified sources are summed, manual and
 * imported sources are excluded (never merged), staleness marks the view partially
 * verified, and with no connected sources no total is invented.
 */
class ActualMoneyTest {

    private val now = 1_000_000L
    private val freshAt = now - 60_000L
    private val staleAt = now - (ActualMoney.DEFAULT_STALE_AFTER_MS + 1L)

    private fun connected(balanceMinor: Long, atMs: Long?, provider: Provider = Provider.SANIMA) =
        NormalizedFinancialSource(
            provenance = Provenance.CONNECTED_VERIFIED,
            balanceMinor = balanceMinor,
            provider = provider,
            verifiedAtMs = atMs
        )

    @Test
    fun noSources_reportsNoTotalAndNoAvailability() {
        val view = ActualMoney.unifiedActualMoney(emptyList(), now)
        assertNull(view.connectedVerifiedTotalMinor)
        assertEquals(0, view.connectedSourceCount)
        assertFalse(view.isFullyVerified)
        assertNotNull(view.unavailableReason)
    }

    @Test
    fun singleFreshConnectedSource_totalsExactlyThatBalance() {
        val view = ActualMoney.unifiedActualMoney(listOf(connected(150_000L, freshAt)), now)
        assertEquals(150_000L, view.connectedVerifiedTotalMinor)
        assertTrue(view.isFullyVerified)
        assertEquals(1, view.freshSourceCount)
        assertEquals(0, view.staleSourceCount)
        assertNull(view.unavailableReason)
    }

    @Test
    fun multipleConnectedSources_combinedVerifiedBalance() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(
                connected(100_000L, freshAt, Provider.SANIMA),
                connected(50_000L, freshAt, Provider.ESEWA)
            ),
            now
        )
        assertEquals(150_000L, view.connectedVerifiedTotalMinor)
        assertEquals(2, view.connectedSourceCount)
        assertTrue(view.isFullyVerified)
    }

    @Test
    fun manualAndImportedSources_neverMergedIntoConnectedTotal() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(
                connected(100_000L, freshAt),
                NormalizedFinancialSource(
                    provenance = Provenance.MANUAL_ENTRY,
                    balanceMinor = 999_999L
                ),
                NormalizedFinancialSource(
                    provenance = Provenance.IMPORTED,
                    balanceMinor = 888_888L
                )
            ),
            now
        )
        assertEquals(100_000L, view.connectedVerifiedTotalMinor)
        assertEquals(1, view.connectedSourceCount)
    }

    @Test
    fun onlyManualAndImportedSources_reportsNoConnectedTotal() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(
                NormalizedFinancialSource(Provenance.MANUAL_ENTRY, 50_000L),
                NormalizedFinancialSource(Provenance.IMPORTED, 70_000L)
            ),
            now
        )
        assertNull(view.connectedVerifiedTotalMinor)
        assertEquals(0, view.connectedSourceCount)
    }

    @Test
    fun staleSource_marksViewPartiallyVerified() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(connected(100_000L, staleAt)),
            now
        )
        assertEquals(100_000L, view.connectedVerifiedTotalMinor)
        assertFalse(view.isFullyVerified)
        assertEquals(1, view.staleSourceCount)
        assertEquals(0, view.freshSourceCount)
        assertTrue(view.unavailableReason!!.contains("PARTIALLY VERIFIED"))
        assertTrue(view.unavailableReason!!.contains("stale"))
    }

    @Test
    fun partialVerification_oneFreshOneStale_countsBoth() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(connected(100_000L, freshAt), connected(60_000L, staleAt, Provider.ESEWA)),
            now
        )
        assertEquals(160_000L, view.connectedVerifiedTotalMinor)
        assertEquals(1, view.freshSourceCount)
        assertEquals(1, view.staleSourceCount)
        assertFalse(view.isFullyVerified)
    }

    @Test
    fun missingVerificationTime_marksUnverifiedAndReportsNoTotal() {
        val view = ActualMoney.unifiedActualMoney(
            listOf(connected(100_000L, null)),
            now
        )
        assertNull(view.connectedVerifiedTotalMinor)
        assertEquals(1, view.unverifiedSourceCount)
        assertFalse(view.isFullyVerified)
    }

    @Test
    fun stalenessThreshold_boundaryIsInclusive() {
        val exactlyAtThreshold = now - ActualMoney.DEFAULT_STALE_AFTER_MS
        val view = ActualMoney.unifiedActualMoney(
            listOf(connected(100_000L, exactlyAtThreshold)),
            now
        )
        assertTrue(view.isFullyVerified)
        assertEquals(1, view.freshSourceCount)
    }
}
