package com.prasbin.shadowmoney.presentation.screen.money

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.AccountDao
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.TransactionDao
import com.prasbin.shadowmoney.data.imports.AmountParseResult
import com.prasbin.shadowmoney.data.imports.ImportAmount
import com.prasbin.shadowmoney.data.model.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MoneyAccountView(
    val account: Account,
    val balanceMinor: Long
)

data class MoneyUiState(
    val accounts: List<MoneyAccountView> = emptyList(),
    val isLoading: Boolean = true
)

class MoneyViewModel(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(MoneyUiState())
    val uiState: StateFlow<MoneyUiState> = _uiState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        scope.launch {
            combine(
                accountDao.getAll(),
                transactionDao.observeTransactionCount()
            ) { accounts, _ -> accounts }
                .collect { accounts ->
                    val views = accounts.map { account ->
                        MoneyAccountView(
                            account = account,
                            balanceMinor = Money.balanceMinor(
                                account.openingBalanceMinor,
                                transactionDao.getTotalIncomeMinor(account.id),
                                transactionDao.getTotalOutflowMinor(account.id)
                            )
                        )
                    }
                    _uiState.value = MoneyUiState(accounts = views, isLoading = false)
                }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun createAccount(name: String, type: Int, openingText: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Account name is required"
            return false
        }
        val opening = when (val parsed = parseOpening(openingText)) {
            is OpeningParse.Invalid -> {
                _errorMessage.value = parsed.reason
                return false
            }
            is OpeningParse.Ok -> parsed.minor
        }
        scope.launch {
            accountDao.insert(
                Account(
                    name = trimmed,
                    type = type,
                    openingBalanceMinor = opening,
                    createdTimestamp = clock()
                )
            )
        }
        _errorMessage.value = null
        return true
    }

    fun updateAccount(id: Long, name: String, type: Int, openingText: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _errorMessage.value = "Account name is required"
            return false
        }
        val opening = when (val parsed = parseOpening(openingText)) {
            is OpeningParse.Invalid -> {
                _errorMessage.value = parsed.reason
                return false
            }
            is OpeningParse.Ok -> parsed.minor
        }
        scope.launch {
            val existing = accountDao.getById(id) ?: return@launch
            accountDao.update(
                existing.copy(
                    name = trimmed,
                    type = type,
                    openingBalanceMinor = opening
                )
            )
        }
        _errorMessage.value = null
        return true
    }

    fun setArchived(id: Long, archived: Boolean) {
        scope.launch {
            val existing = accountDao.getById(id) ?: return@launch
            accountDao.update(existing.copy(isActive = !archived))
        }
        _errorMessage.value = null
    }

    private fun parseOpening(text: String): OpeningParse {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return OpeningParse.Ok(0L)
        return when (val result = ImportAmount.parse(trimmed)) {
            is AmountParseResult.Invalid -> OpeningParse.Invalid(result.reason)
            is AmountParseResult.Ok -> OpeningParse.Ok(result.minorUnits)
        }
    }
}

private sealed interface OpeningParse {
    data class Ok(val minor: Long) : OpeningParse
    data class Invalid(val reason: String) : OpeningParse
}
