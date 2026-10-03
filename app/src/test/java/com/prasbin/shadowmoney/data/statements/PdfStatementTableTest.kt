package com.prasbin.shadowmoney.data.statements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfStatementTableTest {

    private val sanimaText = """
        Sanima Bank Limited
        Account Statement (synthetic test data)

        Date${'\t'}Description${'\t'}Debit${'\t'}Credit${'\t'}Balance
        2026-01-05${'\t'}Coffee Shop${'\t'}150.00${'\t'}${'\t'}10850.00
        2026-01-06${'\t'}Salary${'\t'}${'\t'}50000.00${'\t'}60850.00
        random footer line
    """.trimIndent()

    @Test
    fun parsesDebitAndCreditColumnsIntoRows() {
        val result = PdfStatementTable.build(sanimaText)
        assertNull(result.failure)
        assertEquals(2, result.rows.size)

        val debitRow = result.rows[0]
        assertEquals("2026-01-05", debitRow.date)
        assertEquals("Coffee Shop", debitRow.description)
        assertEquals("150.00", debitRow.amount)
        assertEquals("OUTFLOW", debitRow.direction)
        assertEquals("10850.00", debitRow.balance)

        val creditRow = result.rows[1]
        assertEquals("Salary", creditRow.description)
        assertEquals("50000.00", creditRow.amount)
        assertEquals("INCOME", creditRow.direction)
        assertEquals("60850.00", creditRow.balance)
    }

    @Test
    fun linesThatDoNotFitColumnsAreKeptForReviewNotDropped() {
        val result = PdfStatementTable.build(sanimaText)
        assertEquals(listOf("random footer line"), result.unparsedLines)
    }

    @Test
    fun leadingTitleLinesAreReportedAsNotesOnly() {
        val result = PdfStatementTable.build(sanimaText)
        assertTrue(result.notes.any { it.contains("header detected at line 4") })
        assertTrue(result.notes.any { it.contains("ignored") })
    }

    @Test
    fun missingHeaderFailsWithAmbiguousColumns() {
        val result = PdfStatementTable.build("just some text\nmore text\nnothing here")
        assertNotNull(result.failure)
        assertEquals(StatementFailureReason.AMBIGUOUS_COLUMNS, result.failure!!.reason)
    }

    @Test
    fun headerWithoutDescriptionIsRejectedNotGuessed() {
        val text = "Date\tAmount\tBalance\n2026-01-05\t150.00\t1000.00"
        val result = PdfStatementTable.build(text)
        assertNotNull(result.failure)
        assertEquals(StatementFailureReason.AMBIGUOUS_COLUMNS, result.failure!!.reason)
    }

    @Test
    fun headerWithoutAnyAmountSourceIsRejected() {
        val text = "Date\tDescription\tBalance\n2026-01-05\tCoffee\t1000.00"
        val result = PdfStatementTable.build(text)
        assertNotNull(result.failure)
        assertEquals(StatementFailureReason.AMBIGUOUS_COLUMNS, result.failure!!.reason)
    }

    @Test
    fun unsignedSingleAmountColumnLeavesDirectionEmptyForEngineReview() {
        val text = """
            Date${'\t'}Description${'\t'}Amount${'\t'}Balance
            2026-01-05${'\t'}Coffee${'\t'}150.00${'\t'}1000.00
        """.trimIndent()
        val result = PdfStatementTable.build(text)
        assertNull(result.failure)
        assertEquals(1, result.rows.size)
        assertEquals("", result.rows[0].direction)
    }

    @Test
    fun signedAmountColumnReadsDirectionFromSign() {
        val text = """
            Date${'\t'}Description${'\t'}Amount${'\t'}Balance
            2026-01-05${'\t'}Coffee${'\t'}-150.00${'\t'}1000.00
            2026-01-06${'\t'}Refund${'\t'}+200.00${'\t'}1200.00
        """.trimIndent()
        val result = PdfStatementTable.build(text)
        assertEquals(2, result.rows.size)
        assertEquals("OUTFLOW", result.rows[0].direction)
        assertEquals("INCOME", result.rows[1].direction)
    }

    @Test
    fun combinedDebitCreditCellIsNotGuessed() {
        val text = "Date\tDescription\tDebit/Credit\tBalance\n2026-01-05\tCoffee\t150.00\t1000.00"
        val result = PdfStatementTable.build(text)
        assertNotNull(result.failure)
        assertEquals(StatementFailureReason.AMBIGUOUS_COLUMNS, result.failure!!.reason)
    }

    @Test
    fun repeatedHeaderLinesInsideDataAreSkippedWithNote() {
        val text = """
            Date${'\t'}Description${'\t'}Debit${'\t'}Credit${'\t'}Balance
            2026-01-05${'\t'}Coffee${'\t'}150.00${'\t'}${'\t'}1000.00
            Date${'\t'}Description${'\t'}Debit${'\t'}Credit${'\t'}Balance
            2026-01-06${'\t'}Tea${'\t'}80.00${'\t'}${'\t'}920.00
        """.trimIndent()
        val result = PdfStatementTable.build(text)
        assertNull(result.failure)
        assertEquals(2, result.rows.size)
        assertTrue(result.notes.any { it.contains("repeated header") })
    }

    @Test
    fun headerOnlyYieldsNoTransactionsDetected() {
        val text = "Date\tDescription\tDebit\tCredit\tBalance"
        val result = PdfStatementTable.build(text)
        assertNotNull(result.failure)
        assertEquals(StatementFailureReason.NO_TRANSACTIONS_DETECTED, result.failure!!.reason)
    }

    @Test
    fun referenceColumnIsCarriedThrough() {
        val text = """
            Date${'\t'}Description${'\t'}Debit${'\t'}Reference${'\t'}Balance
            2026-01-05${'\t'}Transfer${'\t'}150.00${'\t'}TX-9${'\t'}1000.00
        """.trimIndent()
        val result = PdfStatementTable.build(text)
        assertEquals("TX-9", result.rows[0].externalRef)
    }

    @Test
    fun trailingEmptyColumnIsPaddedNotDropped() {
        val text = """
            Date${'\t'}Description${'\t'}Debit${'\t'}Credit${'\t'}Balance
            2026-01-05${'\t'}Coffee${'\t'}150.00${'\t'}${'\t'}
        """.trimIndent()
        val result = PdfStatementTable.build(text)
        assertNull(result.failure)
        assertEquals(1, result.rows.size)
        assertEquals("150.00", result.rows[0].amount)
        assertNull(result.rows[0].balance)
    }

    @Test
    fun toCsvDocumentProducesEngineCompatibleHeaderAndRows() {
        val result = PdfStatementTable.build(sanimaText)
        val document = PdfStatementTable.toCsvDocument(result.rows)
        assertEquals(
            listOf("date", "description", "amount", "direction", "account", "balance"),
            document.header
        )
        assertEquals(2, document.records.size)
        assertEquals(6, document.records[0].values.size)
        assertEquals("", document.records[0].values[4]) // account filled at import
    }
}
