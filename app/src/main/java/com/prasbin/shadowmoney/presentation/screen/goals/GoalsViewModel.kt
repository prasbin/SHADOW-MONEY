package com.prasbin.shadowmoney.presentation.screen.goals

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.GoalRepository
import com.prasbin.shadowmoney.data.GoalView
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

sealed interface GoalsUiState {
    data object Loading : GoalsUiState
    data object Empty : GoalsUiState

    data class Content(val goals: List<GoalView>) : GoalsUiState

    data class Error(val message: String) : GoalsUiState
}

class GoalsViewModel(
    private val repository: GoalRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<GoalsUiState> = combine(
        repository.observeAll(),
        repository.observeTransactionTrigger()
    ) { goals, _ -> goals }
        .map { goals ->
            runCatching { repository.loadGoalViews(goals) }
                .fold(
                    onSuccess = { views ->
                        if (views.isEmpty()) {
                            GoalsUiState.Empty
                        } else {
                            GoalsUiState.Content(views)
                        }
                    },
                    onFailure = { error ->
                        GoalsUiState.Error(error.message ?: "Failed to load goals")
                    }
                )
        }
        .catch { error ->
            emit(GoalsUiState.Error(error.message ?: "Failed to load goals"))
        }
        .onStart { emit(GoalsUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = GoalsUiState.Loading
        )

    fun create(name: String, targetAmountMinor: Long, accountId: Long?, deadlineTimestamp: Long) {
        runCrud {
            repository.create(name, targetAmountMinor, accountId, deadlineTimestamp)
        }
    }

    fun update(
        goal: com.prasbin.shadowmoney.data.model.Goal,
        name: String,
        targetAmountMinor: Long,
        accountId: Long?,
        deadlineTimestamp: Long
    ) {
        runCrud {
            repository.update(goal, name, targetAmountMinor, accountId, deadlineTimestamp)
        }
    }

    fun archive(goalId: Long) {
        runCrud { repository.archive(goalId) }
    }

    fun markComplete(goalId: Long) {
        runCrud { repository.markComplete(goalId) }
    }

    fun delete(goal: com.prasbin.shadowmoney.data.model.Goal) {
        runCrud { repository.delete(goal) }
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
