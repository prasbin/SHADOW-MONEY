package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportEngineStatementTest {

    private val reference = ImportReference(
        accounts = listOf(Account(id = 1L, name = "Wallet")),
        categories = listOf(
            Category(id = 1L, name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        ),
        existingFingerprints = emptyMap()
    )

    private val header = "date,description,amount,direction,account,balance"

    private fun preview(
        text: String,
        reference: ImportReference = this.reference,
        defaultAccountId: Long? = null
    ): ImportPreview {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse(text),
            reference,
            defaultAccountId = defaultAccountId
        )
        assertTrue("expected Ready but was $outcome", outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    @Test
    fun defaultAccountAssignsRowsWithEmptyAccountCell() {
        val rows = preview("$header\n2026-01-05,Coffee,150.00,outflow,,10850.00", defaultAccountId = 1L)
        assertEquals(1, rows.importableRows)
        assertEquals(1L, rows.rows[0].accountId)
        assertNull(rows.rows[0].unmatchedAccountName)
    }

    @Test
    fun withoutDefaultAccountEmptyCellStaysVisibleInvalid() {
        val rows = preview("$header\n2026-01-05,Coffee,150.00,outflow,,")
        assertEquals(0, rows.importableRows)
        assertEquals(1, rows.invalidRows)
        assertTrue(rows.rows[0].invalidReasons.any { it.contains("Missing account") })
    }

    @Test
    fun validBalanceIsParsedIntoMinorUnits() {
        val rows = preview("$header\n2026-01-05,Coffee,150.00,outflow,Wallet,10850.00")
        assertEquals(1_085_000L, rows.rows[0].balanceMinor)
        assertEquals("10850.00", rows.rows[0].rawBalance)
    }

    @Test
    fun malformedBalanceCellRejectsRowWithExplicitReason() {
        val rows = preview("$header\n2026-01-05,Coffee,150.00,outflow,Wallet,not-a-number")
        assertEquals(0, rows.importableRows)
        assertEquals(1, rows.invalidRows)
        assertTrue(
            rows.rows[0].invalidReasons.any { it.startsWith("Balance value could not be read") }
        )
    }

    @Test
    fun absentBalanceColumnLeavesBalanceUnclaimed() {
        val text = "date,description,amount,direction,account\n2026-01-05,Coffee,150.00,outflow,Wallet"
        val rows = preview(text)
        assertNull(rows.rows[0].balanceMinor)
        assertNull(rows.rows[0].rawBalance)
    }

    @Test
    fun emptyAccountRowFingerprintsUnderResolvedAccountName() {
        val text = "$header\n2026-01-05,Coffee,150.00,outflow,,"
        val first = preview(text, defaultAccountId = 1L).rows[0]
        val fingerprint = ImportFingerprint.of(
            accountName = "Wallet",
            timestamp = first.timestamp!!,
            amountMinor = first.amountMinor!!,
            direction = first.direction!!,
            categoryName = null,
            note = "Coffee",
            externalRef = null
        )

        val second = preview(
            text,
            reference = reference.copy(
                existingFingerprints = mapOf(fingerprint to listOf(77L))
            ),
            defaultAccountId = 1L
        ).rows[0]
        assertEquals(ImportRowState.POSSIBLE_DUPLICATE, second.state)
        assertEquals(DuplicateMatchType.EXISTING_TRANSACTION, second.duplicate!!.type)
        assertEquals(77L, second.duplicate!!.existingTransactionId)
    }

    @Test
    fun pdfStyleEmptyDirectionIsRejectedForReviewNeverGuessed() {
        val text = "$header\n2026-01-05,Coffee,150.00,,Wallet,10850.00"
        val rows = preview(text)
        assertEquals(1, rows.invalidRows)
        assertTrue(rows.rows[0].invalidReasons.any { it.contains("Direction is required") })
    }

    @Test
    fun duplicateRowsInSameFileAreFlaggedNotDuplicated() {
        val text = "$header\n" +
            "2026-01-05,Coffee,150.00,outflow,Wallet,\n" +
            "2026-01-05,Coffee,150.00,outflow,Wallet,"
        val rows = preview(text)
        assertEquals(2, rows.totalRows)
        assertEquals(1, rows.duplicateRows)
        assertEquals(
            DuplicateMatchType.EARLIER_ROW,
            rows.rows[1].duplicate!!.type
        )
    }
}
