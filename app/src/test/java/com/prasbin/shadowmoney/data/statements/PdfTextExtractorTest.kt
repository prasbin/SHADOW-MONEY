package com.prasbin.shadowmoney.data.statements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextExtractorTest {

    private fun textOf(outcome: PdfExtractOutcome): String {
        assertTrue("expected Text but was $outcome", outcome is PdfExtractOutcome.Text)
        return (outcome as PdfExtractOutcome.Text).text
    }

    private fun failureOf(outcome: PdfExtractOutcome): StatementFailureReason {
        assertTrue("expected Failed but was $outcome", outcome is PdfExtractOutcome.Failed)
        return (outcome as PdfExtractOutcome.Failed).failure.reason
    }

    @Test
    fun extractsPlainTextFromSimplePdf() {
        val pdf = SyntheticPdf.simple("BT\n72 720 Td\n(Hello Statement) Tj\nET")
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("Hello Statement"))
    }

    @Test
    fun sameLineColumnMovesBecomeTabsPreservingEmptyCells() {
        val pdf = SyntheticPdf.simple(
            "BT 72 720 Td (2026-01-05) Tj 80 0 Td (Coffee) Tj 60 0 Td (150.00) Tj ET"
        )
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("2026-01-05\tCoffee\t150.00"))
    }

    @Test
    fun verticalMovesSeparateLines() {
        val pdf = SyntheticPdf.simple(
            "BT 72 720 Td (Line1) Tj 0 -14 Td (Line2) Tj ET"
        )
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("Line1\nLine2"))
    }

    @Test
    fun tjArrayKerningGapBecomesSpace() {
        val pdf = SyntheticPdf.simple("BT 72 720 Tj [(Pre) -200 (Post)] TJ ET")
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("Pre Post"))
    }

    @Test
    fun literalStringEscapesAreDecoded() {
        val pdf = SyntheticPdf.simple("BT (Line\\nBreak \\(ok\\)) Tj ET")
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("Line\nBreak (ok)"))
    }

    @Test
    fun flateCompressedContentIsInflated() {
        val pdf = SyntheticPdf.flateCompressed("BT 72 720 Td (Compressed Rows) Tj ET")
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue(text.contains("Compressed Rows"))
    }

    @Test
    fun encryptedPdfFailsSafelyWithoutAskingForPasswords() {
        val pdf = SyntheticPdf.simple("BT (secret) Tj ET", encrypt = true)
        val reason = failureOf(PdfTextExtractor.extract(pdf))
        assertEquals(StatementFailureReason.ENCRYPTED_PDF, reason)
        assertTrue(StatementFailureReason.ENCRYPTED_PDF.userMessage.contains("never asks"))
    }

    @Test
    fun malformedPdfIsRejected() {
        val reason = failureOf(PdfTextExtractor.extract(SyntheticPdf.notAPdf()))
        assertEquals(StatementFailureReason.MALFORMED_PDF, reason)
    }

    @Test
    fun imageOnlyPdfReportsNoTextInsteadOfGuessing() {
        val reason = failureOf(PdfTextExtractor.extract(SyntheticPdf.imageOnly()))
        assertEquals(StatementFailureReason.NO_TEXT_EXTRACTED, reason)
    }

    @Test
    fun oversizedFileIsRejected() {
        val oversized = ByteArray(PdfTextExtractor.MAX_PDF_BYTES + 1) { 0x41 }
        oversized[0] = '%'.code.toByte()
        oversized[1] = 'P'.code.toByte()
        oversized[2] = 'D'.code.toByte()
        oversized[3] = 'F'.code.toByte()
        val reason = failureOf(PdfTextExtractor.extract(oversized))
        assertEquals(StatementFailureReason.FILE_TOO_LARGE, reason)
    }

    @Test
    fun emptyFileIsRejected() {
        val reason = failureOf(PdfTextExtractor.extract(ByteArray(0)))
        assertEquals(StatementFailureReason.EMPTY_DOCUMENT, reason)
    }

    @Test
    fun toUnicodeCMapIsAppliedWhenConsistent() {
        val pdf = SyntheticPdf.simple(
            content = "BT (A) Tj ET",
            extraObjects = SyntheticPdf.cmapObject(SyntheticPdf.simpleToUnicodeCmap())
        )
        val text = textOf(PdfTextExtractor.extract(pdf))
        assertTrue("expected mapped Z but was: $text", text.contains("Z"))
        assertTrue(!text.contains("A"))
    }

    @Test
    fun binaryGarbageInsideTextOperatorsFailsQualityFilter() {
        val content = "BT (\u0001\u0002\u0003\u0004\u0005\u0006) Tj ET"
        val reason = failureOf(PdfTextExtractor.extract(SyntheticPdf.simple(content)))
        assertEquals(StatementFailureReason.NO_TEXT_EXTRACTED, reason)
    }

    @Test
    fun nonPdfWithPdfMagicMissingIsRejectedEarly() {
        val bytes = ByteArray(100) { 0x20 }
        val reason = failureOf(PdfTextExtractor.extract(bytes))
        assertEquals(StatementFailureReason.MALFORMED_PDF, reason)
    }
}
