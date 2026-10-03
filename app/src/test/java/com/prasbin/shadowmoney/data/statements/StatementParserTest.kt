package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.imports.ImportEngine
import com.prasbin.shadowmoney.data.imports.ImportPreview
import com.prasbin.shadowmoney.data.imports.ImportReference
import com.prasbin.shadowmoney.data.imports.PreviewOutcome
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementParserTest {

    private val reference = ImportReference(
        accounts = listOf(Account(id = 1L, name = "Wallet")),
        categories = listOf(Category(id = 1L, name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)),
        existingFingerprints = emptyMap()
    )

    private fun previewOf(document: com.prasbin.shadowmoney.data.imports.CsvDocument): ImportPreview {
        val outcome = ImportEngine.buildPreview(document, reference, defaultAccountId = 1L)
        assertTrue("expected Ready but was $outcome", outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    @Test
    fun csvParserProducesParsedOutcomeWithContentDetection() {
        val input = StatementInput.TextDocument(
            text = "Date,Description,Amount,Direction,Account\n" +
                "2026-01-05,Sanima account charge,120.00,outflow,Wallet\n",
            documentName = "statement.csv"
        )
        val outcome = CsvStatementParser().parse(input)
        assertTrue(outcome is StatementParseOutcome.Parsed)
        val parsed = (outcome as StatementParseOutcome.Parsed).result
        assertEquals(StatementFormat.CSV, parsed.format)
        assertEquals(StatementSource.SANIMA, parsed.detection.candidate)
        assertEquals(1, parsed.document.records.size)
    }

    @Test
    fun csvParserLeavesHeaderErrorsToTheEngine() {
        val input = StatementInput.TextDocument(text = "", documentName = "empty.csv")
        val outcome = CsvStatementParser().parse(input)
        assertTrue(outcome is StatementParseOutcome.Parsed)
        val parsed = (outcome as StatementParseOutcome.Parsed).result
        assertTrue(parsed.document.headerError != null)
    }

    @Test
    fun registrySelectsParserByFormat() {
        assertTrue(StatementParserRegistry.parserFor(StatementFormat.PDF) is PdfStatementParser)
        assertTrue(StatementParserRegistry.parserFor(StatementFormat.CSV) is CsvStatementParser)
        assertTrue(StatementParserRegistry.parserFor(StatementFormat.PASTED) is CsvStatementParser)
    }

    @Test
    fun pdfStatementFeedsEngineAndImportBecomesRealRows() {
        val content = """
            BT 72 760 Td (Sanima Bank Limited) Tj 0 -20 Td (Synthetic statement) Tj
            0 -28 Td (Date) Tj 90 0 Td (Description) Tj 130 0 Td (Debit) Tj
            80 0 Td (Credit) Tj 80 0 Td (Balance) Tj
            0 -16 Td (2026-01-05) Tj 90 0 Td (Coffee Shop) Tj 130 0 Td (150.00) Tj
            80 0 Td () Tj 80 0 Td (10850.00) Tj
            0 -16 Td (2026-01-06) Tj 90 0 Td (Salary) Tj 130 0 Td () Tj
            80 0 Td (50000.00) Tj 80 0 Td (60850.00) Tj
            ET
        """.trimIndent()

        val pdf = SyntheticPdf.flateCompressed(content)
        val outcome = StatementParserRegistry.parserFor(StatementFormat.PDF)
            .parse(StatementInput.PdfDocument(bytes = pdf, documentName = "statement.pdf"))

        assertTrue("expected Parsed but was $outcome", outcome is StatementParseOutcome.Parsed)
        val parsed = (outcome as StatementParseOutcome.Parsed).result
        assertEquals(StatementFormat.PDF, parsed.format)
        assertEquals(StatementSource.SANIMA, parsed.detection.candidate)

        val preview = previewOf(parsed.document)
        assertEquals(2, preview.totalRows)
        assertEquals(0, preview.invalidRows)
        assertEquals(2, preview.importableRows)
        assertEquals(1_085_000L, preview.rows[0].balanceMinor)
        assertEquals(6_085_000L, preview.rows[1].balanceMinor)
        assertEquals("10850.00", preview.rows[0].rawBalance)
    }

    @Test
    fun pdfWithoutProviderMarkersStaysUnknownSource() {
        val content = """
            BT 72 760 Td (Generic Export) Tj
            0 -24 Td (Date) Tj 90 0 Td (Description) Tj 90 0 Td (Debit) Tj
            0 -16 Td (2026-01-05) Tj 90 0 Td (Coffee) Tj 90 0 Td (150.00) Tj
            ET
        """.trimIndent()
        val pdf = SyntheticPdf.simple(content)
        val outcome = StatementParserRegistry.parserFor(StatementFormat.PDF)
            .parse(StatementInput.PdfDocument(bytes = pdf, documentName = "generic.pdf"))
        assertTrue(outcome is StatementParseOutcome.Parsed)
        val parsed = (outcome as StatementParseOutcome.Parsed).result
        assertEquals(StatementSource.UNKNOWN, parsed.detection.candidate)
    }

    @Test
    fun encryptedPdfFailsWithSafePasswordFreeMessage() {
        val pdf = SyntheticPdf.simple("BT (data) Tj ET", encrypt = true)
        val outcome = StatementParserRegistry.parserFor(StatementFormat.PDF)
            .parse(StatementInput.PdfDocument(bytes = pdf, documentName = "locked.pdf"))
        assertTrue(outcome is StatementParseOutcome.Failed)
        val failure = (outcome as StatementParseOutcome.Failed).failure
        assertEquals(StatementFailureReason.ENCRYPTED_PDF, failure.reason)
        assertTrue(failure.userMessage.contains("never asks"))
    }

    @Test
    fun engineRejectsPdfRowsWhenNoAccountIsAssigned() {
        val content = """
            BT 72 760 Td (Date) Tj 90 0 Td (Description) Tj 130 0 Td (Debit) Tj
            0 -16 Td (2026-01-05) Tj 90 0 Td (Coffee) Tj 130 0 Td (150.00) Tj ET
        """.trimIndent()
        val pdf = SyntheticPdf.simple(content)
        val outcome = StatementParserRegistry.parserFor(StatementFormat.PDF)
            .parse(StatementInput.PdfDocument(bytes = pdf, documentName = "noacct.pdf"))
        assertTrue(outcome is StatementParseOutcome.Parsed)
        val document = (outcome as StatementParseOutcome.Parsed).result.document

        val noDefault = ImportEngine.buildPreview(document, reference)
        assertTrue(noDefault is PreviewOutcome.Ready)
        val preview = (noDefault as PreviewOutcome.Ready).preview
        assertEquals(1, preview.invalidRows)
        assertTrue(preview.rows[0].invalidReasons.any { it.contains("Missing account") })
    }
}
