package com.prasbin.shadowmoney.presentation.screen.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prasbin.shadowmoney.data.SecretTargetStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SecretTargetViewModel(
    private val store: SecretTargetStore
) : ViewModel() {

    val target: StateFlow<Long?> = store.observeTarget()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    fun setTarget(amountMinor: Long) {
        viewModelScope.launch { store.setTarget(amountMinor) }
    }

    fun clear() {
        viewModelScope.launch { store.clear() }
    }
}
