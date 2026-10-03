package com.prasbin.shadowmoney.presentation.screen.csvimport

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.imports.ImportRepository
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.statements.StatementFormat
import com.prasbin.shadowmoney.data.statements.StatementSource
import com.prasbin.shadowmoney.data.statements.SyntheticPdf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImportTransactionsViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: ImportRepository

    private val now = 1_800_000_000_000L

    private val validCsv =
        "date,description,amount,direction,account,category\n" +
            "2026-09-01,Coffee,120.00,outflow,Wallet,Food\n"

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        runBlocking {
            database.accountDao().insert(
                Account(name = "Wallet", openingBalanceMinor = 100_000L, createdTimestamp = now)
            )
            database.categoryDao().insert(
                Category(name = "Food", isActive = true, isSystem = true, createdTimestamp = now)
            )
        }
        repository = ImportRepository(database, clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(maxInputBytes: Int = 5 * 1024 * 1024) =
        ImportTransactionsViewModel(repository, maxInputBytes = maxInputBytes)

    private suspend fun awaitState(
        vm: ImportTransactionsViewModel,
        predicate: (ImportUiState) -> Boolean
    ): ImportUiState = withTimeout(10_000) { vm.state.first(predicate) }

    private suspend fun transactionCount(): Int =
        database.transactionDao().getAll().first().size

    @Test
    fun initialState_isSource() {
        val vm = viewModel()
        assertTrue(vm.state.value is ImportUiState.Source)
    }

    @Test
    fun submitCsv_valid_reachesPreviewWithNewRowSelected() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        val state = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady

        assertEquals(1, state.preview.totalRows)
        assertEquals(1, state.preview.validRows)
        assertEquals(0, state.preview.invalidRows)
        assertEquals(0, state.preview.duplicateRows)
        assertEquals(1, state.selectedImportableCount)
        assertEquals(true, state.selections[1])
        assertTrue(state.preview.rows[0].isImportable())

        assertEquals(0, transactionCount())
    }

    @Test
    fun submitCsv_missingRequiredColumn_rejected() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(
            "date,description,amount,direction\n" +
                "2026-09-01,Coffee,120.00,outflow\n"
        )
        val state = awaitState(vm) { it is ImportUiState.PreviewRejected }
            as ImportUiState.PreviewRejected
        assertTrue(state.reason.contains("account"))
        assertEquals(0, transactionCount())
    }

    @Test
    fun submitCsv_blank_emptyInput() = runBlocking {
        val vm = viewModel()
        vm.submitCsv("   \n  ")
        val state = awaitState(vm) { it is ImportUiState.EmptyInput }
        assertTrue(state is ImportUiState.EmptyInput)
    }

    @Test
    fun submitCsv_oversize_errorWithoutPreview() = runBlocking {
        val vm = viewModel(maxInputBytes = 64)
        vm.submitCsv(validCsv)
        val state = awaitState(vm) { it is ImportUiState.Error } as ImportUiState.Error
        assertTrue(state.message.contains("limit"))
        assertEquals(0, transactionCount())
    }

    @Test
    fun confirmImport_writesConfirmedTransactions() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        awaitState(vm) { it is ImportUiState.PreviewReady }
        assertEquals(0, transactionCount())

        vm.confirmImport()
        val success = awaitState(vm) { it is ImportUiState.Success }
            as ImportUiState.Success

        assertEquals(1, success.totalRows)
        assertEquals(1, success.importedCount)
        assertEquals(0, success.skippedCount)
        assertEquals(0, success.invalidCount)

        val transactions = database.transactionDao().getAll().first()
        assertEquals(1, transactions.size)
        assertEquals(TRANSACTION_SOURCE_IMPORT_FILE, transactions[0].source)
        assertEquals(12_000L, transactions[0].amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, transactions[0].direction)
        assertEquals(now, transactions[0].createdTimestamp)
    }

    @Test
    fun confirmImport_withoutSelection_errorAndNoWrite() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        awaitState(vm) { it is ImportUiState.PreviewReady }

        vm.toggleRow(1, selected = false)
        vm.confirmImport()

        val state = awaitState(vm) { it is ImportUiState.Error }
            as ImportUiState.Error
        assertEquals("No transactions selected for import", state.message)
        assertEquals(0, transactionCount())
    }

    @Test
    fun toggleRow_updatesSelectionCounts() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady
        assertEquals(1, preview.selectedImportableCount)

        vm.toggleRow(1, selected = false)
        val unselected = vm.state.value as ImportUiState.PreviewReady
        assertEquals(0, unselected.selectedImportableCount)

        vm.toggleRow(1, selected = true)
        val reselected = vm.state.value as ImportUiState.PreviewReady
        assertEquals(1, reselected.selectedImportableCount)
    }

    @Test
    fun duplicateRow_defaultsUnselected_thenExplicitDecisionImportsIt() = runBlocking {
        val first = viewModel()
        first.submitCsv(validCsv)
        awaitState(first) { it is ImportUiState.PreviewReady }
        first.confirmImport()
        awaitState(first) { it is ImportUiState.Success }

        val vm = viewModel()
        vm.submitCsv(validCsv)
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady

        assertEquals(1, preview.preview.duplicateRows)
        assertEquals(0, preview.selectedImportableCount)
        assertEquals(false, preview.selections[1])
        assertEquals(1, transactionCount())

        vm.setDuplicateDecision(1, importRow = true)
        val chosen = vm.state.value as ImportUiState.PreviewReady
        assertEquals(1, chosen.selectedImportableCount)

        vm.confirmImport()
        val success = awaitState(vm) { it is ImportUiState.Success }
            as ImportUiState.Success
        assertEquals(1, success.importedCount)
        assertEquals(2, transactionCount())
    }

    @Test
    fun unmatchedAccount_requiresExplicitMapping() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(
            "date,description,amount,direction,account,category\n" +
                "2026-09-01,Coffee,120.00,outflow,Cash,Food\n"
        )
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady

        assertEquals(1, preview.preview.unmatchedAccountNames.size)
        assertFalse(preview.preview.rows[0].isImportable())
        assertEquals(0, preview.selectedImportableCount)

        vm.setAccountMapping("Cash", 1L)
        val mapped = vm.state.value as ImportUiState.PreviewReady
        assertEquals(0, mapped.preview.unmatchedAccountNames.size)
        assertTrue(mapped.preview.rows[0].isImportable())
        assertEquals(1, mapped.selectedImportableCount)

        vm.confirmImport()
        awaitState(vm) { it is ImportUiState.Success }
        val transactions = database.transactionDao().getAll().first()
        assertEquals(1, transactions.size)
        assertEquals(1L, transactions[0].accountId)
    }

    @Test
    fun startOver_returnsToSourceState() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        awaitState(vm) { it is ImportUiState.PreviewReady }
        vm.startOver()
        assertTrue(vm.state.value is ImportUiState.Source)
        assertEquals(0, transactionCount())
    }

    @Test
    fun rejectedInput_canBeRetriedAfterStartOver() = runBlocking {
        val vm = viewModel()
        vm.submitCsv("no,header,here\n1,2,3\n")
        awaitState(vm) { it is ImportUiState.PreviewRejected }

        vm.startOver()
        assertTrue(vm.state.value is ImportUiState.Source)

        vm.submitCsv(validCsv)
        val state = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady
        assertEquals(1, state.preview.validRows)
    }

    // ------------------------------------------------------ statement ingestion

    private fun sanimaPdfBytes(): ByteArray = SyntheticPdf.flateCompressed(
        """
            BT 72 760 Td (Sanima Bank Limited) Tj 0 -20 Td (Synthetic statement) Tj
            0 -28 Td (Date) Tj 90 0 Td (Description) Tj 130 0 Td (Debit) Tj
            80 0 Td (Credit) Tj 80 0 Td (Balance) Tj
            0 -16 Td (2026-01-05) Tj 90 0 Td (Coffee Shop) Tj 130 0 Td (150.00) Tj
            80 0 Td () Tj 80 0 Td (10850.00) Tj
            0 -16 Td (2026-01-06) Tj 90 0 Td (Salary) Tj 130 0 Td () Tj
            80 0 Td (50000.00) Tj 80 0 Td (60850.00) Tj
            ET
        """.trimIndent()
    )

    @Test
    fun statementFile_detectsSourceConfirmsThenImportsWithEvidence() = runBlocking {
        val vm = viewModel()
        vm.submitStatementBytes(sanimaPdfBytes(), "sanima_jan_2026.pdf")
        val confirm = awaitState(vm) { it is ImportUiState.SourceConfirm }
            as ImportUiState.SourceConfirm

        assertEquals(StatementSource.SANIMA, confirm.detection.candidate)
        assertEquals("sanima_jan_2026.pdf", confirm.documentName)
        assertEquals(StatementFormat.PDF, confirm.format)
        assertTrue(confirm.detection.evidence.contains("Sanima"))
        assertEquals(0, transactionCount())

        vm.confirmSource(StatementSource.SANIMA)
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady

        assertNotNull(preview.statement)
        assertEquals("Sanima", preview.statementProviderLabel)
        assertTrue(preview.needsAccountAssignment)
        assertNotNull(preview.summary)
        assertEquals(2, preview.summary!!.totalRows)
        assertEquals(0, preview.selectedImportableCount)

        vm.setDefaultAccount(1L)
        val assigned = vm.state.value as ImportUiState.PreviewReady
        assertEquals(1L, assigned.defaultAccountId)
        assertEquals(2, assigned.selectedImportableCount)
        assertEquals(6_085_000L, assigned.summary!!.endBalanceMinor)
        assertEquals(5_000_000L, assigned.summary!!.moneyInMinor)
        assertEquals(15_000L, assigned.summary!!.moneyOutMinor)

        vm.confirmImport()
        val success = awaitState(vm) { it is ImportUiState.Success }
            as ImportUiState.Success
        assertEquals(2, success.importedCount)
        assertEquals("sanima_jan_2026.pdf", success.statementDocumentName)
        assertEquals("Sanima", success.statementProviderLabel)
        assertNotNull(success.statementId)

        val statement = database.importedStatementDao().observeAll().first().single()
        assertEquals("SANIMA", statement.provider)
        assertEquals("PDF", statement.format)
        assertEquals(2, statement.transactionCount)
        assertEquals(6_085_000L, statement.endBalanceMinor)
        assertTrue(statement.notes.contains("confirmed by user"))

        val transactions = database.transactionDao().getAll().first()
        assertEquals(2, transactions.size)
        assertTrue(transactions.all { it.statementId == statement.id })
    }

    @Test
    fun statementFile_unknownSource_isStoredOnlyAfterUserConfirmsUnknown() = runBlocking {
        val genericPdf = SyntheticPdf.simple(
            """
                BT 72 760 Td (Generic Export) Tj
                0 -24 Td (Date) Tj 90 0 Td (Description) Tj 90 0 Td (Debit) Tj
                0 -16 Td (2026-01-05) Tj 90 0 Td (Coffee) Tj 90 0 Td (150.00) Tj
                ET
            """.trimIndent()
        )
        val vm = viewModel()
        vm.submitStatementBytes(genericPdf, "generic.pdf")
        val confirm = awaitState(vm) { it is ImportUiState.SourceConfirm }
            as ImportUiState.SourceConfirm
        assertEquals(StatementSource.UNKNOWN, confirm.detection.candidate)

        vm.confirmSource(StatementSource.UNKNOWN)
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady
        vm.setDefaultAccount(1L)
        vm.confirmImport()
        val success = awaitState(vm) { it is ImportUiState.Success }
            as ImportUiState.Success
        assertEquals(1, success.importedCount)
        assertEquals("Unknown source", success.statementProviderLabel)

        val statement = database.importedStatementDao().observeAll().first().single()
        assertEquals("UNKNOWN", statement.provider)
        assertTrue(statement.notes.contains("no provider markers"))
    }

    @Test
    fun statementFile_encryptedPdf_failsSafelyWithoutPasswordPrompt() = runBlocking {
        val vm = viewModel()
        vm.submitStatementBytes(
            SyntheticPdf.simple("BT (secret) Tj ET", encrypt = true),
            "locked.pdf"
        )
        val error = awaitState(vm) { it is ImportUiState.Error }
            as ImportUiState.Error
        assertTrue(error.message.contains("password-protected"))
        assertTrue(error.message.contains("never asks"))
        assertEquals(0, transactionCount())
        assertEquals(0, database.importedStatementDao().observeAll().first().size)
    }

    @Test
    fun pastedCsv_createsNoStatementEvidence() = runBlocking {
        val vm = viewModel()
        vm.submitCsv(validCsv)
        val preview = awaitState(vm) { it is ImportUiState.PreviewReady }
            as ImportUiState.PreviewReady
        assertNull(preview.statement)
        assertNull(preview.summary)

        vm.confirmImport()
        val success = awaitState(vm) { it is ImportUiState.Success }
            as ImportUiState.Success
        assertNull(success.statementId)
        assertNull(success.statementDocumentName)
        assertEquals(1, transactionCount())
        assertEquals(0, database.importedStatementDao().observeAll().first().size)
    }

    @Test
    fun statementFile_secondImportOfSameDocument_showsDuplicateBeforeAnyWrite() = runBlocking {
        val first = viewModel()
        first.submitStatementBytes(sanimaPdfBytes(), "sanima_jan_2026.pdf")
        awaitState(first) { it is ImportUiState.SourceConfirm }
        first.confirmSource(StatementSource.SANIMA)
        awaitState(first) { it is ImportUiState.PreviewReady }
        first.setDefaultAccount(1L)
        first.confirmImport()
        awaitState(first) { it is ImportUiState.Success }
        assertEquals(2, transactionCount())

        val second = viewModel()
        second.submitStatementBytes(sanimaPdfBytes(), "sanima_jan_2026.pdf")
        awaitState(second) { it is ImportUiState.SourceConfirm }
        second.confirmSource(StatementSource.SANIMA)
        awaitState(second) { it is ImportUiState.PreviewReady }
        second.setDefaultAccount(1L)
        val preview = second.state.value as ImportUiState.PreviewReady

        assertEquals(2, preview.preview.duplicateRows)
        assertEquals(0, preview.selectedImportableCount)
        assertEquals(2, transactionCount())
        assertEquals(1, database.importedStatementDao().observeAll().first().size)
    }
}
