package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import org.junit.Assert.*
import org.junit.Test

class ImportAmountTest {

    private fun ok(text: String): Long {
        val result = ImportAmount.parse(text)
        assertTrue("expected Ok for '$text' but was $result", result is AmountParseResult.Ok)
        return (result as AmountParseResult.Ok).minorUnits
    }

    private fun invalid(text: String): String {
        val result = ImportAmount.parse(text)
        assertTrue("expected Invalid for '$text' but was $result", result is AmountParseResult.Invalid)
        return (result as AmountParseResult.Invalid).reason
    }

    @Test
    fun integerAmount_twoDecimalPlaces() {
        assertEquals(12300L, ok("123"))
        assertEquals(12345L, ok("123.45"))
        assertEquals(100L, ok("1.00"))
        assertEquals(5L, ok("0.05"))
    }

    @Test
    fun positiveAndNegativeSigns() {
        assertEquals(50000L, ok("+500"))
        assertEquals((-50000L), ok("-500"))
        assertEquals((-12345L), ok("-123.45"))
    }

    @Test
    fun zero_parsesToZeroMinorUnits() {
        assertEquals(0L, ok("0"))
        assertEquals(0L, ok("0.00"))
        assertEquals(0L, ok("-0.00"))
    }

    @Test
    fun fewerThanTwoDecimals_zeroPadded() {
        assertEquals(50L, ok("0.5"))
        assertEquals(12340L, ok("123.4"))
    }

    @Test
    fun tooManyDecimals_rejectedNeverRounded() {
        val reason = invalid("123.456")
        assertTrue(reason.contains("more than two decimal places"))
        val reason2 = invalid("0.005")
        assertTrue(reason2.contains("more than two decimal places"))
    }

    @Test
    fun thousandsSeparators_validGrouping() {
        assertEquals(123456L, ok("1,234.56"))
        assertEquals(100000L, ok("1,000"))
        assertEquals(123456789L, ok("1,234,567.89"))
    }

    @Test
    fun invalidGrouping_rejected() {
        invalid("1,23")
        invalid("12,34")
        invalid("1,2345")
    }

    @Test
    fun whitespace_trimmed() {
        assertEquals(5000L, ok("  50.00  "))
    }

    @Test
    fun nonNumeric_rejected() {
        invalid("abc")
        invalid("12a")
        invalid("1.2.3")
        invalid("-")
        invalid("+")
    }

    @Test
    fun missingAmount_rejected() {
        val reason = invalid("")
        assertTrue(reason.contains("required"))
        invalid("   ")
    }

    @Test
    fun decimalWithoutLeadingDigit_rejected() {
        invalid(".5")
        invalid("5.")
    }

    @Test
    fun overflow_rejected() {
        invalid("99999999999999999999")
        invalid("9223372036854775807.99")
        invalid("999999999999999999,999")
    }

    @Test
    fun direction_incomeAliases() {
        for (alias in listOf("income", "INCOME", " Credit ", "cr", "DEPOSIT", "received", "in")) {
            val result = ImportAmount.parseDirection(alias)
            assertTrue("expected income for '$alias'", result is DirectionParseResult.Ok)
            assertEquals(TRANSACTION_DIRECTION_INCOME, (result as DirectionParseResult.Ok).direction)
        }
    }

    @Test
    fun direction_outflowAliases() {
        for (alias in listOf("outflow", "OUTFLOW", "debit", "Dr", "withdrawal", "expense", "spent", "out")) {
            val result = ImportAmount.parseDirection(alias)
            assertTrue("expected outflow for '$alias'", result is DirectionParseResult.Ok)
            assertEquals(TRANSACTION_DIRECTION_OUTFLOW, (result as DirectionParseResult.Ok).direction)
        }
    }

    @Test
    fun direction_unsupported_rejected() {
        val result = ImportAmount.parseDirection("wire")
        assertTrue(result is DirectionParseResult.Invalid)
        assertTrue((result as DirectionParseResult.Invalid).reason.contains("Unsupported"))
    }

    @Test
    fun direction_missing_rejected() {
        val result = ImportAmount.parseDirection("  ")
        assertTrue(result is DirectionParseResult.Invalid)
    }

    @Test
    fun direction_normalizesSeparators() {
        val result = ImportAmount.parseDirection("in come")
        assertTrue(result is DirectionParseResult.Ok)
    }
}
