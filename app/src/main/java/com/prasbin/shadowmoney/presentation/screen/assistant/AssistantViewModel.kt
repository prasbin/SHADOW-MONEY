package com.prasbin.shadowmoney.presentation.screen.assistant

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.assistant.AssistantEngine
import com.prasbin.shadowmoney.assistant.AssistantResponse
import com.prasbin.shadowmoney.assistant.AssistantSection
import com.prasbin.shadowmoney.assistant.ASSISTANT_READ_ERROR_TEXT
import com.prasbin.shadowmoney.assistant.ClassifiedQuestion
import com.prasbin.shadowmoney.assistant.IntentClassifier
import com.prasbin.shadowmoney.assistant.requiresData
import com.prasbin.shadowmoney.data.AssistantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Session-local conversation holder. History is kept only while this
 * ViewModel lives — it is never persisted, never written to the database,
 * and never becomes financial truth.
 */
data class AssistantChatMessage(
    val isUser: Boolean,
    val text: String = "",
    val sections: List<AssistantSection> = emptyList(),
    val source: String? = null
)

data class AssistantUiState(
    val messages: List<AssistantChatMessage> = emptyList(),
    val isBusy: Boolean = false
)

class AssistantViewModel(
    private val repository: AssistantRepository,
    private val engine: AssistantEngine = AssistantEngine()
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state

    fun submit(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || _state.value.isBusy) return
        val question: ClassifiedQuestion = IntentClassifier.classify(text)
        _state.update { current ->
            current.copy(
                messages = current.messages + AssistantChatMessage(isUser = true, text = text),
                isBusy = true
            )
        }
        scope.launch {
            val response = runCatching {
                val data = if (question.intent.requiresData) repository.load(question) else null
                engine.answer(question, data)
            }.getOrElse {
                AssistantResponse(listOf(AssistantSection(null, ASSISTANT_READ_ERROR_TEXT)))
            }
            _state.update { current ->
                val messages = (
                    current.messages + AssistantChatMessage(
                        isUser = false,
                        sections = response.sections,
                        source = response.source
                    )
                    ).takeLast(MAX_MESSAGES)
                current.copy(messages = messages, isBusy = false)
            }
        }
    }

    fun clearHistory() {
        _state.value = AssistantUiState()
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    companion object {
        const val MAX_MESSAGES = 40
    }
}
