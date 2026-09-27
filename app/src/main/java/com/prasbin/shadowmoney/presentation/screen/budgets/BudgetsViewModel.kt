package com.prasbin.shadowmoney.presentation.screen.budgets

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.BudgetMonthData
import com.prasbin.shadowmoney.data.BudgetRepository
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

sealed interface BudgetsUiState {
    data object Loading : BudgetsUiState
    data object Empty : BudgetsUiState

    data class Content(val month: BudgetMonthData) : BudgetsUiState

    data class Error(val message: String) : BudgetsUiState
}

class BudgetsViewModel(
    private val repository: BudgetRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val selectedMonth = MutableStateFlow(BudgetCalendar.currentMonthKey(clock()))

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val uiState: StateFlow<BudgetsUiState> = combine(
        selectedMonth,
        repository.observeChanges()
    ) { month, _ -> month }
        .map { month ->
            runCatching { repository.loadMonthData(month) }
                .fold(
                    onSuccess = { data ->
                        if (data.isEmpty) BudgetsUiState.Empty else BudgetsUiState.Content(data)
                    },
                    onFailure = { error ->
                        BudgetsUiState.Error(error.message ?: "Failed to load budgets")
                    }
                )
        }
        .catch { error ->
            emit(BudgetsUiState.Error(error.message ?: "Failed to load budgets"))
        }
        .onStart { emit(BudgetsUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = BudgetsUiState.Loading
        )

    fun selectMonth(monthKey: String) {
        selectedMonth.value = monthKey
    }

    fun selectPreviousMonth() {
        selectedMonth.value = BudgetCalendar.shiftMonth(selectedMonth.value, -1)
    }

    fun selectNextMonth() {
        selectedMonth.value = BudgetCalendar.shiftMonth(selectedMonth.value, 1)
    }

    fun createOverallBudget(monthKey: String, amountMinor: Long) {
        runCrud { repository.createOverallBudget(monthKey, amountMinor) }
    }

    fun createCategoryBudget(monthKey: String, categoryId: Long, amountMinor: Long) {
        runCrud { repository.createCategoryBudget(monthKey, categoryId, amountMinor) }
    }

    fun updateBudgetAmount(budget: com.prasbin.shadowmoney.data.model.Budget, newAmountMinor: Long) {
        runCrud { repository.updateBudgetAmount(budget, newAmountMinor) }
    }

    fun deleteBudget(budget: com.prasbin.shadowmoney.data.model.Budget) {
        runCrud { repository.deleteBudget(budget) }
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
