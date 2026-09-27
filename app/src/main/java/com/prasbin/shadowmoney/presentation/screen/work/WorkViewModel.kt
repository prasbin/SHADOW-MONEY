package com.prasbin.shadowmoney.presentation.screen.work

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.DeadlineStatus
import com.prasbin.shadowmoney.data.WorkItemView
import com.prasbin.shadowmoney.data.WorkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WorkUiState {
    data object Loading : WorkUiState
    data object Empty : WorkUiState

    data class Content(val items: List<WorkItemView>) : WorkUiState

    data class Error(val message: String) : WorkUiState
}

class WorkViewModel(
    private val repository: WorkRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val statusFilter = MutableStateFlow<Int?>(null)
    private val searchQuery = MutableStateFlow("")

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<WorkUiState> = combine(
        statusFilter,
        searchQuery,
        repository.observeChanges()
    ) { filter, search, _ -> filter to search }
        .flatMapLatest { (filter, search) -> repository.observeWorkItems(filter, search) }
        .map { items ->
            runCatching { repository.loadViews(items) }
                .fold(
                    onSuccess = { views ->
                        if (views.isEmpty()) WorkUiState.Empty else WorkUiState.Content(views)
                    },
                    onFailure = { error ->
                        WorkUiState.Error(error.message ?: "Failed to load work items")
                    }
                )
        }
        .catch { error ->
            emit(WorkUiState.Error(error.message ?: "Failed to load work items"))
        }
        .onStart { emit(WorkUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = WorkUiState.Loading
        )

    fun setStatusFilter(status: Int?) {
        statusFilter.value = status
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun create(
        title: String,
        description: String,
        expectedAmountMinor: Long,
        deadlineTimestamp: Long,
        client: String
    ) {
        runCrud { repository.create(title, description, expectedAmountMinor, deadlineTimestamp, client) }
    }

    fun update(
        workItem: com.prasbin.shadowmoney.data.model.WorkItem,
        title: String,
        description: String,
        expectedAmountMinor: Long,
        deadlineTimestamp: Long,
        client: String
    ) {
        runCrud { repository.update(workItem, title, description, expectedAmountMinor, deadlineTimestamp, client) }
    }

    fun archive(workItemId: Long) {
        runCrud { repository.archive(workItemId) }
    }

    fun delete(workItem: com.prasbin.shadowmoney.data.model.WorkItem) {
        runCrud { repository.delete(workItem) }
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
