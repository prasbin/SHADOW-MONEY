package com.prasbin.shadowmoney.data.statements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementSourceDetectorTest {

    @Test
    fun detectsSanimaFromDocumentContent() {
        val detection = StatementSourceDetector.detect(
            "SANIMA BANK LIMITED\nAccount Statement\nDate Description Amount"
        )
        assertEquals(StatementSource.SANIMA, detection.candidate)
        assertTrue(detection.evidence.contains("Sanima"))
    }

    @Test
    fun detectsGlobalImeFromDocumentContent() {
        val detection = StatementSourceDetector.detect(
            "Global IME Bank\nStatement of account"
        )
        assertEquals(StatementSource.GLOBAL_IME, detection.candidate)
    }

    @Test
    fun detectsGlobalSmartMarkerAsGlobalIme() {
        val detection = StatementSourceDetector.detect("Global Smart Plus statement")
        assertEquals(StatementSource.GLOBAL_IME, detection.candidate)
    }

    @Test
    fun detectsEsewaFromDocumentContent() {
        val detection = StatementSourceDetector.detect("eSewa wallet statement for the month")
        assertEquals(StatementSource.ESEWA, detection.candidate)
    }

    @Test
    fun noMarkersYieldsUnknown() {
        val detection = StatementSourceDetector.detect("Some generic CSV export\nDate,Amount")
        assertEquals(StatementSource.UNKNOWN, detection.candidate)
        assertTrue(detection.evidence.contains("no provider markers"))
    }

    @Test
    fun multipleProviderMarkersYieldUnknownAndAskUser() {
        val detection = StatementSourceDetector.detect(
            "Sanima partner sheet for Global IME reconciliation"
        )
        assertEquals(StatementSource.UNKNOWN, detection.candidate)
        assertTrue(detection.evidence.contains("multiple provider names"))
    }

    @Test
    fun markerInsideStatementTableRowStillCountsAsContentEvidence() {
        // Evidence is only ever a suggestion — the UI must still confirm it.
        val detection = StatementSourceDetector.detect(
            "Date\tDescription\tAmount\n2026-01-01\tTransfer to sanima friend\t100.00"
        )
        assertEquals(StatementSource.SANIMA, detection.candidate)
    }

    @Test
    fun filenameNeverInfluencesDetection_apiTakesOnlyText() {
        // Detection input is the document text alone; a file named like a
        // provider but containing no markers stays UNKNOWN.
        val detection = StatementSourceDetector.detect("random bytes without names")
        assertEquals(StatementSource.UNKNOWN, detection.candidate)
        assertFalse(detection.evidence.contains("filename"))
    }

    @Test
    fun sourceStorageRoundtrip() {
        assertEquals(StatementSource.SANIMA, StatementSource.fromStorage("SANIMA"))
        assertEquals(StatementSource.GLOBAL_IME, StatementSource.fromStorage("GLOBAL_IME"))
        assertEquals(StatementSource.ESEWA, StatementSource.fromStorage("ESEWA"))
        assertEquals(StatementSource.UNKNOWN, StatementSource.fromStorage("UNKNOWN"))
        assertEquals(StatementSource.UNKNOWN, StatementSource.fromStorage("something-else"))
        assertEquals(StatementSource.UNKNOWN, StatementSource.fromStorage(null))
    }
}
