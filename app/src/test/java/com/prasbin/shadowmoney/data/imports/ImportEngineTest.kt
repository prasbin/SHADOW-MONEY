package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ImportEngineTest {

    private val wallet = Account(id = 1, name = "Wallet")
    private val bank = Account(id = 2, name = "Bank")
    private val food = Category(id = 10, name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
    private val travel = Category(id = 11, name = "Travel", direction = CATEGORY_DIRECTION_OUTFLOW)

    private val reference = ImportReference(
        accounts = listOf(wallet, bank),
        categories = listOf(food, travel),
        existingFingerprints = emptyMap()
    )

    private fun csv(vararg rows: String): String =
        (listOf("date,description,amount,direction,account,category") + rows).joinToString("\n")

    private fun previewOf(
        text: String,
        ref: ImportReference = reference,
        accountMappings: Map<String, Long> = emptyMap(),
        categoryMappings: Map<String, Long> = emptyMap()
    ): ImportPreview {
        val outcome = ImportEngine.buildPreview(CsvParser.parse(text), ref, accountMappings, categoryMappings)
        assertTrue("expected Ready but was $outcome", outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    private fun dayStart(iso: String): Long =
        LocalDate.parse(iso).atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()

    @Test
    fun validRow_parsedToTypedValues() {
        val preview = previewOf(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"))
        assertEquals(1, preview.rows.size)
        val row = preview.rows[0]
        assertEquals(ImportRowState.NEW, row.state)
        assertTrue(row.invalidReasons.isEmpty())
        assertEquals(12000L, row.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, row.direction)
        assertEquals(dayStart("2026-09-01"), row.timestamp)
        assertEquals(1L, row.accountId)
        assertEquals(10L, row.categoryId)
        assertEquals("Coffee", row.note)
        assertTrue(row.isImportable())
        assertEquals(1, preview.importableRows)
    }

    @Test
    fun incomeWithNegativeAmount_contradictionRejected() {
        val preview = previewOf(csv("2026-09-01,Coffee,-500.00,income,Wallet,Food"))
        val row = preview.rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("conflict") })
        assertFalse(row.isImportable())
    }

    @Test
    fun incomeWithPositiveAmount_accepted() {
        val row = previewOf(csv("2026-09-01,Salary,50000,income,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
        assertEquals(5000000L, row.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_INCOME, row.direction)
    }

    @Test
    fun outflowWithNegativeAmount_storesAbsoluteMagnitude() {
        val row = previewOf(csv("2026-09-01,Rent,-15000,outflow,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
        assertEquals(1500000L, row.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, row.direction)
    }

    @Test
    fun outflowWithPositiveAmount_directionAuthoritative() {
        val row = previewOf(csv("2026-09-01,Groceries,1500.50,debit,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
        assertEquals(150050L, row.amountMinor)
        assertEquals(TRANSACTION_DIRECTION_OUTFLOW, row.direction)
    }

    @Test
    fun zeroAmount_neverBecomesATransaction() {
        val row = previewOf(csv("2026-09-01,Noop,0.00,outflow,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("non-zero") })
    }

    @Test
    fun unsupportedDirection_invalidRow() {
        val row = previewOf(csv("2026-09-01,Coffee,100,wire,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Unsupported direction") })
    }

    @Test
    fun missingAccountCell_invalidRow() {
        val row = previewOf(csv("2026-09-01,Coffee,100,outflow,,Food")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Missing account") })
    }

    @Test
    fun missingDescription_invalidRow() {
        val row = previewOf(csv("2026-09-01,,100,outflow,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Missing description") })
    }

    @Test
    fun invalidDate_invalidRow() {
        val row = previewOf(csv("2026-02-30,Coffee,100,outflow,Wallet,Food")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Invalid date") })
    }

    @Test
    fun malformedRow_wrongFieldCount_invalidRow() {
        val row = previewOf(csv("2026-09-01,Coffee,100,outflow,Wallet")).rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Malformed row") })
    }

    @Test
    fun unmatchedAccount_flaggedAndNotImportable() {
        val preview = previewOf(csv("2026-09-01,Coffee,100,outflow,Reserve,Food"))
        val row = preview.rows[0]
        assertEquals(ImportRowState.NEW, row.state)
        assertEquals("Reserve", row.unmatchedAccountName)
        assertEquals(listOf("Reserve"), preview.unmatchedAccountNames)
        assertFalse(row.isImportable())
        assertEquals(0, preview.importableRows)
    }

    @Test
    fun unmatchedCategory_flaggedAndNotImportable() {
        val preview = previewOf(csv("2026-09-01,Coffee,100,outflow,Wallet,Donations"))
        val row = preview.rows[0]
        assertEquals("Donations", row.unmatchedCategoryName)
        assertEquals(listOf("Donations"), preview.unmatchedCategoryNames)
        assertFalse(row.isImportable())
    }

    @Test
    fun accountMapping_resolvesUnmatchedRow() {
        val preview = previewOf(
            csv("2026-09-01,Coffee,100,outflow,Reserve,Food"),
            accountMappings = mapOf("reserve" to 2L)
        )
        val row = preview.rows[0]
        assertNull(row.unmatchedAccountName)
        assertEquals(2L, row.accountId)
        assertTrue(row.isImportable())
        assertEquals(1, preview.importableRows)
        assertTrue(preview.unmatchedAccountNames.isEmpty())
    }

    @Test
    fun categoryMapping_resolvesUnmatchedRow() {
        val preview = previewOf(
            csv("2026-09-01,Coffee,100,outflow,Wallet,Donations"),
            categoryMappings = mapOf("donations" to 11L)
        )
        val row = preview.rows[0]
        assertNull(row.unmatchedCategoryName)
        assertEquals(11L, row.categoryId)
        assertTrue(row.isImportable())
    }

    @Test
    fun mappingToNonexistentRecord_staysUnmatched() {
        val preview = previewOf(
            csv("2026-09-01,Coffee,100,outflow,Reserve,Food"),
            accountMappings = mapOf("reserve" to 999L)
        )
        assertEquals("Reserve", preview.rows[0].unmatchedAccountName)
        assertFalse(preview.rows[0].isImportable())
    }

    @Test
    fun categoryColumnAbsent_categoryOptionalAndNull() {
        val text = "date,description,amount,direction,account\n2026-09-01,Coffee,100,outflow,Wallet"
        val preview = previewOf(text)
        val row = preview.rows[0]
        assertNull(row.rawCategory)
        assertNull(row.categoryId)
        assertTrue(row.invalidReasons.isEmpty())
        assertTrue(row.isImportable())
    }

    @Test
    fun categoryColumnPresentEmptyCell_invalidRow() {
        val preview = previewOf(csv("2026-09-01,Coffee,100,outflow,Wallet,"))
        val row = preview.rows[0]
        assertEquals(ImportRowState.INVALID, row.state)
        assertTrue(row.invalidReasons.any { it.contains("Missing category") })
    }

    @Test
    fun exactDuplicateOfExistingTransaction_detected() {
        val fingerprint = ImportFingerprint.of(
            accountName = "Wallet",
            timestamp = dayStart("2026-09-01"),
            amountMinor = 12000L,
            direction = TRANSACTION_DIRECTION_OUTFLOW,
            categoryName = "Food",
            note = "Coffee",
            externalRef = null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"), ref).rows[0]
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, row.state)
        assertNotNull(row.duplicate)
        assertEquals(DuplicateMatchType.EXISTING_TRANSACTION, row.duplicate!!.type)
        assertEquals(77L, row.duplicate!!.existingTransactionId)
        assertTrue(row.duplicate!!.reason.contains("#77"))
        assertTrue(row.isImportable())
    }

    @Test
    fun nearDuplicate_differentAmount_new() {
        val fingerprint = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-01,Coffee,120.01,outflow,Wallet,Food"), ref).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
    }

    @Test
    fun nearDuplicate_differentDate_new() {
        val fingerprint = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-02,Coffee,120.00,outflow,Wallet,Food"), ref).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
    }

    @Test
    fun nearDuplicate_differentAccount_new() {
        val fingerprint = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-01,Coffee,120.00,outflow,Bank,Food"), ref).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
    }

    @Test
    fun nearDuplicate_differentCategory_new() {
        val fingerprint = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Travel"), ref).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
    }

    @Test
    fun nearDuplicate_differentNote_new() {
        val fingerprint = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(fingerprint to listOf(77L)))
        val row = previewOf(csv("2026-09-01,Coffee shop,120.00,outflow,Wallet,Food"), ref).rows[0]
        assertEquals(ImportRowState.NEW, row.state)
    }

    @Test
    fun externalReferenceParticipatesInFingerprint() {
        val withRef = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Coffee", "TX-9"
        )
        val ref = reference.copy(existingFingerprints = mapOf(withRef to listOf(5L)))

        val missingRef = previewOf(csv("2026-09-01,Coffee,120.00,outflow,Wallet,Food"), ref).rows[0]
        assertEquals(ImportRowState.NEW, missingRef.state)

        val matching = previewOf(
            csvWithRef("2026-09-01,Coffee,120.00,outflow,Wallet,Food,TX-9"),
            ref
        ).rows[0]
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, matching.state)
        assertEquals(5L, matching.duplicate!!.existingTransactionId)
    }

    @Test
    fun withinFileDuplicate_flagsLaterRow() {
        val preview = previewOf(
            csv(
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food",
                "2026-09-01,Coffee,120.00,outflow,Wallet,Food"
            )
        )
        assertEquals(ImportRowState.NEW, preview.rows[0].state)
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, preview.rows[1].state)
        assertEquals(DuplicateMatchType.EARLIER_ROW, preview.rows[1].duplicate!!.type)
        assertEquals(1, preview.rows[1].duplicate!!.earlierRowNumber)
        assertEquals(1, preview.newRows)
        assertEquals(1, preview.duplicateRows)
    }

    @Test
    fun previewCounts_areExact() {
        val withExisting = ImportFingerprint.of(
            "Wallet", dayStart("2026-09-01"), 12000L,
            TRANSACTION_DIRECTION_OUTFLOW, "Food", "Dup", null
        )
        val ref = reference.copy(existingFingerprints = mapOf(withExisting to listOf(9L)))
        val preview = previewOf(
            csv(
                "2026-09-01,Fresh,100.00,outflow,Wallet,Food",
                "2026-13-45,Broken date,100.00,outflow,Wallet,Food",
                "2026-09-01,Dup,120.00,outflow,Wallet,Food",
                "2026-09-01,Unmatched,50.00,outflow,Reserve,Food"
            ),
            ref
        )
        assertEquals(4, preview.totalRows)
        assertEquals(1, preview.invalidRows)
        assertEquals(3, preview.validRows)
        assertEquals(1, preview.duplicateRows)
        assertEquals(2, preview.newRows)
        assertEquals(2, preview.importableRows)
        assertEquals(listOf("Reserve"), preview.unmatchedAccountNames)
    }

    @Test
    fun missingDirectionColumn_rejectedWithReason() {
        val text = "date,description,amount,account,category\n2026-09-01,Coffee,100,Wallet,Food"
        val outcome = ImportEngine.buildPreview(CsvParser.parse(text), reference)
        assertTrue(outcome is PreviewOutcome.Rejected)
        assertTrue((outcome as PreviewOutcome.Rejected).reason.contains("direction"))
    }

    @Test
    fun missingHeaderRow_rejected() {
        val outcome = ImportEngine.buildPreview(CsvParser.parse(""), reference)
        assertTrue(outcome is PreviewOutcome.Rejected)
    }

    @Test
    fun headerOnly_emptyOutcome() {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse("date,description,amount,direction,account"),
            reference
        )
        assertTrue(outcome is PreviewOutcome.Empty)
    }

    @Test
    fun normalizedNames_matchCaseAndWhitespaceVariants() {
        val text = "date,description,amount,direction,account,category\n2026-09-01,Coffee,100,outflow,  wallet , FOOD"
        val row = previewOf(text).rows[0]
        assertEquals(1L, row.accountId)
        assertEquals(10L, row.categoryId)
        assertTrue(row.isImportable())
    }

    private fun csvWithRef(row: String): String =
        "date,description,amount,direction,account,category,external_ref\n$row"
}
