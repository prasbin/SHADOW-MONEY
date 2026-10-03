package com.prasbin.shadowmoney.data.connections

/** States the discrepancy engine can report, in evaluation priority order. */
enum class DiscrepancyState {
    NO_CONNECTED_SOURCES,
    CONNECTION_ERROR,
    REAUTH_REQUIRED,
    CONNECTION_STALE,
    NO_BASELINE,
    EXPECTED_CHANGE,
    UNEXPLAINED_REDUCTION,
    ACTUAL_DISCREPANCY
}

/**
 * Inputs to one evaluation. [recordedNetChangeMinor] is the net recorded change
 * (income minus outflow) since the baseline was set; [latestVerifiedBalanceMinor]
 * is the newest connected verified reading.
 */
data class DiscrepancyInput(
    val connectedSourceCount: Int,
    val status: ConnectionStatus? = null,
    val baselineMinor: Long? = null,
    val recordedNetChangeMinor: Long = 0L,
    val latestVerifiedBalanceMinor: Long? = null
)

data class DiscrepancyResult(
    val state: DiscrepancyState,
    val deltaMinor: Long?,
    val explanation: String
)

/**
 * Pure comparison between what the records say should have happened and what the
 * connected source actually verifies. Never mutates data and never invents a reading:
 * with no baseline or no verified reading it reports an honest non-comparison state.
 */
object DiscrepancyEngine {

    fun evaluate(input: DiscrepancyInput): DiscrepancyResult {
        if (input.connectedSourceCount <= 0) {
            return DiscrepancyResult(
                state = DiscrepancyState.NO_CONNECTED_SOURCES,
                deltaMinor = null,
                explanation = "No connected sources yet. Nothing to compare against."
            )
        }
        when (input.status) {
            ConnectionStatus.ERROR -> return DiscrepancyResult(
                state = DiscrepancyState.CONNECTION_ERROR,
                deltaMinor = null,
                explanation = "The connection failed during its last read. No comparison was made."
            )
            ConnectionStatus.REAUTH_REQUIRED -> return DiscrepancyResult(
                state = DiscrepancyState.REAUTH_REQUIRED,
                deltaMinor = null,
                explanation = "Re-authentication is required at the provider before comparing balances."
            )
            ConnectionStatus.STALE, ConnectionStatus.UNAVAILABLE, ConnectionStatus.DISCONNECTED -> {
                return DiscrepancyResult(
                    state = DiscrepancyState.CONNECTION_STALE,
                    deltaMinor = null,
                    explanation = "The connected reading is stale. Refresh before comparing balances."
                )
            }
            else -> Unit
        }

        val baseline = input.baselineMinor
        if (baseline == null) {
            return DiscrepancyResult(
                state = DiscrepancyState.NO_BASELINE,
                deltaMinor = null,
                explanation = "No baseline established from connected sources yet."
            )
        }

        val latest = input.latestVerifiedBalanceMinor
        if (latest == null) {
            return DiscrepancyResult(
                state = DiscrepancyState.CONNECTION_STALE,
                deltaMinor = null,
                explanation = "No fresh verified reading to compare against the baseline."
            )
        }

        val expected = baseline + input.recordedNetChangeMinor
        val delta = latest - expected
        return when {
            delta == 0L -> DiscrepancyResult(
                state = DiscrepancyState.EXPECTED_CHANGE,
                deltaMinor = 0L,
                explanation = "Recorded activity explains the connected balance exactly."
            )
            delta < 0L -> DiscrepancyResult(
                state = DiscrepancyState.UNEXPLAINED_REDUCTION,
                deltaMinor = delta,
                explanation = "Connected balance is lower than expected — money left without a recorded entry."
            )
            else -> DiscrepancyResult(
                state = DiscrepancyState.ACTUAL_DISCREPANCY,
                deltaMinor = delta,
                explanation = "Connected balance is higher than expected — an unrecorded inflow."
            )
        }
    }
}
