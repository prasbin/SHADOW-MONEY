package com.prasbin.shadowmoney.data.connections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every discrepancy state, exercised on the pure engine with controlled inputs. */
class DiscrepancyEngineTest {

    @Test
    fun noConnectedSources_reportsHonestNonComparison() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(connectedSourceCount = 0)
        )
        assertEquals(DiscrepancyState.NO_CONNECTED_SOURCES, result.state)
        assertNull(result.deltaMinor)
    }

    @Test
    fun noBaseline_reportsNoBaseline() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = null,
                latestVerifiedBalanceMinor = 100_000L
            )
        )
        assertEquals(DiscrepancyState.NO_BASELINE, result.state)
        assertNull(result.deltaMinor)
    }

    @Test
    fun connectionError_takesPriorityOverBaselineComparison() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.ERROR,
                baselineMinor = 100_000L,
                latestVerifiedBalanceMinor = 90_000L
            )
        )
        assertEquals(DiscrepancyState.CONNECTION_ERROR, result.state)
        assertNull(result.deltaMinor)
    }

    @Test
    fun reauthRequired_reportedBeforeAnyComparison() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.REAUTH_REQUIRED,
                baselineMinor = 100_000L,
                latestVerifiedBalanceMinor = 100_000L
            )
        )
        assertEquals(DiscrepancyState.REAUTH_REQUIRED, result.state)
    }

    @Test
    fun staleConnection_reportedBeforeAnyComparison() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.STALE,
                baselineMinor = 100_000L,
                latestVerifiedBalanceMinor = 100_000L
            )
        )
        assertEquals(DiscrepancyState.CONNECTION_STALE, result.state)
    }

    @Test
    fun staleReadingWithoutTimestamp_reportedAsStale() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                latestVerifiedBalanceMinor = null
            )
        )
        assertEquals(DiscrepancyState.CONNECTION_STALE, result.state)
    }

    @Test
    fun explainedChange_expectedWhenRecordedActivityMatchesExactly() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = -20_000L,
                latestVerifiedBalanceMinor = 80_000L
            )
        )
        assertEquals(DiscrepancyState.EXPECTED_CHANGE, result.state)
        assertEquals(0L, result.deltaMinor)
    }

    @Test
    fun unexplainedReduction_whenConnectedBalanceDropsBelowExpected() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = -10_000L,
                latestVerifiedBalanceMinor = 70_000L
            )
        )
        assertEquals(DiscrepancyState.UNEXPLAINED_REDUCTION, result.state)
        assertEquals(-20_000L, result.deltaMinor)
    }

    @Test
    fun actualDiscrepancy_whenConnectedBalanceExceedsExpected() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 2,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = 0L,
                latestVerifiedBalanceMinor = 115_000L
            )
        )
        assertEquals(DiscrepancyState.ACTUAL_DISCREPANCY, result.state)
        assertEquals(15_000L, result.deltaMinor)
    }

    @Test
    fun evaluationPriority_noSourcesBeatsEverythingElse() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 0,
                status = ConnectionStatus.ERROR,
                baselineMinor = 1L
            )
        )
        assertEquals(DiscrepancyState.NO_CONNECTED_SOURCES, result.state)
    }

    @Test
    fun evaluation_priorityOrderIsStable() {
        assertEquals(
            listOf(
                DiscrepancyState.NO_CONNECTED_SOURCES,
                DiscrepancyState.CONNECTION_ERROR,
                DiscrepancyState.REAUTH_REQUIRED,
                DiscrepancyState.CONNECTION_STALE,
                DiscrepancyState.NO_BASELINE,
                DiscrepancyState.EXPECTED_CHANGE,
                DiscrepancyState.UNEXPLAINED_REDUCTION,
                DiscrepancyState.ACTUAL_DISCREPANCY
            ),
            DiscrepancyState.entries
        )
    }

    @Test
    fun explainedReductionBelowOriginal_isExpectedChangeAndFlaggedBelowOriginal() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = -20_000L,
                latestVerifiedBalanceMinor = 80_000L
            )
        )
        assertEquals(DiscrepancyState.EXPECTED_CHANGE, result.state)
        assertTrue(result.belowOriginalBalance)
        assertTrue(result.isExplained)
        assertEquals(100_000L, result.originalMinor)
        assertEquals(80_000L, result.currentMinor)
        assertEquals(-20_000L, result.differenceMinor)
        assertEquals(-20_000L, result.explainedMovementMinor)
        assertEquals(0L, result.unexplainedMinor)
    }

    @Test
    fun importedAndManualMovement_bothExplainTheDifference() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = -5_000L,
                importedNetChangeMinor = -10_000L,
                manualNetChangeMinor = -5_000L,
                latestVerifiedBalanceMinor = 80_000L
            )
        )
        assertEquals(DiscrepancyState.EXPECTED_CHANGE, result.state)
        assertEquals(-20_000L, result.explainedMovementMinor)
        assertEquals(0L, result.unexplainedMinor)
    }

    @Test
    fun unexplainedReduction_fillsReconciliationFigures() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                verifiedNetChangeMinor = -10_000L,
                latestVerifiedBalanceMinor = 70_000L
            )
        )
        assertEquals(DiscrepancyState.UNEXPLAINED_REDUCTION, result.state)
        assertEquals(100_000L, result.originalMinor)
        assertEquals(70_000L, result.currentMinor)
        assertEquals(-30_000L, result.differenceMinor)
        assertEquals(-10_000L, result.explainedMovementMinor)
        assertEquals(-20_000L, result.unexplainedMinor)
        assertEquals(-20_000L, result.deltaMinor)
        assertTrue(result.belowOriginalBalance)
        assertFalse(result.isExplained)
    }

    @Test
    fun affectedProviders_carriedThroughForConnectionStates() {
        val error = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.ERROR,
                affectedProviders = listOf(Provider.SANIMA, Provider.ESEWA),
                baselineMinor = 100_000L
            )
        )
        assertEquals(DiscrepancyState.CONNECTION_ERROR, error.state)
        assertEquals(listOf(Provider.SANIMA, Provider.ESEWA), error.affectedProviders)
        assertTrue(error.explanation.contains("SANIMA"))
        assertTrue(error.explanation.contains("ESEWA"))
    }

    @Test
    fun sameBalanceWithNoMovement_isExpectedChangeNotDiscrepancy() {
        val result = DiscrepancyEngine.evaluate(
            DiscrepancyInput(
                connectedSourceCount = 1,
                status = ConnectionStatus.CONNECTED,
                baselineMinor = 100_000L,
                latestVerifiedBalanceMinor = 100_000L
            )
        )
        assertEquals(DiscrepancyState.EXPECTED_CHANGE, result.state)
        assertEquals(0L, result.deltaMinor)
        assertFalse(result.belowOriginalBalance)
        assertTrue(result.isExplained)
    }
}
