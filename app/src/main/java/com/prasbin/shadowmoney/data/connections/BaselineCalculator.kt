package com.prasbin.shadowmoney.data.connections

/** Outcome of an attempt to establish a baseline from connected sources. */
sealed interface BaselineResult {
    data class Established(val baselines: List<BalanceBaseline>) : BaselineResult
    data class Refused(val reason: String) : BaselineResult
}

/**
 * Baselines are established only from fresh, connected, verified sources. Manual and
 * imported records can never anchor a baseline, and stale readings are refused rather
 * than silently used — a baseline is the reference every later discrepancy is judged
 * against, so it must be trustworthy.
 */
object BaselineCalculator {

    fun setFromConnectedSources(
        sources: List<NormalizedFinancialSource>,
        nowMs: Long,
        staleAfterMs: Long = ActualMoney.DEFAULT_STALE_AFTER_MS
    ): BaselineResult {
        val connected = sources.filter {
            it.provenance == Provenance.CONNECTED_VERIFIED && it.provider != null
        }
        if (connected.isEmpty()) {
            return BaselineResult.Refused(
                "Baseline requires at least one connected, verified source. " +
                    "Official consumer data connections are not currently available."
            )
        }

        val withoutTimestamp = connected.filter { it.verifiedAtMs == null }
        if (withoutTimestamp.isNotEmpty()) {
            return BaselineResult.Refused(
                "Connected source ${withoutTimestamp.first().provider} has no verification time; baseline not set."
            )
        }

        val stale = connected.filter { nowMs - it.verifiedAtMs!! > staleAfterMs }
        if (stale.isNotEmpty()) {
            return BaselineResult.Refused(
                "Connected source ${stale.first().provider} is stale; refresh before setting a baseline."
            )
        }

        val baselines = connected
            .groupBy { it.provider!! }
            .map { (provider, list) ->
                BalanceBaseline(
                    provider = provider,
                    baselineMinor = list.sumOf { it.balanceMinor },
                    provenance = Provenance.CONNECTED_VERIFIED,
                    setAtMs = nowMs
                )
            }
            .sortedBy { it.provider.name }

        return BaselineResult.Established(baselines)
    }
}
