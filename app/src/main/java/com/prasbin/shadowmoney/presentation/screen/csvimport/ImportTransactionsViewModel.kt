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
import com.prasbin.shadowmoney.data.imports.StatementImportContext
import com.prasbin.shadowmoney.data.statements.StatementDetection
import com.prasbin.shadowmoney.data.statements.StatementFormat
import com.prasbin.shadowmoney.data.statements.StatementHash
import com.prasbin.shadowmoney.data.statements.StatementInput
import com.prasbin.shadowmoney.data.statements.StatementParseOutcome
import com.prasbin.shadowmoney.data.statements.StatementParserRegistry
import com.prasbin.shadowmoney.data.statements.StatementSource
import com.prasbin.shadowmoney.data.statements.StatementSummary
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
 *
 * Real statement ingestion (CSV or PDF picked through SAF): the file's own
 * content decides the format, the detected provider is only a suggestion the
 * user confirms, and a confirmed file import records IMPORTED / USER-PROVIDED
 * statement evidence. Pasted CSV keeps the original paste flow without a
 * statement document.
 */
sealed interface ImportUiState {
    /** Step 1: choose a statement file (CSV or PDF) or paste CSV text. */
    data object Source : ImportUiState

    /** Parsing / reference loading in progress. */
    data object Loading : ImportUiState

    /**
     * File parsed: show content-based source detection and ask the user to
     * confirm or change the provider. Nothing is labelled connected.
     */
    data class SourceConfirm(
        val detection: StatementDetection,
        val documentName: String,
        val format: StatementFormat,
        val unparsedLines: List<String>,
        val notes: List<String>
    ) : ImportUiState

    /** Read-only preview: counts, rows, duplicates, unmatched references. */
    data class PreviewReady(
        val preview: ImportPreview,
        val selections: Map<Int, Boolean>,
        val accounts: List<com.prasbin.shadowmoney.data.model.Account>,
        val categories: List<com.prasbin.shadowmoney.data.model.Category>,
        val accountMappings: Map<String, Long>,
        val categoryMappings: Map<String, Long>,
        val accountLabels: Map<String, String>,
        val categoryLabels: Map<String, String>,
        val statement: SourceConfirm? = null,
        val statementProviderLabel: String? = null,
        val summary: StatementSummary? = null,
        val defaultAccountId: Long? = null
    ) : ImportUiState {
        val selectedImportableCount: Int
            get() = preview.rows.count {
                selections[it.rowNumber] == true && it.isImportable()
            }

        /** Statement rows carry no account column; one must be assigned. */
        val needsAccountAssignment: Boolean
            get() = preview.rows.any { it.rawAccount.isEmpty() }
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
        val invalidCount: Int,
        val statementId: Long? = null,
        val statementDocumentName: String? = null,
        val statementProviderLabel: String? = null
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
    private var statementMeta: StatementMeta? = null
    private var defaultAccountId: Long? = null

    /** Pasted CSV text: no statement document, original preview flow. */
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
        statementMeta = null
        defaultAccountId = null
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

    /**
     * Real statement file (CSV or PDF) picked through SAF. Content decides
     * the format — a "statement.pdf" that is really CSV parses as CSV. The
     * parsed result is held read-only until the user confirms the source.
     */
    fun submitStatementBytes(bytes: ByteArray, documentName: String) {
        if (bytes.isEmpty()) {
            _state.value = ImportUiState.EmptyInput
            return
        }
        if (bytes.size > maxInputBytes) {
            _state.value = ImportUiState.Error(
                "Import input exceeds the ${maxInputBytes / (1024 * 1024)} MB limit"
            )
            return
        }
        statementMeta = null
        defaultAccountId = null
        _state.value = ImportUiState.Loading
        scope.launch {
            runCatching {
                val isPdf = bytes.copyOfRange(0, minOf(bytes.size, 5))
                    .contentEquals("%PDF-".toByteArray(Charsets.ISO_8859_1))
                val input = if (isPdf) {
                    StatementInput.PdfDocument(bytes = bytes, documentName = documentName)
                } else {
                    StatementInput.TextDocument(
                        text = String(bytes, Charsets.UTF_8),
                        documentName = documentName,
                        format = StatementFormat.CSV
                    )
                }
                val outcome = StatementParserRegistry.parserFor(input.format).parse(input)
                when (outcome) {
                    is StatementParseOutcome.Failed -> StatementWork.Failed(outcome.failure.userMessage)
                    is StatementParseOutcome.Parsed -> StatementWork.Parsed(
                        result = outcome.result,
                        referenceData = repository.loadReference()
                    )
                }
            }.fold(
                onSuccess = { work ->
                    when (work) {
                        is StatementWork.Failed -> _state.value =
                            ImportUiState.Error(work.message)
                        is StatementWork.Parsed -> {
                            document = work.result.document
                            reference = work.referenceData
                            accountMappings = emptyMap()
                            categoryMappings = emptyMap()
                            accountLabels = emptyMap()
                            categoryLabels = emptyMap()
                            statementMeta = StatementMeta(
                                documentName = documentName,
                                format = work.result.format,
                                detection = work.result.detection,
                                unparsedLineCount = work.result.unparsedLines.size,
                                fileSha256 = StatementHash.sha256Hex(bytes),
                                confirmedSource = null
                            )
                            _state.value = ImportUiState.SourceConfirm(
                                detection = work.result.detection,
                                documentName = documentName,
                                format = work.result.format,
                                unparsedLines = work.result.unparsedLines,
                                notes = work.result.notes
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.value = ImportUiState.Error(
                        error.message ?: "Could not read the selected statement"
                    )
                }
            )
        }
    }

    /** The user confirmed (or changed) the detected source for this file. */
    fun confirmSource(source: StatementSource) {
        val meta = statementMeta ?: return
        val parsed = document ?: return
        meta.confirmedSource = source
        _state.value = ImportUiState.Loading
        scope.launch {
            runCatching {
                val referenceData = reference ?: repository.loadReference()
                reference = referenceData
                ImportEngine.buildPreview(
                    parsed,
                    referenceData,
                    accountMappings,
                    categoryMappings,
                    defaultAccountId
                )
            }.fold(
                onSuccess = { outcome ->
                    _state.value = when (outcome) {
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
                        error.message ?: "Could not build the preview"
                    )
                }
            )
        }
    }

    /**
     * Statement rows without an account column are assigned to one local
     * account. Rows are never guessed into an account silently.
     */
    fun setDefaultAccount(accountId: Long?) {
        defaultAccountId = accountId
        recomputePreview()
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

        val meta = statementMeta
        val statementContext = meta?.let {
            StatementImportContext(
                provider = (it.confirmedSource ?: StatementSource.UNKNOWN).storageName,
                documentName = it.documentName,
                format = it.format.storageName,
                detectionEvidence = buildString {
                    append(it.detection.evidence)
                    append("; source confirmed by user")
                    val detected = it.detection.candidate
                    val confirmed = it.confirmedSource ?: StatementSource.UNKNOWN
                    if (confirmed != detected) {
                        append(" (user selected ${confirmed.label} over detected ${detected.label})")
                    }
                },
                fileSha256 = it.fileSha256,
                unparsedLineCount = it.unparsedLineCount,
                rowCount = totalRows,
                invalidRowCount = invalidCount,
                duplicateRowCount = current.preview.duplicateRows
            )
        }

        _state.value = ImportUiState.Importing
        scope.launch {
            runCatching { repository.importSelectedWithStatement(selected, statementContext) }
                .fold(
                    onSuccess = { outcome ->
                        _state.value = ImportUiState.Success(
                            totalRows = totalRows,
                            importedCount = outcome.importedCount,
                            skippedCount = validCount - outcome.importedCount,
                            invalidCount = invalidCount,
                            statementId = outcome.statementId,
                            statementDocumentName = statementContext?.documentName,
                            statementProviderLabel = statementContext?.let {
                                StatementSource.fromStorage(it.provider).label
                            }
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
        statementMeta = null
        defaultAccountId = null
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
            ImportEngine.buildPreview(
                parsed,
                referenceData,
                accountMappings,
                categoryMappings,
                defaultAccountId
            )
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

    private fun buildPreviewState(preview: ImportPreview): ImportUiState {
        val meta = statementMeta
        val sourceConfirm = meta?.let {
            ImportUiState.SourceConfirm(
                detection = it.detection,
                documentName = it.documentName,
                format = it.format,
                unparsedLines = emptyList(),
                notes = emptyList()
            )
        }
        return ImportUiState.PreviewReady(
            preview = preview,
            selections = defaultSelections(preview),
            accounts = reference?.accounts ?: emptyList(),
            categories = reference?.categories ?: emptyList(),
            accountMappings = accountMappings,
            categoryMappings = categoryMappings,
            accountLabels = accountLabels,
            categoryLabels = categoryLabels,
            statement = sourceConfirm,
            statementProviderLabel = meta?.let {
                (it.confirmedSource ?: StatementSource.UNKNOWN).label
            },
            summary = meta?.let { StatementSummary.from(preview, it.unparsedLineCount) },
            defaultAccountId = defaultAccountId
        )
    }

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

    private sealed interface StatementWork {
        data class Parsed(
            val result: com.prasbin.shadowmoney.data.statements.StatementParseResult,
            val referenceData: ImportReference
        ) : StatementWork
        data class Failed(val message: String) : StatementWork
    }

    private data class StatementMeta(
        val documentName: String,
        val format: StatementFormat,
        val detection: StatementDetection,
        val unparsedLineCount: Int,
        val fileSha256: String,
        var confirmedSource: StatementSource?
    )
}
