package com.prasbin.shadowmoney.presentation.screen.connections

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.connections.ActualMoney
import com.prasbin.shadowmoney.data.connections.ActualMoneyView
import com.prasbin.shadowmoney.data.connections.BalanceBaseline
import com.prasbin.shadowmoney.data.connections.BaselineCalculator
import com.prasbin.shadowmoney.data.connections.BaselineResult
import com.prasbin.shadowmoney.data.connections.BaselineSummary
import com.prasbin.shadowmoney.data.connections.ConnectionRepository
import com.prasbin.shadowmoney.data.connections.ConnectionStatus
import com.prasbin.shadowmoney.data.connections.ConnectionSyncCoordinator
import com.prasbin.shadowmoney.data.connections.DiscrepancyEngine
import com.prasbin.shadowmoney.data.connections.DiscrepancyInput
import com.prasbin.shadowmoney.data.connections.DiscrepancyResult
import com.prasbin.shadowmoney.data.connections.FinancialConnection
import com.prasbin.shadowmoney.data.connections.NormalizedFinancialSource
import com.prasbin.shadowmoney.data.connections.Provenance
import com.prasbin.shadowmoney.data.connections.ProviderAvailability
import com.prasbin.shadowmoney.data.connections.ProviderCatalog
import com.prasbin.shadowmoney.data.connections.ReconciliationActivity
import com.prasbin.shadowmoney.data.model.ImportedStatement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionsUiState(
    val providers: List<ProviderAvailability> = emptyList(),
    val connections: List<FinancialConnection> = emptyList(),
    val baselines: List<BalanceBaseline> = emptyList(),
    val baselineSummary: BaselineSummary? = null,
    val actualMoney: ActualMoneyView? = null,
    val discrepancy: DiscrepancyResult? = null,
    val baselineMessage: String? = null,
    /** Imported statement evidence (IMPORTED / USER-PROVIDED), never merged. */
    val importedStatements: List<ImportedStatement> = emptyList()
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Drives the Connections screen from the real connection architecture: honest
 * provider availability, stored connection state, the unified actual-money view,
 * baseline establishment (refuses without connected sources), and the pure
 * reconciliation engine fed with recorded ledger activity since the baseline was
 * set. Never fabricates a connection or a balance. Imported statements appear
 * only as IMPORTED / USER-PROVIDED evidence with their own age labels — they are
 * never merged into connected or verified figures.
 */
class ConnectionsViewModel(
    private val repository: ConnectionRepository,
    private val coordinator: ConnectionSyncCoordinator,
    private val activity: ReconciliationActivity? = null,
    private val importedStatements: Flow<List<ImportedStatement>>? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val baselineMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ConnectionsUiState> = combine(
        repository.observeConnections(),
        repository.observeBaselines(),
        baselineMessage,
        importedStatements ?: flowOf(emptyList())
    ) { connections, baselines, message, statements ->
        Quadruple(connections, baselines, message, statements)
    }.flatMapLatest { (connections, baselines, message, statements) ->
        val summary = BaselineCalculator.summarize(baselines)
        flow {
            val recorded = if (summary != null && activity != null) {
                runCatching { activity.netActivitySince(summary.setAtMs) }.getOrNull()
            } else {
                null
            }
            emit(buildState(connections, baselines, summary, recorded, message, statements))
        }
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionsUiState())

    private fun buildState(
        connections: List<FinancialConnection>,
        baselines: List<BalanceBaseline>,
        summary: BaselineSummary?,
        recorded: ReconciliationActivity.RecordedActivity?,
        message: String?,
        statements: List<ImportedStatement>
    ): ConnectionsUiState {
        val now = clock()
        val sources = connections
            .filter {
                it.status == ConnectionStatus.CONNECTED || it.status == ConnectionStatus.STALE
            }
            .filter { it.verifiedBalanceMinor != null && it.lastVerifiedAtMs != null }
            .map {
                NormalizedFinancialSource(
                    provenance = Provenance.CONNECTED_VERIFIED,
                    balanceMinor = it.verifiedBalanceMinor!!,
                    provider = it.provider,
                    verifiedAtMs = it.lastVerifiedAtMs
                )
            }

        val connectedCount = sources.size
        val worstStatus = when {
            connections.any { it.status == ConnectionStatus.ERROR } -> ConnectionStatus.ERROR
            connections.any { it.status == ConnectionStatus.REAUTH_REQUIRED } -> ConnectionStatus.REAUTH_REQUIRED
            connections.any { it.status == ConnectionStatus.STALE } -> ConnectionStatus.STALE
            connections.any { it.status == ConnectionStatus.CONNECTED } -> ConnectionStatus.CONNECTED
            else -> null
        }
        val affectedProviders = if (worstStatus == null) {
            emptyList()
        } else {
            connections.filter { it.status == worstStatus }.map { it.provider }.distinct()
                .sortedBy { it.name }
        }
        val failedSourceCount = connections.count { it.status == ConnectionStatus.ERROR }
        val latestVerified = if (sources.isEmpty()) null else sources.sumOf { it.balanceMinor }

        return ConnectionsUiState(
            providers = ProviderCatalog.all(),
            connections = connections,
            baselines = baselines,
            baselineSummary = summary,
            actualMoney = ActualMoney.unifiedActualMoney(
                sources = sources,
                nowMs = now,
                failedSourceCount = failedSourceCount
            ),
            discrepancy = DiscrepancyEngine.evaluate(
                DiscrepancyInput(
                    connectedSourceCount = connectedCount,
                    status = worstStatus,
                    affectedProviders = affectedProviders,
                    baselineMinor = summary?.originalBalanceMinor,
                    verifiedNetChangeMinor = recorded?.verifiedNetChangeMinor ?: 0L,
                    importedNetChangeMinor = recorded?.importedNetChangeMinor ?: 0L,
                    manualNetChangeMinor = recorded?.manualNetChangeMinor ?: 0L,
                    latestVerifiedBalanceMinor = latestVerified
                )
            ),
            baselineMessage = message,
            importedStatements = statements
        )
    }

    init {
        scope.launch {
            runCatching {
                coordinator.ensureCatalog()
                coordinator.refreshStaleness()
            }
        }
    }

    /**
     * Attempts to establish baselines from connected sources. With no official
     * consumer connections available this honestly refuses and reports why. On
     * success the message states the original balance and the source set used.
     */
    fun setBaselineFromConnectedSources() {
        scope.launch {
            val result = runCatching {
                BaselineCalculator.setFromConnectedSources(
                    sources = coordinator.connectedSources(),
                    nowMs = clock()
                )
            }.getOrElse { error ->
                BaselineResult.Refused(error.message ?: "Baseline could not be set.")
            }
            when (result) {
                is BaselineResult.Established -> {
                    repository.setBaselines(result.baselines)
                    val summary = BaselineCalculator.summarize(result.baselines)
                    baselineMessage.value = if (summary != null) {
                        "Baseline set: original balance " +
                            "${Money.formatNpr(summary.originalBalanceMinor)} from " +
                            "${summary.sourceSet.joinToString(", ") { it.name }}."
                    } else {
                        "Baseline set from ${result.baselines.size} connected source(s)."
                    }
                }
                is BaselineResult.Refused -> baselineMessage.value = result.reason
            }
        }
    }

    fun clearBaselineMessage() {
        baselineMessage.value = null
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
