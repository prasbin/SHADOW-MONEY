package com.prasbin.shadowmoney.presentation.screen.csvimport

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.imports.CsvDocument
import com.prasbin.shadowmoney.data.imports.CsvParser
import com.prasbin.shadowmoney.data.imports.CsvStreamReader
import com.prasbin.shadowmoney.data.imports.ImportEngine
import com.prasbin.shadowmoney.data.imports.ImportFingerprint
import com.prasbin.shadowmoney.data.imports.ImportPreview
import com.prasbin.shadowmoney.data.imports.ImportReference
import com.prasbin.shadowmoney.data.imports.ImportRepository
import com.prasbin.shadowmoney.data.imports.ImportRowState
import com.prasbin.shadowmoney.data.imports.PreviewOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Explicit import state machine. No database write ever happens before the
 * user triggers [confirmImport] with an explicit selection.
 */
sealed interface ImportUiState {
    /** Step 1: choose a CSV source (paste or SAF file). */
    data object Source : ImportUiState

    /** Parsing / reference loading in progress. */
    data object Loading : ImportUiState

    /** Read-only preview: counts, rows, duplicates, unmatched references. */
    data class PreviewReady(
        val preview: ImportPreview,
        val selections: Map<Int, Boolean>,
        val accounts: List<com.prasbin.shadowmoney.data.model.Account>,
        val categories: List<com.prasbin.shadowmoney.data.model.Category>,
        val accountMappings: Map<String, Long>,
        val categoryMappings: Map<String, Long>,
        val accountLabels: Map<String, String>,
        val categoryLabels: Map<String, String>
    ) : ImportUiState {
        val selectedImportableCount: Int
            get() = preview.rows.count {
                selections[it.rowNumber] == true && it.isImportable()
            }
    }

    /** Input rejected before a preview (missing/unreadable header, missing columns). */
    data class PreviewRejected(val reason: String) : ImportUiState

    /** Source parsed successfully but contains no data rows. */
    data object EmptyInput : ImportUiState

    /** Final confirmed write in progress. */
    data object Importing : ImportUiState

    /** Batch completed. */
    data class Success(
        val totalRows: Int,
        val importedCount: Int,
        val skippedCount: Int,
        val invalidCount: Int
    ) : ImportUiState

    data class Error(val message: String) : ImportUiState
}

class ImportTransactionsViewModel(
    private val repository: ImportRepository,
    private val maxInputBytes: Int = CsvStreamReader.MAX_IMPORT_BYTES
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Source)
    val state: StateFlow<ImportUiState> = _state

    private var document: CsvDocument? = null
    private var reference: ImportReference? = null
    private var accountMappings: Map<String, Long> = emptyMap()
    private var categoryMappings: Map<String, Long> = emptyMap()
    private var accountLabels: Map<String, String> = emptyMap()
    private var categoryLabels: Map<String, String> = emptyMap()

    fun submitCsv(text: String) {
        if (text.isBlank()) {
            _state.value = ImportUiState.EmptyInput
            return
        }
        if (text.length > maxInputBytes ||
            text.toByteArray(Charsets.UTF_8).size > maxInputBytes
        ) {
            _state.value = ImportUiState.Error(
                "Import input exceeds the ${maxInputBytes / (1024 * 1024)} MB limit"
            )
            return
        }
        _state.value = ImportUiState.Loading
        scope.launch {
            runCatching {
                val parsed = CsvParser.parse(text)
                val referenceData = repository.loadReference()
                val outcome = ImportEngine.buildPreview(parsed, referenceData)
                ImportWork(parsed, referenceData, outcome)
            }.fold(
                onSuccess = { work ->
                    document = work.parsed
                    reference = work.reference
                    accountMappings = emptyMap()
                    categoryMappings = emptyMap()
                    accountLabels = emptyMap()
                    categoryLabels = emptyMap()
                    _state.value = when (val outcome = work.outcome) {
                        is PreviewOutcome.Ready -> {
                            recordLabels(outcome.preview)
                            buildPreviewState(outcome.preview)
                        }
                        is PreviewOutcome.Empty -> ImportUiState.EmptyInput
                        is PreviewOutcome.Rejected ->
                            ImportUiState.PreviewRejected(outcome.reason)
                    }
                },
                onFailure = { error ->
                    _state.value = ImportUiState.Error(
                        error.message ?: "Could not parse the CSV input"
                    )
                }
            )
        }
    }

    fun reportSourceError(message: String) {
        _state.value = ImportUiState.Error(message)
    }

    fun setAccountMapping(displayName: String, accountId: Long?) {
        val key = ImportFingerprint.normalizeText(displayName)
        accountLabels = accountLabels + (key to displayName)
        accountMappings = if (accountId == null) accountMappings - key
        else accountMappings + (key to accountId)
        recomputePreview()
    }

    fun setCategoryMapping(displayName: String, categoryId: Long?) {
        val key = ImportFingerprint.normalizeText(displayName)
        categoryLabels = categoryLabels + (key to displayName)
        categoryMappings = if (categoryId == null) categoryMappings - key
        else categoryMappings + (key to categoryId)
        recomputePreview()
    }

    fun toggleRow(rowNumber: Int, selected: Boolean) {
        val current = _state.value as? ImportUiState.PreviewReady ?: return
        _state.value = current.copy(
            selections = current.selections + (rowNumber to selected)
        )
    }

    fun setDuplicateDecision(rowNumber: Int, importRow: Boolean) {
        val current = _state.value as? ImportUiState.PreviewReady ?: return
        _state.value = current.copy(
            selections = current.selections + (rowNumber to importRow)
        )
    }

    fun confirmImport() {
        val current = _state.value as? ImportUiState.PreviewReady ?: return
        val selected = current.preview.rows.filter {
            current.selections[it.rowNumber] == true && it.isImportable()
        }
        if (selected.isEmpty()) {
            _state.value = ImportUiState.Error("No transactions selected for import")
            return
        }
        val totalRows = current.preview.totalRows
        val invalidCount = current.preview.invalidRows
        val validCount = current.preview.validRows

        _state.value = ImportUiState.Importing
        scope.launch {
            runCatching { repository.importSelected(selected) }
                .fold(
                    onSuccess = { importedCount ->
                        _state.value = ImportUiState.Success(
                            totalRows = totalRows,
                            importedCount = importedCount,
                            skippedCount = validCount - importedCount,
                            invalidCount = invalidCount
                        )
                    },
                    onFailure = { error ->
                        _state.value = ImportUiState.Error(
                            "Import failed: nothing was written. " +
                                (error.message ?: "Unknown database error")
                        )
                    }
                )
        }
    }

    fun startOver() {
        document = null
        reference = null
        accountMappings = emptyMap()
        categoryMappings = emptyMap()
        accountLabels = emptyMap()
        categoryLabels = emptyMap()
        _state.value = ImportUiState.Source
    }

    fun clearError() {
        if (_state.value is ImportUiState.Error) {
            startOver()
        }
    }

    private fun recomputePreview() {
        val parsed = document ?: return
        val referenceData = reference ?: return
        runCatching {
            ImportEngine.buildPreview(parsed, referenceData, accountMappings, categoryMappings)
        }.fold(
            onSuccess = { outcome ->
                _state.value = when (outcome) {
                    is PreviewOutcome.Ready -> {
                        recordLabels(outcome.preview)
                        buildPreviewState(outcome.preview)
                    }
                    is PreviewOutcome.Empty -> ImportUiState.EmptyInput
                    is PreviewOutcome.Rejected -> ImportUiState.PreviewRejected(outcome.reason)
                }
            },
            onFailure = { error ->
                _state.value = ImportUiState.Error(
                    error.message ?: "Could not rebuild the preview"
                )
            }
        )
    }

    private fun recordLabels(preview: ImportPreview) {
        preview.unmatchedAccountNames.forEach { name ->
            val key = ImportFingerprint.normalizeText(name)
            if (key !in accountLabels) accountLabels = accountLabels + (key to name)
        }
        preview.unmatchedCategoryNames.forEach { name ->
            val key = ImportFingerprint.normalizeText(name)
            if (key !in categoryLabels) categoryLabels = categoryLabels + (key to name)
        }
    }

    private fun buildPreviewState(preview: ImportPreview): ImportUiState =
        ImportUiState.PreviewReady(
            preview = preview,
            selections = defaultSelections(preview),
            accounts = reference?.accounts ?: emptyList(),
            categories = reference?.categories ?: emptyList(),
            accountMappings = accountMappings,
            categoryMappings = categoryMappings,
            accountLabels = accountLabels,
            categoryLabels = categoryLabels
        )

    private fun defaultSelections(preview: ImportPreview): Map<Int, Boolean> =
        preview.rows.associate { row ->
            row.rowNumber to (row.isImportable() && row.state == ImportRowState.NEW)
        }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    private data class ImportWork(
        val parsed: CsvDocument,
        val reference: ImportReference,
        val outcome: PreviewOutcome
    )
}
