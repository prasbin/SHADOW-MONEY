package com.prasbin.shadowmoney.presentation.screen.transactions

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.AccountDao
import com.prasbin.shadowmoney.data.CategoryDao
import com.prasbin.shadowmoney.data.TransactionDao
import com.prasbin.shadowmoney.data.WorkItemDao
import com.prasbin.shadowmoney.data.imports.AmountParseResult
import com.prasbin.shadowmoney.data.imports.DateParseResult
import com.prasbin.shadowmoney.data.imports.ImportAmount
import com.prasbin.shadowmoney.data.imports.ImportDate
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_BOTH
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WorkItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TransactionListView(
    val transaction: Transaction,
    val accountName: String,
    val categoryName: String
)

data class TransactionsUiState(
    val recent: List<TransactionListView> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val workItems: List<WorkItem> = emptyList(),
    val isLoading: Boolean = true
)

class TransactionsViewModel(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val workItemDao: WorkItemDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    val defaultDateText: String =
        LocalDate.now(ZoneId.of("Asia/Kathmandu")).toString()

    init {
        scope.launch {
            combine(
                transactionDao.getRecent(RECENT_LIMIT),
                accountDao.getAll(),
                categoryDao.getAll(),
                workItemDao.observeWorkItems(statusFilter = null, search = null)
            ) { recent, allAccounts, allCategories, allWorkItems ->
                val accountNames = allAccounts.associate { it.id to it.name }
                val categoryNames = allCategories.associate { it.id to it.name }
                TransactionsUiState(
                    recent = recent.map { transaction ->
                        TransactionListView(
                            transaction = transaction,
                            accountName = accountNames[transaction.accountId] ?: "Unknown account",
                            categoryName = transaction.categoryId
                                ?.let { categoryNames[it] }
                                ?: "Uncategorized"
                        )
                    },
                    accounts = allAccounts.filter { it.isActive },
                    categories = allCategories.filter { it.isActive },
                    workItems = allWorkItems.filter { it.status == WORK_STATUS_ACTIVE },
                    isLoading = false
                )
            }.collect { _uiState.value = it }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun addTransaction(
        direction: Int,
        amountText: String,
        dateText: String,
        accountId: Long,
        categoryId: Long?,
        workItemId: Long?,
        note: String
    ): Boolean {
        val amountMinor = when (val amount = ImportAmount.parse(amountText)) {
            is AmountParseResult.Invalid -> {
                _errorMessage.value = amount.reason
                return false
            }
            is AmountParseResult.Ok -> amount.minorUnits
        }
        if (amountMinor <= 0L) {
            _errorMessage.value = "Amount must be greater than zero"
            return false
        }
        val timestamp = when (val date = ImportDate.parse(dateText)) {
            is DateParseResult.Invalid -> {
                _errorMessage.value = date.reason
                return false
            }
            is DateParseResult.Ok -> date.epochMillis
        }
        val state = _uiState.value
        if (state.accounts.none { it.id == accountId }) {
            _errorMessage.value = "Select an existing active account"
            return false
        }
        var resolvedCategoryId: Long? = null
        if (categoryId != null) {
            val category = state.categories.firstOrNull { it.id == categoryId }
            if (category == null) {
                _errorMessage.value = "Selected category is not available"
                return false
            }
            if (category.direction != CATEGORY_DIRECTION_BOTH && category.direction != direction) {
                _errorMessage.value = "Category does not match the selected direction"
                return false
            }
            resolvedCategoryId = category.id
        }
        var resolvedWorkItemId: Long? = null
        if (workItemId != null) {
            if (state.workItems.none { it.id == workItemId }) {
                _errorMessage.value = "Selected work item is not available"
                return false
            }
            resolvedWorkItemId = workItemId
        }
        scope.launch {
            transactionDao.insert(
                Transaction(
                    accountId = accountId,
                    categoryId = resolvedCategoryId,
                    workItemId = resolvedWorkItemId,
                    amountMinor = amountMinor,
                    direction = direction,
                    transactionTimestamp = timestamp,
                    note = note.trim(),
                    createdTimestamp = clock(),
                    source = "",
                    externalRef = null
                )
            )
        }
        _errorMessage.value = null
        return true
    }

    fun isIncome(direction: Int): Boolean = direction == TRANSACTION_DIRECTION_INCOME

    companion object {
        const val RECENT_LIMIT = 100
    }
}
