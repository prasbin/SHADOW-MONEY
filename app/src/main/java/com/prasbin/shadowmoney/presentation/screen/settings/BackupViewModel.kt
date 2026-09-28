package com.prasbin.shadowmoney.presentation.screen.settings

import androidx.lifecycle.ViewModel
import com.prasbin.shadowmoney.data.backup.BackupCreateOutcome
import com.prasbin.shadowmoney.data.backup.BackupRecordCounts
import com.prasbin.shadowmoney.data.backup.BackupRepository
import com.prasbin.shadowmoney.data.backup.FileWriteResult
import com.prasbin.shadowmoney.data.backup.RESTORE_WARNING_TEXT
import com.prasbin.shadowmoney.data.backup.RestoreCandidate
import com.prasbin.shadowmoney.data.backup.RestorePreviewOutcome
import com.prasbin.shadowmoney.data.backup.RestoreWriteOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState

    /** Backup file being generated and written to the chosen destination. */
    data object Exporting : BackupUiState

    data class ExportSuccess(
        val checksum: String,
        val counts: BackupRecordCounts,
        val createdAtEpochMillis: Long
    ) : BackupUiState

    data class ExportError(val message: String) : BackupUiState

    /** Selected backup file being parsed, checksum-verified and validated. */
    data object Preparing : BackupUiState

    /**
     * Read-only preview. Nothing was written to the database; restoring only
     * happens after [BackupViewModel.confirmRestore] is called from this
     * state with the explicit destructive confirmation.
     */
    data class PreviewReady(
        val candidate: RestoreCandidate,
        val warning: String = RESTORE_WARNING_TEXT
    ) : BackupUiState

    data class PreviewInvalid(val message: String) : BackupUiState

    data object Restoring : BackupUiState

    data class RestoreSuccess(val counts: BackupRecordCounts) : BackupUiState

    data class RestoreError(val message: String) : BackupUiState
}

class BackupViewModel(
    private val repository: BackupRepository
) : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state

    private val _infoCounts = MutableStateFlow<BackupRecordCounts?>(null)
    val infoCounts: StateFlow<BackupRecordCounts?> = _infoCounts

    fun refreshInfo() {
        scope.launch {
            runCatching { repository.loadCounts() }
                .onSuccess { _infoCounts.value = it }
                .onFailure { _infoCounts.value = BackupRecordCounts() }
        }
    }

    fun exportTo(uriString: String) {
        _state.value = BackupUiState.Exporting
        scope.launch {
            when (val created = repository.createBackupJson()) {
                is BackupCreateOutcome.Failed -> {
                    _state.value = BackupUiState.ExportError(created.error.message)
                }
                is BackupCreateOutcome.Success -> {
                    val write = runCatching {
                        repository.writeBackupToUri(uriString, created.json)
                    }.getOrElse { FileWriteResult.Failed("Could not write the backup file") }
                    when (write) {
                        is FileWriteResult.Success -> {
                            _state.value = BackupUiState.ExportSuccess(
                                checksum = created.checksum,
                                counts = created.counts,
                                createdAtEpochMillis = created.createdAtEpochMillis
                            )
                            refreshInfo()
                        }
                        is FileWriteResult.Failed -> {
                            _state.value = BackupUiState.ExportError(write.reason)
                        }
                    }
                }
            }
        }
    }

    fun prepareRestore(uriString: String) {
        _state.value = BackupUiState.Preparing
        scope.launch {
            when (val preview = repository.prepareRestoreFromUri(uriString)) {
                is RestorePreviewOutcome.Rejected -> {
                    _state.value = BackupUiState.PreviewInvalid(preview.error.message)
                }
                is RestorePreviewOutcome.Ready -> {
                    _state.value = BackupUiState.PreviewReady(preview.candidate)
                }
            }
        }
    }

    fun prepareRestoreText(text: String) {
        _state.value = BackupUiState.Preparing
        scope.launch {
            when (val preview = repository.prepareRestoreText(text)) {
                is RestorePreviewOutcome.Rejected -> {
                    _state.value = BackupUiState.PreviewInvalid(preview.error.message)
                }
                is RestorePreviewOutcome.Ready -> {
                    _state.value = BackupUiState.PreviewReady(preview.candidate)
                }
            }
        }
    }

    fun confirmRestore() {
        val current = _state.value as? BackupUiState.PreviewReady ?: return
        _state.value = BackupUiState.Restoring
        scope.launch {
            when (val result = repository.restore(current.candidate)) {
                is RestoreWriteOutcome.Failed -> {
                    _state.value = BackupUiState.RestoreError(result.error.message)
                }
                is RestoreWriteOutcome.Success -> {
                    _state.value = BackupUiState.RestoreSuccess(result.counts)
                    refreshInfo()
                }
            }
        }
    }

    fun reportFileError(message: String) {
        _state.value = BackupUiState.PreviewInvalid(message)
    }

    fun reset() {
        _state.value = BackupUiState.Idle
    }

    fun clearError() {
        val current = _state.value
        if (current is BackupUiState.ExportError || current is BackupUiState.PreviewInvalid ||
            current is BackupUiState.RestoreError
        ) {
            _state.value = BackupUiState.Idle
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
