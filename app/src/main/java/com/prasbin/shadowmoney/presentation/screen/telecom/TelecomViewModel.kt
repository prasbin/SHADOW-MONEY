package com.prasbin.shadowmoney.presentation.screen.telecom

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.TelecomRepository
import com.prasbin.shadowmoney.data.TelecomSummary
import com.prasbin.shadowmoney.data.UpcomingRenewal
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TelecomUiState {
    data object Loading : TelecomUiState
    data object Empty : TelecomUiState

    data class Content(
        val summary: TelecomSummary,
        val sims: List<TelecomSim>,
        val packages: List<TelecomPackage>,
        val subscriptions: List<TelecomSubscription>
    ) : TelecomUiState

    data class Error(val message: String) : TelecomUiState
}

class TelecomViewModel(
    private val repository: TelecomRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<TelecomUiState> = combine(
        repository.observeSims(),
        repository.observePackages(),
        repository.observeSubscriptions(),
        repository.observeChanges()
    ) { sims, packages, subscriptions, _ ->
        runCatching { repository.loadSummaryFrom(sims, packages, subscriptions) }
            .fold(
                onSuccess = { summary ->
                    if (sims.isEmpty() && packages.isEmpty() && subscriptions.isEmpty()) {
                        TelecomUiState.Empty
                    } else {
                        TelecomUiState.Content(summary, sims, packages, subscriptions)
                    }
                },
                onFailure = { error ->
                    TelecomUiState.Error(error.message ?: "Failed to load telecom data")
                }
            )
    }
        .catch { error ->
            emit(TelecomUiState.Error(error.message ?: "Failed to load telecom data"))
        }
        .onStart { emit(TelecomUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = TelecomUiState.Loading
        )

    fun createSim(label: String, carrier: String, phoneNumber: String, notes: String) {
        runCrud { repository.createSim(label, carrier, phoneNumber, notes) }
    }

    fun updateSim(sim: TelecomSim, label: String, carrier: String, phoneNumber: String, notes: String) {
        runCrud { repository.updateSim(sim, label, carrier, phoneNumber, notes) }
    }

    fun archiveSim(sim: TelecomSim) {
        runCrud { repository.archiveSim(sim) }
    }

    fun activateSim(sim: TelecomSim) {
        runCrud { repository.activateSim(sim) }
    }

    fun createPackage(
        name: String,
        carrier: String,
        category: String,
        priceMinor: Long,
        period: Int,
        notes: String
    ) {
        runCrud { repository.createPackage(name, carrier, category, priceMinor, period, notes) }
    }

    fun updatePackage(
        pkg: TelecomPackage,
        name: String,
        carrier: String,
        category: String,
        priceMinor: Long,
        period: Int,
        notes: String
    ) {
        runCrud { repository.updatePackage(pkg, name, carrier, category, priceMinor, period, notes) }
    }

    fun archivePackage(pkg: TelecomPackage) {
        runCrud { repository.archivePackage(pkg) }
    }

    fun activatePackage(pkg: TelecomPackage) {
        runCrud { repository.activatePackage(pkg) }
    }

    fun createSubscription(
        simId: Long,
        packageId: Long,
        startTimestamp: Long,
        renewalTimestamp: Long,
        monthlyCostMinor: Long
    ) {
        runCrud { repository.createSubscription(simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor) }
    }

    fun updateSubscription(
        subscription: TelecomSubscription,
        simId: Long,
        packageId: Long,
        startTimestamp: Long,
        renewalTimestamp: Long,
        monthlyCostMinor: Long
    ) {
        runCrud {
            repository.updateSubscription(
                subscription, simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor
            )
        }
    }

    fun deactivateSubscription(subscription: TelecomSubscription) {
        runCrud { repository.deactivateSubscription(subscription) }
    }

    fun activateSubscription(subscription: TelecomSubscription) {
        runCrud { repository.activateSubscription(subscription) }
    }

    fun deleteSubscription(subscription: TelecomSubscription) {
        runCrud { repository.deleteSubscription(subscription) }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun runCrud(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { error ->
                    _errorMessage.value = error.message ?: "Operation failed"
                }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
