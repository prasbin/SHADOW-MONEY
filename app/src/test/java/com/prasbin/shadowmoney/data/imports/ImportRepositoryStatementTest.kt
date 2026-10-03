package com.prasbin.shadowmoney.data.imports

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImportRepositoryStatementTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: ImportRepository

    private val now = 1_800_000_000_000L

    private val header = "date,description,amount,direction,account,balance"

    private val context = StatementImportContext(
        provider = "SANIMA",
        documentName = "sanima_statement_jan_2026.csv",
        format = "CSV",
        detectionEvidence = "document content mentions Sanima",
        fileSha256 = "abc123",
        unparsedLineCount = 2,
        rowCount = 2,
        invalidRowCount = 0,
        duplicateRowCount = 0
    )

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ImportRepository(database, clock = { now })
        runBlocking {
            database.accountDao().insert(Account(name = "Wallet"))
            database.categoryDao().insert(
                Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
            )
        }
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun csv(vararg rows: String): String = (listOf(header) + rows).joinToString("\n")

    private suspend fun preview(text: String): ImportPreview {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse(text),
            repository.loadReference(),
            defaultAccountId = 1L
        )
        assertTrue("expected Ready but was $outcome", outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    private fun selected(preview: ImportPreview): List<ImportRow> =
        preview.rows.filter { it.isImportable() && it.state == ImportRowState.NEW }

    private suspend fun transactionCount(): Int =
        database.transactionDao().getAll().first().size

    private suspend fun statements(): List<com.prasbin.shadowmoney.data.model.ImportedStatement> =
        database.importedStatementDao().observeAll().first()

    @Test
    fun importWithStatement_createsEvidenceRowAndLinksEveryTransaction() = runBlocking {
        val result = preview(
            csv(
                "2026-01-05,Coffee,150.00,outflow,,10850.00",
                "2026-01-06,Salary,50000.00,income,,60850.00"
            )
        )
        val outcome = repository.importSelectedWithStatement(selected(result), context)

        assertEquals(2, outcome.importedCount)
        assertNotNull(outcome.statementId)

        val statement = statements().single()
        assertEquals(outcome.statementId, statement.id)
        assertEquals("SANIMA", statement.provider)
        assertEquals("sanima_statement_jan_2026.csv", statement.documentName)
        assertEquals("CSV", statement.format)
        assertEquals(now, statement.importedAtMs)
        assertEquals(2, statement.transactionCount)
        assertEquals(5_000_000L, statement.moneyInMinor)
        assertEquals(15_000L, statement.moneyOutMinor)
        assertEquals(6_085_000L, statement.endBalanceMinor)
        assertEquals(1L, statement.endBalanceAccountId)
        assertEquals(2, statement.rowCount)
        assertEquals(0, statement.invalidRowCount)
        assertEquals(0, statement.duplicateRowCount)
        assertEquals("abc123", statement.fileSha256)
        assertTrue(statement.notes.contains("document content mentions Sanima"))
        assertTrue(statement.notes.contains("2 unrecognized line(s)"))

        val transactions = database.transactionDao().getAll().first()
        assertEquals(2, transactions.size)
        assertTrue(transactions.all { it.statementId == statement.id })
        assertTrue(transactions.all { it.source == TRANSACTION_SOURCE_IMPORT_FILE })
    }

    @Test
    fun statementPeriodComesFromImportedRowsOnly() = runBlocking {
        val result = preview(
            csv(
                "2026-01-05,Coffee,150.00,outflow,,10850.00",
                "2026-01-10,Groceries,2000.00,outflow,,"
            )
        )
        repository.importSelectedWithStatement(selected(result), context)
        val statement = statements().single()

        val startOfDay = java.time.LocalDate.of(2026, 1, 5)
            .atStartOfDay(com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE)
            .toInstant().toEpochMilli()
        val endOfDay = java.time.LocalDate.of(2026, 1, 10)
            .atStartOfDay(com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE)
            .toInstant().toEpochMilli()
        assertEquals(startOfDay, statement.periodStartMs)
        assertEquals(endOfDay, statement.periodEndMs)
    }

    @Test
    fun secondImportOfSameStatement_showsDuplicatesAndCreatesNoSilentCopy() = runBlocking {
        val text = csv("2026-01-05,Coffee,150.00,outflow,,10850.00")

        val first = preview(text)
        val firstOutcome = repository.importSelectedWithStatement(selected(first), context)
        assertEquals(1, firstOutcome.importedCount)

        val second = preview(text)
        assertEquals(1, second.duplicateRows)
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, second.rows[0].state)

        val secondOutcome = repository.importSelectedWithStatement(selected(second), context)
        assertEquals(0, secondOutcome.importedCount)
        assertNull(secondOutcome.statementId)
        assertEquals(1, transactionCount())
        assertEquals(1, statements().size)
    }

    @Test
    fun nothingImportable_recordsNoStatement() = runBlocking {
        val result = preview(csv("2026-13-45,Broken,10.00,outflow,,"))
        assertEquals(0, result.importableRows)

        val outcome = repository.importSelectedWithStatement(selected(result), context)
        assertEquals(0, outcome.importedCount)
        assertNull(outcome.statementId)
        assertEquals(0, statements().size)
        assertEquals(0, transactionCount())
    }

    @Test
    fun plainImportSelected_withoutStatementContext_createsNoStatement() = runBlocking {
        val result = preview(csv("2026-01-05,Coffee,150.00,outflow,,10850.00"))
        val imported = repository.importSelected(selected(result))
        assertEquals(1, imported)
        assertEquals(0, statements().size)
        assertNull(database.transactionDao().getAll().first().single().statementId)
    }

    @Test
    fun statementRowCountsComeFromPreviewMetadataNeverFromSelection() = runBlocking {
        val text = csv(
            "2026-01-05,Coffee,150.00,outflow,,10850.00",
            "2026-01-05,Coffee,150.00,outflow,,10850.00",
            "2026-13-45,Broken,10.00,outflow,,"
        )
        val result = preview(text)
        assertEquals(1, result.invalidRows)
        assertEquals(1, result.duplicateRows)
        assertEquals(3, result.totalRows)

        val reviewedContext = context.copy(
            rowCount = result.totalRows,
            invalidRowCount = result.invalidRows,
            duplicateRowCount = result.duplicateRows
        )
        repository.importSelectedWithStatement(selected(result), reviewedContext)
        val statement = statements().single()
        assertEquals(3, statement.rowCount)
        assertEquals(1, statement.invalidRowCount)
        assertEquals(1, statement.duplicateRowCount)
        assertEquals(1, statement.transactionCount)
    }

    @Test
    fun unknownSourceAndPdfFormat_areStoredVerbatimNeverRewritten() = runBlocking {
        val pdfContext = StatementImportContext(
            provider = "UNKNOWN",
            documentName = "generic_export.pdf",
            format = "PDF",
            detectionEvidence = "no provider markers found in the document — confirm the source",
            fileSha256 = null,
            unparsedLineCount = 0,
            rowCount = 1,
            invalidRowCount = 0,
            duplicateRowCount = 0
        )
        val result = preview(csv("2026-01-05,Coffee,150.00,outflow,,10850.00"))
        repository.importSelectedWithStatement(selected(result), pdfContext)

        val statement = statements().single()
        assertEquals("UNKNOWN", statement.provider)
        assertEquals("PDF", statement.format)
        assertEquals("generic_export.pdf", statement.documentName)
        assertNull(statement.fileSha256)
        assertFalse(statement.notes.contains("/"))
    }
}
