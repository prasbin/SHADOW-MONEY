package com.prasbin.shadowmoney.presentation.screen.connections

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.connections.ActualMoney
import com.prasbin.shadowmoney.data.connections.ActualMoneyView
import com.prasbin.shadowmoney.data.connections.BalanceBaseline
import com.prasbin.shadowmoney.data.connections.BaselineCalculator
import com.prasbin.shadowmoney.data.connections.BaselineResult
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConnectionsUiState(
    val providers: List<ProviderAvailability> = emptyList(),
    val connections: List<FinancialConnection> = emptyList(),
    val baselines: List<BalanceBaseline> = emptyList(),
    val actualMoney: ActualMoneyView? = null,
    val discrepancy: DiscrepancyResult? = null,
    val baselineMessage: String? = null
)

/**
 * Drives the Connections screen from the real connection architecture: honest
 * provider availability, stored connection state, the unified actual-money view,
 * baseline establishment (refuses without connected sources), and the pure
 * discrepancy engine. Never fabricates a connection or a balance.
 */
class ConnectionsViewModel(
    private val repository: ConnectionRepository,
    private val coordinator: ConnectionSyncCoordinator,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val baselineMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ConnectionsUiState> = combine(
        repository.observeConnections(),
        repository.observeBaselines(),
        baselineMessage
    ) { connections, baselines, message ->
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
        val latestVerified = if (sources.isEmpty()) null else sources.sumOf { it.balanceMinor }

        ConnectionsUiState(
            providers = ProviderCatalog.all(),
            connections = connections,
            baselines = baselines,
            actualMoney = ActualMoney.unifiedActualMoney(sources, now),
            discrepancy = DiscrepancyEngine.evaluate(
                DiscrepancyInput(
                    connectedSourceCount = connectedCount,
                    status = worstStatus,
                    baselineMinor = baselines.firstOrNull()?.baselineMinor,
                    recordedNetChangeMinor = 0L,
                    latestVerifiedBalanceMinor = latestVerified
                )
            ),
            baselineMessage = message
        )
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionsUiState())

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
     * consumer connections available this honestly refuses and reports why.
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
                    baselineMessage.value =
                        "Baseline set from ${result.baselines.size} connected source(s)."
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
