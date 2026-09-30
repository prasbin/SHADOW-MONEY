package com.prasbin.shadowmoney.presentation.screen.dashboard

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.AccountBalanceView
import com.prasbin.shadowmoney.data.BudgetView
import com.prasbin.shadowmoney.data.CategorySpendView
import com.prasbin.shadowmoney.data.DashboardRepository
import com.prasbin.shadowmoney.data.GoalProgressView
import com.prasbin.shadowmoney.data.RecentTransactionView
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

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data object Empty : DashboardUiState

    data class Content(
        val totalBalanceMinor: Long,
        val totalIncomeMinor: Long,
        val totalOutflowMinor: Long,
        val accounts: List<AccountBalanceView>,
        val recentTransactions: List<RecentTransactionView>,
        val categoryOutflow: List<CategorySpendView>,
        val goals: List<GoalProgressView>,
        val overallBudget: BudgetView?
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(
    private val repository: DashboardRepository
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val uiState: StateFlow<DashboardUiState> = repository.observeSnapshot()
        .map { snapshot ->
            runCatching { repository.loadDashboardData(snapshot) }
                .fold(
                    onSuccess = { data ->
                        if (data.isEmpty) {
                            DashboardUiState.Empty
                        } else {
                            DashboardUiState.Content(
                                totalBalanceMinor = data.totalBalanceMinor,
                                totalIncomeMinor = data.totalIncomeMinor,
                                totalOutflowMinor = data.totalOutflowMinor,
                                accounts = data.accounts,
                                recentTransactions = data.recentTransactions,
                                categoryOutflow = data.categoryOutflow,
                                goals = data.goals,
                                overallBudget = data.overallBudget
                            )
                        }
                    },
                    onFailure = { error ->
                        DashboardUiState.Error(
                            error.message ?: "Failed to load dashboard data"
                        )
                    }
                )
        }
        .catch { error ->
            emit(
                DashboardUiState.Error(
                    error.message ?: "Failed to load dashboard data"
                )
            )
        }
        .onStart { emit(DashboardUiState.Loading) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = DashboardUiState.Loading
        )

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
