package com.prasbin.shadowmoney.data

import org.junit.Test
import org.junit.Assert.*

class BudgetCalendarTest {

    @Test
    fun monthKeyFormat_isYearMonth() {
        assertEquals("2026-09", BudgetCalendar.monthKey(2026, 9))
        assertEquals("2026-12", BudgetCalendar.monthKey(2026, 12))
        assertEquals("2027-01", BudgetCalendar.monthKey(2027, 1))
    }

    @Test
    fun monthStart_isFirstInstantOfKathmanduMonth() {
        val start = BudgetCalendar.monthStart("2026-09")
        val zoned = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(start),
            BudgetCalendar.KATHMANDU_ZONE
        )
        assertEquals(2026, zoned.year)
        assertEquals(9, zoned.monthValue)
        assertEquals(1, zoned.dayOfMonth)
        assertEquals(0, zoned.hour)
        assertEquals(0, zoned.minute)
        assertEquals(0, zoned.second)
        assertEquals(java.time.ZoneOffset.ofHoursMinutes(5, 45), zoned.offset)
    }

    @Test
    fun monthEndExclusive_isFirstInstantOfNextMonth() {
        val end = BudgetCalendar.monthEndExclusive("2026-09")
        val zoned = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(end),
            BudgetCalendar.KATHMANDU_ZONE
        )
        assertEquals(2026, zoned.year)
        assertEquals(10, zoned.monthValue)
        assertEquals(1, zoned.dayOfMonth)
    }

    @Test
    fun monthBoundary_transactionAtStartIncludedAtEndExcluded() {
        val monthKey = "2026-09"
        val start = BudgetCalendar.monthStart(monthKey)
        val end = BudgetCalendar.monthEndExclusive(monthKey)
        assertTrue(start in start until end)
        assertFalse(end in start until end)
        assertTrue(start - 1 !in start until end)
    }

    @Test
    fun yearBoundary_decemberToJanuary() {
        val decEnd = BudgetCalendar.monthEndExclusive("2026-12")
        val janStart = BudgetCalendar.monthStart("2027-01")
        assertEquals(decEnd, janStart)
        val zoned = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(decEnd),
            BudgetCalendar.KATHMANDU_ZONE
        )
        assertEquals(2027, zoned.year)
        assertEquals(1, zoned.monthValue)
    }

    @Test
    fun shiftMonth_navigatesMonthsDeterministically() {
        assertEquals("2026-08", BudgetCalendar.shiftMonth("2026-09", -1))
        assertEquals("2026-10", BudgetCalendar.shiftMonth("2026-09", 1))
        assertEquals("2027-01", BudgetCalendar.shiftMonth("2026-12", 1))
        assertEquals("2026-11", BudgetCalendar.shiftMonth("2026-12", -1))
    }

    @Test
    fun monthLabel_isHumanReadable() {
        assertEquals("September 2026", BudgetCalendar.monthLabel("2026-09"))
        assertEquals("January 2027", BudgetCalendar.monthLabel("2027-01"))
    }

    @Test
    fun currentMonthKey_matchesKathmanduNow() {
        val key = BudgetCalendar.currentMonthKey(1_800_000_000_000L)
        val zoned = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(1_800_000_000_000L),
            BudgetCalendar.KATHMANDU_ZONE
        )
        assertEquals(BudgetCalendar.monthKey(zoned.year, zoned.monthValue), key)
    }
}
