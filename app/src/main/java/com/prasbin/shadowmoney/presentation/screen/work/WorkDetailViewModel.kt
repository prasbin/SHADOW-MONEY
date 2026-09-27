package com.prasbin.shadowmoney.presentation.screen.work

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.WorkDetailData
import com.prasbin.shadowmoney.data.WorkRepository
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
import kotlinx.coroutines.launch

sealed interface WorkDetailUiState {
    data object Loading : WorkDetailUiState
    data object NotFound : WorkDetailUiState

    data class Content(val detail: WorkDetailData) : WorkDetailUiState

    data class Error(val message: String) : WorkDetailUiState
}

class WorkDetailViewModel(
    private val workItemId: Long,
    private val repository: WorkRepository
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val uiState: StateFlow<WorkDetailUiState> = repository.observeWorkDetail(workItemId)
        .map { detail ->
            if (detail == null) {
                WorkDetailUiState.NotFound
            } else {
                WorkDetailUiState.Content(detail)
            }
        }
        .catch { error ->
            emit(WorkDetailUiState.Error(error.message ?: "Failed to load work item"))
        }
        .onStart { emit(WorkDetailUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = WorkDetailUiState.Loading
        )

    fun linkTransaction(transactionId: Long) {
        runCrud { repository.linkTransaction(transactionId, workItemId) }
    }

    fun unlinkTransaction(transactionId: Long) {
        runCrud { repository.linkTransaction(transactionId, null) }
    }

    private fun runCrud(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
