package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.BudgetCalendar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class ImportDateTest {

    private fun ok(text: String): Long {
        val result = ImportDate.parse(text)
        assertTrue("expected Ok for '$text' but was $result", result is DateParseResult.Ok)
        return (result as DateParseResult.Ok).epochMillis
    }

    private fun invalid(text: String): String {
        val result = ImportDate.parse(text)
        assertTrue("expected Invalid for '$text' but was $result", result is DateParseResult.Invalid)
        return (result as DateParseResult.Invalid).reason
    }

    @Test
    fun isoDate_parsedAsKathmanduStartOfDay() {
        val expected = LocalDate.of(2026, 9, 25)
            .atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
        assertEquals(expected, ok("2026-09-25"))
    }

    @Test
    fun slashDate_sameAsDashDate() {
        assertEquals(ok("2026-09-25"), ok("2026/09/25"))
    }

    @Test
    fun dateTime_withSpace_parsedInKathmandu() {
        val expected = LocalDateTime.of(2026, 9, 25, 13, 45, 0)
            .atZone(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
        assertEquals(expected, ok("2026-09-25 13:45:00"))
    }

    @Test
    fun dateTime_withT_parsed() {
        assertEquals(ok("2026-09-25 13:45:00"), ok("2026-09-25T13:45:00"))
    }

    @Test
    fun dateTime_withoutSeconds_parsed() {
        val expected = LocalDateTime.of(2026, 9, 25, 13, 45, 0)
            .atZone(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
        assertEquals(expected, ok("2026-09-25T13:45"))
        assertEquals(expected, ok("2026-09-25 13:45"))
    }

    @Test
    fun impossibleCalendarDates_rejected() {
        invalid("2026-02-30")
        invalid("2026-13-01")
        invalid("2026-00-10")
        invalid("2026-01-32")
    }

    @Test
    fun ambiguousDayFirstFormat_rejectedExplicitly() {
        val reason = invalid("01/02/2026")
        assertTrue(reason.contains("Ambiguous"))
        val reason2 = invalid("01-02-2026")
        assertTrue(reason2.contains("Ambiguous"))
    }

    @Test
    fun timezoneQualifiedTimestamps_rejectedExplicitly() {
        val reason = invalid("2026-01-02T03:04:05Z")
        assertTrue(reason.contains("Asia/Kathmandu"))
        val reason2 = invalid("2026-01-02T03:04:00+05:45")
        assertTrue(reason2.contains("Asia/Kathmandu"))
        val reason3 = invalid("2026-01-02T03:04:00-08:00")
        assertTrue(reason3.contains("Asia/Kathmandu"))
    }

    @Test
    fun unsupportedFormat_rejected() {
        val reason = invalid("Sep 25, 2026")
        assertTrue(reason.contains("Unsupported"))
        invalid("25.09.2026")
        invalid("20260925")
    }

    @Test
    fun blankDate_rejectedAsRequired() {
        val reason = invalid("")
        assertTrue(reason.contains("required"))
        invalid("   ")
    }

    @Test
    fun result_isIndependentOfDeviceTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val expected = LocalDate.of(2026, 9, 25)
                .atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
            assertEquals(expected, ok("2026-09-25"))

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(expected, ok("2026-09-25"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun kathmanduMidnight_differsFromUtcMidnight() {
        val kathmandu = ok("2026-09-25")
        val utcMidnight = LocalDate.of(2026, 9, 25)
            .atStartOfDay(TimeZone.getTimeZone("UTC").toZoneId()).toInstant().toEpochMilli()
        assertNotEquals(utcMidnight, kathmandu)
        // Kathmandu is UTC+05:45 → local midnight is 18:15 of the previous day UTC.
        assertEquals(5L * 60 + 45, (utcMidnight - kathmandu) / 60_000L)
    }

    @Test
    fun dateBoundary_acrossYearEnd_supported() {
        assertEquals(ok("2026-12-31"), ok("2026/12/31"))
        val newYear = ok("2027-01-01")
        assertTrue(newYear > ok("2026-12-31"))
    }
}
