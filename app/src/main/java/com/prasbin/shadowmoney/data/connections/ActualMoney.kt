package com.prasbin.shadowmoney.data.connections

/**
 * Unified actual-money view over normalized sources.
 *
 * Source-of-truth hierarchy: only CONNECTED_VERIFIED sources are summed into
 * [connectedVerifiedTotalMinor]. Manual and imported sources keep their own provenance
 * and are never merged into that figure — there is no third stored "actual balance"
 * that blends them. When any connected source is stale or missing a verification time
 * the view is marked not fully verified instead of silently pretending it is.
 */
data class ActualMoneyView(
    val connectedVerifiedTotalMinor: Long?,
    val connectedSourceCount: Int,
    val freshSourceCount: Int,
    val staleSourceCount: Int,
    val unverifiedSourceCount: Int,
    val isFullyVerified: Boolean,
    val unavailableReason: String?
)

object ActualMoney {

    /** A connected snapshot older than this is stale. */
    const val DEFAULT_STALE_AFTER_MS = 15 * 60 * 1000L

    fun unifiedActualMoney(
        sources: List<NormalizedFinancialSource>,
        nowMs: Long,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS
    ): ActualMoneyView {
        val connected = sources.filter { it.provenance == Provenance.CONNECTED_VERIFIED }
        if (connected.isEmpty()) {
            return ActualMoneyView(
                connectedVerifiedTotalMinor = null,
                connectedSourceCount = 0,
                freshSourceCount = 0,
                staleSourceCount = 0,
                unverifiedSourceCount = 0,
                isFullyVerified = false,
                unavailableReason = "No connected sources. Manual and imported records stay under their own provenance."
            )
        }

        val withTimestamp = connected.filter { it.verifiedAtMs != null }
        val withoutTimestamp = connected.filter { it.verifiedAtMs == null }
        val fresh = withTimestamp.filter { nowMs - it.verifiedAtMs!! <= staleAfterMs }
        val stale = withTimestamp.filter { nowMs - it.verifiedAtMs!! > staleAfterMs }

        val total = if (withTimestamp.isEmpty()) null else withTimestamp.sumOf { it.balanceMinor }
        val fullyVerified = stale.isEmpty() && withoutTimestamp.isEmpty()

        return ActualMoneyView(
            connectedVerifiedTotalMinor = total,
            connectedSourceCount = connected.size,
            freshSourceCount = fresh.size,
            staleSourceCount = stale.size,
            unverifiedSourceCount = withoutTimestamp.size,
            isFullyVerified = fullyVerified,
            unavailableReason = if (fullyVerified) null
            else "PARTIALLY VERIFIED — " +
                listOfNotNull(
                    stale.takeIf { it.isNotEmpty() }?.let { "${it.size} stale source(s)" },
                    withoutTimestamp.takeIf { it.isNotEmpty() }?.let { "${it.size} source(s) without verification time" }
                ).joinToString(", ")
        )
    }
}
