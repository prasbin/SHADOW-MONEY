package com.prasbin.shadowmoney.data.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Baseline audit trail: connected sources carry their verification time and the
 * source set used, and [BaselineCalculator.summarize] deterministically reproduces
 * the original balance from stored rows (including legacy rows without audit
 * fields).
 */
class BaselineSummaryTest {

    private val now = 5_000_000L

    private fun connected(
        provider: Provider,
        balanceMinor: Long,
        verifiedAtMs: Long?
    ) = NormalizedFinancialSource(
        provenance = Provenance.CONNECTED_VERIFIED,
        balanceMinor = balanceMinor,
        provider = provider,
        verifiedAtMs = verifiedAtMs
    )

    @Test
    fun setFromConnectedSources_recordsAuditFieldsPerRow() {
        val result = BaselineCalculator.setFromConnectedSources(
            sources = listOf(
                connected(Provider.SANIMA, 100_000L, now - 60_000L),
                connected(Provider.SANIMA, 50_000L, now - 30_000L),
                connected(Provider.ESEWA, 30_000L, now - 10_000L)
            ),
            nowMs = now
        )
        assertTrue(result is BaselineResult.Established)
        val rows = (result as BaselineResult.Established).baselines
        assertEquals(now, rows.first().setAtMs)
        assertEquals("SANIMA,ESEWA", rows.first().sourceSet)

        val sanima = rows.first { it.provider == Provider.SANIMA }
        assertEquals(150_000L, sanima.baselineMinor)
        assertEquals(now - 30_000L, sanima.sourceVerifiedAtMs)
        assertEquals("SANIMA,ESEWA", sanima.sourceSet)

        val esewa = rows.first { it.provider == Provider.ESEWA }
        assertEquals(now - 10_000L, esewa.sourceVerifiedAtMs)
    }

    @Test
    fun summarize_sumsRowsIntoOriginalBalanceDeterministically() {
        val rows = listOf(
            BalanceBaseline(Provider.SANIMA, 100_000L, setAtMs = 1_000L, sourceSet = "ESEWA,SANIMA"),
            BalanceBaseline(Provider.ESEWA, 50_000L, setAtMs = 1_000L, sourceSet = "ESEWA,SANIMA")
        )
        val summary = BaselineCalculator.summarize(rows)
        assertEquals(150_000L, summary!!.originalBalanceMinor)
        assertEquals(1_000L, summary.setAtMs)
        assertEquals(listOf(Provider.ESEWA, Provider.SANIMA), summary.sourceSet)
        assertEquals(2, summary.rows.size)

        val reversedInput = rows.reversed()
        assertEquals(
            summary.originalBalanceMinor,
            BaselineCalculator.summarize(reversedInput)!!.originalBalanceMinor
        )
        assertEquals(
            summary.sourceSet,
            BaselineCalculator.summarize(reversedInput)!!.sourceSet
        )
    }

    @Test
    fun summarize_legacyRowsWithoutAuditFields_fallsBackToRowProviders() {
        val summary = BaselineCalculator.summarize(
            listOf(
                BalanceBaseline(Provider.SANIMA, 75_000L, setAtMs = 2_000L),
                BalanceBaseline(Provider.GLOBAL_IME, 25_000L, setAtMs = 3_000L)
            )
        )
        assertEquals(100_000L, summary!!.originalBalanceMinor)
        assertEquals(3_000L, summary.setAtMs)
        assertEquals(listOf(Provider.GLOBAL_IME, Provider.SANIMA), summary.sourceSet)
    }

    @Test
    fun summarize_emptyReturnsNull() {
        assertNull(BaselineCalculator.summarize(emptyList()))
    }
}
