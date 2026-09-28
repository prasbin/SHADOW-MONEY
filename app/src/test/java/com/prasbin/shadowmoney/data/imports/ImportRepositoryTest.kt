package com.prasbin.shadowmoney.data.imports

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import com.prasbin.shadowmoney.data.model.Transaction
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
class ImportRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: ImportRepository

    private val now = 1_800_000_000_000L

    private val header = "date,description,amount,direction,account,category"

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

    private suspend fun preview(text: String, mappings: Map<String, Long> = emptyMap()): ImportPreview {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse(text),
            repository.loadReference(),
            mappings
        )
        assertTrue("expected Ready but was $outcome", outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    private fun selectedDefaults(preview: ImportPreview): List<ImportRow> =
        preview.rows.filter { it.isImportable() && it.state == ImportRowState.NEW }

    private suspend fun transactionCount(): Int = database.transactionDao().getAll().first().size

    private fun tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table'", emptyArray())
            .use { cursor ->
                while (cursor.moveToNext()) names.add(cursor.getString(0))
            }
        return names
    }

    @Test
    fun preview_makesNoDatabaseWrite() = runBlocking {
        val before = transactionCount()
        val result = preview(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        assertEquals(1, result.totalRows)
        assertEquals(before, transactionCount())
        assertEquals(0, transactionCount())
    }

    @Test
    fun preview_detectsExistingTransactionAsDuplicate() = runBlocking {
        val dayTimestamp = java.time.LocalDate.of(2026, 9, 1)
            .atStartOfDay(com.prasbin.shadowmoney.data.BudgetCalendar.KATHMANDU_ZONE)
            .toInstant().toEpochMilli()
        database.transactionDao().insert(
            Transaction(
                accountId = 1L,
                categoryId = 1L,
                amountMinor = 12000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = dayTimestamp,
                note = "Coffee",
                source = "manual"
            )
        )

        val result = preview(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        assertEquals(1, result.duplicateRows)
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, result.rows[0].state)
        assertEquals(DuplicateMatchType.EXISTING_TRANSACTION, result.rows[0].duplicate!!.type)
        assertNotNull(result.rows[0].duplicate!!.existingTransactionId)
    }

    @Test
    fun importSelected_createsOrdinaryTransactionsWithImportSource() = runBlocking {
        val result = preview(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        val imported = repository.importSelected(selectedDefaults(result))

        assertEquals(1, imported)
        val transactions = database.transactionDao().getAll().first()
        assertEquals(1, transactions.size)
        val tx = transactions[0]
        assertEquals(12000L, tx.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, tx.direction)
        assertEquals(1L, tx.accountId)
        assertEquals(1L, tx.categoryId)
        assertEquals("Coffee", tx.note)
        assertEquals(TRANSACTION_SOURCE_IMPORT_FILE, tx.source)
        assertNull(tx.externalRef)
        assertEquals(now, tx.createdTimestamp)
    }

    @Test
    fun importSelected_preservesExternalReference() = runBlocking {
        val text = "date,description,amount,direction,account,category,external_ref\n" +
            "2026-09-01,Coffee,120.00,outflow,Wallet,Food,TX-42"
        val result = preview(text)
        repository.importSelected(selectedDefaults(result))

        val tx = database.transactionDao().getAll().first().single()
        assertEquals("TX-42", tx.externalRef)
    }

    @Test
    fun importedRows_appearThroughNormalTransactionQueriesAndTotals() = runBlocking {
        val text = "date,description,amount,direction,account,category\n" +
            "2026-09-01,Salary,50000,income,Wallet,Food\n" +
            "2026-09-02,Coffee,120.00,outflow,Wallet,Food"
        val result = preview(text)
        repository.importSelected(selectedDefaults(result))

        assertEquals(2, transactionCount())
        assertEquals(5_000_000L, database.transactionDao().getTotalIncomeMinorForActiveAccounts())
        assertEquals(12_000L, database.transactionDao().getTotalOutflowMinorForActiveAccounts())
        val recent = database.transactionDao().getRecent(10).first()
        assertEquals(2, recent.size)
        assertTrue(recent.all { it.source == TRANSACTION_SOURCE_IMPORT_FILE })
    }

    @Test
    fun importSelected_skippedRowsNotImported() = runBlocking {
        val result = preview(
            csv(
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food",
                "2026-09-02,Tea,80.00,outflow,Wallet,Food"
            )
        )
        val selected = listOf(result.rows.first())
        val imported = repository.importSelected(selected)
        assertEquals(1, imported)
        assertEquals(1, transactionCount())
        assertEquals("Coffee", database.transactionDao().getAll().first().single().note)
    }

    @Test
    fun invalidRows_neverImported() = runBlocking {
        val result = preview(
            csv(
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food",
                "2026-13-45,Broken,100,outflow,Wallet,Food"
            )
        )
        assertEquals(1, result.invalidRows)
        val imported = repository.importSelected(selectedDefaults(result))
        assertEquals(1, imported)
        val notes = database.transactionDao().getAll().first().map { it.note }
        assertEquals(listOf("Coffee"), notes)
    }

    @Test
    fun invalidRows_passedExplicitly_stillRejectedByRepository() = runBlocking {
        val result = preview(
            csv(
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food",
                "2026-13-45,Broken,100,outflow,Wallet,Food"
            )
        )
        val imported = repository.importSelected(result.rows)
        assertEquals(1, imported)
        assertFalse(
            database.transactionDao().getAll().first().any { it.note == "Broken" }
        )
    }

    @Test
    fun unmatchedRow_notImportedUntilMapped() = runBlocking {
        val text = csv("2026-09-01,Coffee,120.00,outflow,Reserve,Food")
        val before = preview(text)
        assertEquals(0, before.importableRows)
        assertEquals(0, repository.importSelected(selectedDefaults(before)))
        assertEquals(0, transactionCount())

        val mapped = preview(text, mappings = mapOf("reserve" to 1L))
        assertEquals(1, mapped.importableRows)
        val imported = repository.importSelected(selectedDefaults(mapped))
        assertEquals(1, imported)
        assertEquals(1L, database.transactionDao().getAll().first().single().accountId)
    }

    @Test
    fun importSelected_isAtomic_simulatedFailureRollsBackWholeBatch() = runBlocking {
        val result = preview(
            csv(
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food",
                "2026-09-02,Tea,80.00,outflow,Wallet,Food"
            )
        )
        val rows = selectedDefaults(result)
        assertEquals(2, rows.size)
        // Simulate a concurrent deletion of the target account for one row:
        // the second insert violates the account foreign key.
        val poisoned = rows[0].copy(accountId = 99999L)

        assertThrows(Exception::class.java) {
            runBlocking { repository.importSelected(listOf(rows[1], poisoned)) }
        }
        assertEquals("failed batch must not leave partial data", 0, transactionCount())
    }

    @Test
    fun noParallelLedger_singleTransactionsTableRemainsSourceOfTruth() = runBlocking {
        val tablesBefore = tableNames()
        val result = preview(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        repository.importSelected(selectedDefaults(result))
        val tablesAfter = tableNames()
        assertEquals(tablesBefore, tablesAfter)
    }

    @Test
    fun secondImportOfSameFile_detectsRowsAsDuplicates() = runBlocking {
        val text = csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food")
        val first = preview(text)
        repository.importSelected(selectedDefaults(first))
        assertEquals(1, transactionCount())

        val second = preview(text)
        assertEquals(1, second.duplicateRows)
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, second.rows[0].state)
        // A skipped duplicate imports nothing new.
        val imported = repository.importSelected(
            second.rows.filter { it.isImportable() && it.state == ImportRowState.NEW }
        )
        assertEquals(0, imported)
        assertEquals(1, transactionCount())
    }

    @Test
    fun importSelected_emptySelection_writesNothing() = runBlocking {
        val result = preview(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        assertEquals(0, repository.importSelected(emptyList()))
        assertEquals(0, transactionCount())
    }
}
