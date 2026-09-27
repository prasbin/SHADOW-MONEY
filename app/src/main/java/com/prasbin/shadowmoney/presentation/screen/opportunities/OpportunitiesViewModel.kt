package com.prasbin.shadowmoney.presentation.screen.opportunities

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.OpportunityRepository
import com.prasbin.shadowmoney.data.OpportunitySummaryData
import com.prasbin.shadowmoney.data.OpportunityView
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

sealed interface OpportunitiesUiState {
    data object Loading : OpportunitiesUiState
    data object Empty : OpportunitiesUiState

    data class Content(
        val opportunities: List<OpportunityView>,
        val summary: OpportunitySummaryData
    ) : OpportunitiesUiState

    data class Error(val message: String) : OpportunitiesUiState
}

class OpportunitiesViewModel(
    private val repository: OpportunityRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val statusFilter = MutableStateFlow<Int?>(null)
    private val typeFilter = MutableStateFlow<Int?>(null)
    private val sourceFilter = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<OpportunitiesUiState> = combine(
        statusFilter,
        typeFilter,
        sourceFilter,
        searchQuery,
        repository.observeChanges()
    ) { status, type, source, search, _ -> FilterParams(status, type, source, search) }
        .flatMapLatest { params ->
            repository.observeOpportunities(params.status, params.type, params.source, params.search)
        }
        .map { opportunities ->
            runCatching {
                val views = repository.loadViews(opportunities)
                val summary = repository.loadSummary()
                if (views.isEmpty()) {
                    OpportunitiesUiState.Empty
                } else {
                    OpportunitiesUiState.Content(views, summary)
                }
            }
                .fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        OpportunitiesUiState.Error(error.message ?: "Failed to load opportunities")
                    }
                )
        }
        .catch { error ->
            emit(OpportunitiesUiState.Error(error.message ?: "Failed to load opportunities"))
        }
        .onStart { emit(OpportunitiesUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = OpportunitiesUiState.Loading
        )

    private data class FilterParams(
        val status: Int?,
        val type: Int?,
        val source: String?,
        val search: String?
    )

    fun setStatusFilter(status: Int?) {
        statusFilter.value = status
    }

    fun setTypeFilter(type: Int?) {
        typeFilter.value = type
    }

    fun setSourceFilter(source: String?) {
        sourceFilter.value = source
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun create(
        title: String,
        description: String,
        type: Int,
        source: String,
        sourceUrl: String,
        expectedAmountMinor: Long?,
        deadlineTimestamp: Long,
        client: String
    ) {
        runCrud {
            repository.create(
                title, description, type, source, sourceUrl,
                expectedAmountMinor, deadlineTimestamp, client
            )
        }
    }

    fun update(
        opportunity: com.prasbin.shadowmoney.data.model.Opportunity,
        title: String,
        description: String,
        type: Int,
        source: String,
        sourceUrl: String,
        expectedAmountMinor: Long?,
        status: Int,
        deadlineTimestamp: Long,
        client: String
    ) {
        runCrud {
            repository.update(
                opportunity, title, description, type, source, sourceUrl,
                expectedAmountMinor, status, deadlineTimestamp, client
            )
        }
    }

    fun archive(opportunityId: Long) {
        runCrud { repository.archive(opportunityId) }
    }

    fun delete(opportunity: com.prasbin.shadowmoney.data.model.Opportunity) {
        runCrud { repository.delete(opportunity) }
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
