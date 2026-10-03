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
 * Inputs to one reconciliation evaluation.
 *
 * [verifiedNetChangeMinor] is the net change from CONNECTED_VERIFIED activity since
 * the baseline was set; [importedNetChangeMinor] is user-provided (imported) activity;
 * [manualNetChangeMinor] is manually recorded activity. Each provenance is tracked
 * separately so the explanation can distinguish verified movement from user-provided
 * movement — imported data never becomes verified merely because its numbers fit.
 * [latestVerifiedBalanceMinor] is the summed current connected verified reading.
 */
data class DiscrepancyInput(
    val connectedSourceCount: Int,
    val status: ConnectionStatus? = null,
    val affectedProviders: List<Provider> = emptyList(),
    val baselineMinor: Long? = null,
    val verifiedNetChangeMinor: Long = 0L,
    val importedNetChangeMinor: Long = 0L,
    val manualNetChangeMinor: Long = 0L,
    val latestVerifiedBalanceMinor: Long? = null
)

/**
 * Reconciliation result. [deltaMinor] is the unexplained residual
 * (current − (original + explained movement)) when a comparison ran, null otherwise.
 * [belowOriginalBalance] is true whenever the current verified money is lower than the
 * original balance — surfaced as a warning even when the reduction is fully explained.
 */
data class DiscrepancyResult(
    val state: DiscrepancyState,
    val deltaMinor: Long?,
    val explanation: String,
    val originalMinor: Long? = null,
    val currentMinor: Long? = null,
    val differenceMinor: Long? = null,
    val explainedMovementMinor: Long? = null,
    val unexplainedMinor: Long? = null,
    val affectedProviders: List<Provider> = emptyList(),
    val belowOriginalBalance: Boolean = false,
    val isExplained: Boolean = false
)

/**
 * Pure comparison between the original verified baseline, the current verified
 * reading, and the known recorded activity. Never mutates data and never invents a
 * reading: with no baseline or no verified reading it reports an honest
 * non-comparison state. An explained reduction is reported as an explained change —
 * only a residual that records cannot account for is an unexplained reduction.
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
                explanation = affectedText(
                    input.affectedProviders,
                    "Connection failed during its last read",
                    "The connection failed during its last read"
                ) + ". No comparison was made.",
                affectedProviders = input.affectedProviders
            )
            ConnectionStatus.REAUTH_REQUIRED -> return DiscrepancyResult(
                state = DiscrepancyState.REAUTH_REQUIRED,
                deltaMinor = null,
                explanation = affectedText(
                    input.affectedProviders,
                    "Re-authentication required at",
                    "Re-authentication is required at the provider"
                ) + " before comparing balances.",
                affectedProviders = input.affectedProviders
            )
            ConnectionStatus.STALE, ConnectionStatus.UNAVAILABLE, ConnectionStatus.DISCONNECTED -> {
                return DiscrepancyResult(
                    state = DiscrepancyState.CONNECTION_STALE,
                    deltaMinor = null,
                    explanation = affectedText(
                        input.affectedProviders,
                        "Stale reading for",
                        "The connected reading"
                    ) + " is stale. This comparison is incomplete until it refreshes.",
                    affectedProviders = input.affectedProviders
                )
            }
            else -> Unit
        }

        val baseline = input.baselineMinor
        if (baseline == null) {
            return DiscrepancyResult(
                state = DiscrepancyState.NO_BASELINE,
                deltaMinor = null,
                explanation = "NO VERIFIED BASELINE AVAILABLE — establish a baseline from " +
                    "connected, verified sources first."
            )
        }

        val latest = input.latestVerifiedBalanceMinor
        if (latest == null) {
            return DiscrepancyResult(
                state = DiscrepancyState.CONNECTION_STALE,
                deltaMinor = null,
                explanation = "No fresh verified reading to compare against the original balance.",
                originalMinor = baseline,
                affectedProviders = input.affectedProviders
            )
        }

        val explainedMovement = input.verifiedNetChangeMinor +
            input.importedNetChangeMinor + input.manualNetChangeMinor
        val expected = baseline + explainedMovement
        val residual = latest - expected
        val difference = latest - baseline
        val below = latest < baseline

        return when {
            residual == 0L -> DiscrepancyResult(
                state = DiscrepancyState.EXPECTED_CHANGE,
                deltaMinor = 0L,
                explanation = if (below) {
                    "BALANCE CHANGE EXPLAINED — money is below the original balance and " +
                        "recorded activity accounts for the full difference."
                } else {
                    "BALANCE CHANGE EXPLAINED — recorded activity accounts for the current " +
                        "balance exactly."
                },
                originalMinor = baseline,
                currentMinor = latest,
                differenceMinor = difference,
                explainedMovementMinor = explainedMovement,
                unexplainedMinor = 0L,
                belowOriginalBalance = below,
                isExplained = true
            )
            residual < 0L -> DiscrepancyResult(
                state = DiscrepancyState.UNEXPLAINED_REDUCTION,
                deltaMinor = residual,
                explanation = "UNEXPLAINED REDUCTION — part of the drop from the original " +
                    "balance cannot be reconciled with recorded activity.",
                originalMinor = baseline,
                currentMinor = latest,
                differenceMinor = difference,
                explainedMovementMinor = explainedMovement,
                unexplainedMinor = residual,
                belowOriginalBalance = below,
                isExplained = false
            )
            else -> DiscrepancyResult(
                state = DiscrepancyState.ACTUAL_DISCREPANCY,
                deltaMinor = residual,
                explanation = "UNEXPLAINED INFLOW — the balance is higher than recorded " +
                    "activity explains.",
                originalMinor = baseline,
                currentMinor = latest,
                differenceMinor = difference,
                explainedMovementMinor = explainedMovement,
                unexplainedMinor = residual,
                belowOriginalBalance = below,
                isExplained = false
            )
        }
    }

    private fun affectedText(
        providers: List<Provider>,
        namedPrefix: String,
        unnamedPrefix: String
    ): String =
        if (providers.isEmpty()) {
            unnamedPrefix
        } else {
            "$namedPrefix ${providers.joinToString(", ") { it.name }}"
        }
}
