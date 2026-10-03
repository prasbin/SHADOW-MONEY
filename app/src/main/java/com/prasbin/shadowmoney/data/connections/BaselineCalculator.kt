package com.prasbin.shadowmoney.data.connections

/** Outcome of an attempt to establish a baseline from connected sources. */
sealed interface BaselineResult {
    data class Established(val baselines: List<BalanceBaseline>) : BaselineResult
    data class Refused(val reason: String) : BaselineResult
}

/**
 * The stored baseline set presented as the ORIGINAL BALANCE: the deterministic sum of
 * its per-source rows, the shared creation time, and the source set used. Recomputing
 * [originalBalanceMinor] from the same stored rows always yields the same number —
 * the baseline is reproducible and auditable.
 */
data class BaselineSummary(
    val originalBalanceMinor: Long,
    val setAtMs: Long,
    val sourceSet: List<Provider>,
    val rows: List<BalanceBaseline>
)

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
                "NO VERIFIED BASELINE AVAILABLE — baseline requires at least one connected, " +
                    "verified source. Official consumer data connections are not currently available."
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

        val sourceSetLabel = connected.map { it.provider!! }.distinct().sorted().joinToString(",")

        val baselines = connected
            .groupBy { it.provider!! }
            .map { (provider, list) ->
                BalanceBaseline(
                    provider = provider,
                    baselineMinor = list.sumOf { it.balanceMinor },
                    provenance = Provenance.CONNECTED_VERIFIED,
                    setAtMs = nowMs,
                    sourceVerifiedAtMs = list.mapNotNull { it.verifiedAtMs }.maxOrNull(),
                    sourceSet = sourceSetLabel
                )
            }
            .sortedBy { it.provider.name }

        return BaselineResult.Established(baselines)
    }

    /**
     * Deterministic presentation of stored baseline rows: sorted rows, sum of their
     * amounts, latest shared set time, and the recorded source set (falling back to
     * the providers actually present in the rows). Null when no baseline exists.
     */
    fun summarize(baselines: List<BalanceBaseline>): BaselineSummary? {
        if (baselines.isEmpty()) return null
        val rows = baselines.sortedBy { it.provider.name }
        val recordedSet = rows.first().sourceSet
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { name -> runCatching { Provider.valueOf(name) }.getOrNull() }
            ?: emptyList()
        return BaselineSummary(
            originalBalanceMinor = rows.sumOf { it.baselineMinor },
            setAtMs = rows.maxOf { it.setAtMs },
            sourceSet = recordedSet.ifEmpty { rows.map { it.provider } },
            rows = rows
        )
    }
}
