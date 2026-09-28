package com.prasbin.shadowmoney.presentation.screen.settings

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.backup.BackupCreateOutcome
import com.prasbin.shadowmoney.data.backup.BackupRecordCounts
import com.prasbin.shadowmoney.data.backup.BackupRepository
import com.prasbin.shadowmoney.data.backup.BackupTestData
import com.prasbin.shadowmoney.data.backup.FakeBackupFileIo
import com.prasbin.shadowmoney.data.backup.FileReadResult
import com.prasbin.shadowmoney.data.backup.FileWriteResult
import com.prasbin.shadowmoney.data.backup.RESTORE_WARNING_TEXT
import com.prasbin.shadowmoney.data.model.Account
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var fileIo: FakeBackupFileIo
    private lateinit var repository: BackupRepository
    private lateinit var viewModel: BackupViewModel

    private val now = BackupTestData.NOW

    @Before
    fun setUp() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        fileIo = FakeBackupFileIo()
        repository = BackupRepository(database, fileIo, clock = { now })
        viewModel = BackupViewModel(repository)
    }

    @After
    fun closeDb() {
        runCatching { database.close() }
    }

    private suspend fun awaitState(predicate: (BackupUiState) -> Boolean): BackupUiState =
        withTimeout(10_000) { viewModel.state.first(predicate) }

    private suspend fun awaitCounts(): BackupRecordCounts =
        withTimeout(10_000) { viewModel.infoCounts.first { it != null }!! }

    private suspend fun seedOneAccount() {
        database.accountDao().insert(Account(name = "Wallet"))
    }

    private fun createBackupText(): String {
        val outcome = runBlocking { repository.createBackupJson() }
        assertTrue(outcome is BackupCreateOutcome.Success)
        return (outcome as BackupCreateOutcome.Success).json
    }

    @Test
    fun exportTo_writesFileAndReportsSuccessWithChecksumAndCounts() = runBlocking {
        seedOneAccount()
        viewModel.exportTo("doc://export-1")
        val state = awaitState { it is BackupUiState.ExportSuccess } as BackupUiState.ExportSuccess
        assertEquals(1, state.counts.accounts)
        assertEquals(1, state.counts.total)
        assertEquals(64, state.checksum.length)
        assertEquals(now, state.createdAtEpochMillis)
        assertTrue(fileIo.files.containsKey("doc://export-1"))
        assertEquals(1, awaitCounts().accounts)
    }

    @Test
    fun exportWithNoRecords_succeedsWithExplicitEmptyCounts() = runBlocking {
        viewModel.exportTo("doc://empty")
        val state = awaitState { it is BackupUiState.ExportSuccess } as BackupUiState.ExportSuccess
        assertEquals(0, state.counts.total)
        assertTrue(fileIo.files["doc://empty"]!!.contains("\"format\":\"shadow-money-backup\""))
    }

    @Test
    fun exportWriteFailure_reportsExportError_notFalseSuccess() = runBlocking {
        seedOneAccount()
        fileIo.writeResult = FileWriteResult.Failed("Could not write the backup file")
        viewModel.exportTo("doc://bad")
        val state = awaitState { it is BackupUiState.ExportError } as BackupUiState.ExportError
        assertEquals("Could not write the backup file", state.message)
    }

    @Test
    fun prepareRestore_validBackup_reachesPreviewReady_withoutAnyWrite() = runBlocking {
        seedOneAccount()
        val json = createBackupText()
        val accountsBefore = repository.loadCounts().accounts

        viewModel.prepareRestoreText(json)
        val state = awaitState { it is BackupUiState.PreviewReady } as BackupUiState.PreviewReady
        assertEquals(RESTORE_WARNING_TEXT, state.warning)
        assertEquals(1, state.candidate.counts.accounts)
        assertEquals(accountsBefore, repository.loadCounts().accounts)
    }

    @Test
    fun prepareRestore_tamperedBackup_reachesPreviewInvalid() = runBlocking {
        seedOneAccount()
        val tampered = createBackupText()
            .replace("\"openingBalanceMinor\":0", "\"openingBalanceMinor\":1")
        viewModel.prepareRestoreText(tampered)
        val state = awaitState { it is BackupUiState.PreviewInvalid } as BackupUiState.PreviewInvalid
        assertTrue(state.message.contains("checksum", ignoreCase = true))
    }

    @Test
    fun prepareRestoreFromUri_unreadableFile_reportsStorageError() = runBlocking {
        fileIo.readResultOverride = FileReadResult.Failed("Could not open the selected file")
        viewModel.prepareRestore("doc://missing")
        val state = awaitState { it is BackupUiState.PreviewInvalid } as BackupUiState.PreviewInvalid
        assertTrue(state.message.contains("Could not open the selected file"))
    }

    @Test
    fun confirmRestore_withoutPreview_doesNothing() {
        runBlocking { seedOneAccount() }
        viewModel.confirmRestore()
        assertEquals(BackupUiState.Idle, viewModel.state.value)
        assertTrue(runBlocking { repository.loadCounts().accounts } == 1)
    }

    @Test
    fun confirmRestore_fromPreview_performsAtomicReplace() = runBlocking {
        seedOneAccount()
        val json = createBackupText()
        viewModel.prepareRestoreText(json)
        awaitState { it is BackupUiState.PreviewReady }

        viewModel.confirmRestore()
        val state = awaitState { it is BackupUiState.RestoreSuccess } as BackupUiState.RestoreSuccess
        assertEquals(1, state.counts.accounts)
        assertEquals(1, repository.loadCounts().accounts)
        assertEquals(1, awaitCounts().accounts)
    }

    @Test
    fun restoreFailure_reportsRestoreError_andExplainsNothingChanged() = runBlocking {
        seedOneAccount()
        val json = createBackupText()
        viewModel.prepareRestoreText(json)
        awaitState { it is BackupUiState.PreviewReady }

        database.close()
        viewModel.confirmRestore()
        val state = awaitState { it is BackupUiState.RestoreError } as BackupUiState.RestoreError
        assertTrue(state.message.isNotEmpty())
    }

    @Test
    fun reset_returnsToIdle() = runBlocking {
        seedOneAccount()
        viewModel.prepareRestoreText("not a backup")
        awaitState { it is BackupUiState.PreviewInvalid }
        viewModel.reset()
        assertEquals(BackupUiState.Idle, viewModel.state.value)
    }

    @Test
    fun clearError_fromErrorStates_returnsToIdle() = runBlocking {
        viewModel.prepareRestoreText("")
        awaitState { it is BackupUiState.PreviewInvalid }
        viewModel.clearError()
        assertEquals(BackupUiState.Idle, viewModel.state.value)
    }

    @Test
    fun reportFileError_setsPreviewInvalid() = runBlocking {
        viewModel.reportFileError("Could not write the backup file")
        val state = awaitState { it is BackupUiState.PreviewInvalid } as BackupUiState.PreviewInvalid
        assertEquals("Could not write the backup file", state.message)
    }

    @Test
    fun refreshInfo_reportsLiveRecordCounts() = runBlocking {
        seedOneAccount()
        seedOneAccount()
        viewModel.refreshInfo()
        val counts = awaitCounts()
        assertEquals(2, counts.accounts)
        assertEquals(2, counts.total)
    }
}
