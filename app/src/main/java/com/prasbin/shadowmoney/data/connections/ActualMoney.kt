package com.prasbin.shadowmoney.data.connections

/**
 * How trustworthy the current actual-money figure is:
 * [FULLY_VERIFIED] — every connected source is fresh and timestamped;
 * [PARTIALLY_VERIFIED] — a number exists but something is stale/unverified/failed;
 * [NOT_AVAILABLE] — no connected verified figure exists (never shown as zero).
 */
enum class MoneyVerificationState {
    FULLY_VERIFIED,
    PARTIALLY_VERIFIED,
    NOT_AVAILABLE
}

/**
 * Unified actual-money view over normalized sources.
 *
 * Source-of-truth hierarchy: only CONNECTED_VERIFIED sources are summed into
 * [connectedVerifiedTotalMinor]. Manual and imported sources keep their own provenance
 * and are never merged into that figure — there is no third stored "actual balance"
 * that blends them. When any connected source is stale, unverified, or failed the view
 * is marked not fully verified instead of silently pretending it is; failed sources
 * are excluded and counted, never silently treated as zero.
 */
data class ActualMoneyView(
    val state: MoneyVerificationState,
    val connectedVerifiedTotalMinor: Long?,
    val connectedSourceCount: Int,
    val freshSourceCount: Int,
    val staleSourceCount: Int,
    val unverifiedSourceCount: Int,
    val failedSourceCount: Int,
    val isFullyVerified: Boolean,
    val unavailableReason: String?
)

object ActualMoney {

    /** A connected snapshot older than this is stale. */
    const val DEFAULT_STALE_AFTER_MS = 15 * 60 * 1000L

    fun unifiedActualMoney(
        sources: List<NormalizedFinancialSource>,
        nowMs: Long,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS,
        failedSourceCount: Int = 0
    ): ActualMoneyView {
        val connected = sources.filter { it.provenance == Provenance.CONNECTED_VERIFIED }
        if (connected.isEmpty()) {
            return ActualMoneyView(
                state = MoneyVerificationState.NOT_AVAILABLE,
                connectedVerifiedTotalMinor = null,
                connectedSourceCount = 0,
                freshSourceCount = 0,
                staleSourceCount = 0,
                unverifiedSourceCount = 0,
                failedSourceCount = failedSourceCount,
                isFullyVerified = false,
                unavailableReason = "No connected sources. Manual and imported records stay under " +
                    "their own provenance." +
                    failedSuffix(failedSourceCount)
            )
        }

        val withTimestamp = connected.filter { it.verifiedAtMs != null }
        val withoutTimestamp = connected.filter { it.verifiedAtMs == null }
        val fresh = withTimestamp.filter { nowMs - it.verifiedAtMs!! <= staleAfterMs }
        val stale = withTimestamp.filter { nowMs - it.verifiedAtMs!! > staleAfterMs }

        val total = if (withTimestamp.isEmpty()) null else withTimestamp.sumOf { it.balanceMinor }
        val fullyVerified = stale.isEmpty() && withoutTimestamp.isEmpty() && failedSourceCount == 0

        val state = when {
            total == null -> MoneyVerificationState.NOT_AVAILABLE
            fullyVerified -> MoneyVerificationState.FULLY_VERIFIED
            else -> MoneyVerificationState.PARTIALLY_VERIFIED
        }

        return ActualMoneyView(
            state = state,
            connectedVerifiedTotalMinor = total,
            connectedSourceCount = connected.size,
            freshSourceCount = fresh.size,
            staleSourceCount = stale.size,
            unverifiedSourceCount = withoutTimestamp.size,
            failedSourceCount = failedSourceCount,
            isFullyVerified = fullyVerified,
            unavailableReason = when {
                total == null -> "Connected sources exist but none has a verification time; " +
                    "no total is invented." + failedSuffix(failedSourceCount)
                fullyVerified -> null
                else -> "PARTIALLY VERIFIED — " +
                    listOfNotNull(
                        stale.takeIf { it.isNotEmpty() }?.let { "${it.size} stale source(s)" },
                        withoutTimestamp.takeIf { it.isNotEmpty() }
                            ?.let { "${it.size} source(s) without verification time" },
                        failedSourceCount.takeIf { it > 0 }
                            ?.let { "$it failed source(s), excluded (never counted as zero)" }
                    ).joinToString(", ")
            }
        )
    }

    private fun failedSuffix(failedSourceCount: Int): String =
        if (failedSourceCount > 0) {
            " $failedSourceCount failed source(s) are excluded and never counted as zero."
        } else {
            ""
        }
}
