package com.prasbin.shadowmoney.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.IntelligenceRepository
import com.prasbin.shadowmoney.intelligence.IntelligenceReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface IntelligenceUiState {
    data object Loading : IntelligenceUiState

    data class Content(val report: IntelligenceReport) : IntelligenceUiState

    data class Error(val message: String) : IntelligenceUiState
}

class IntelligenceViewModel(
    private val repository: IntelligenceRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val uiState: StateFlow<IntelligenceUiState> = repository.observeChanges()
        .map {
            runCatching { repository.loadReport(clock()) }
                .fold(
                    onSuccess = { IntelligenceUiState.Content(it) },
                    onFailure = { error ->
                        IntelligenceUiState.Error(
                            error.message ?: "Failed to load intelligence report"
                        )
                    }
                )
        }
        .catch { error ->
            emit(
                IntelligenceUiState.Error(
                    error.message ?: "Failed to load intelligence report"
                )
            )
        }
        .onStart { emit(IntelligenceUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = IntelligenceUiState.Loading
        )

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
