package com.prasbin.shadowmoney.assistant

import com.prasbin.shadowmoney.data.BudgetCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

class AssistantTimeTest {

    // Wednesday 2026-09-23 15:30 Kathmandu.
    private val wednesday: ZonedDateTime =
        ZonedDateTime.of(2026, 9, 23, 15, 30, 0, 0, BudgetCalendar.KATHMANDU_ZONE)
    private val wednesdayMillis: Long = wednesday.toInstant().toEpochMilli()

    private fun startOfDay(day: ZonedDateTime): Long =
        day.toLocalDate().atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()

    @Test
    fun timezone_isKathmanduUtcPlus545() {
        assertEquals(ZoneOffset.ofHoursMinutes(5, 45), wednesday.offset)
        assertEquals(
            BudgetCalendar.KATHMANDU_ZONE,
            AssistantTime.startOfDayKathmandu(wednesdayMillis).zone
        )
    }

    @Test
    fun today_rangeIsOneFullDayStartingAtMidnight() {
        val range = AssistantTime.resolve(AssistantPeriod.TODAY, wednesdayMillis)!!
        assertEquals(startOfDay(wednesday), range.startInclusive)
        assertEquals(startOfDay(wednesday) + 86_400_000L, range.endExclusive)
        assertEquals("today", range.label)
    }

    @Test
    fun yesterday_rangeIsTheDayBeforeToday() {
        val range = AssistantTime.resolve(AssistantPeriod.YESTERDAY, wednesdayMillis)!!
        assertEquals(startOfDay(wednesday) - 86_400_000L, range.startInclusive)
        assertEquals(startOfDay(wednesday), range.endExclusive)
        assertEquals("yesterday", range.label)
    }

    @Test
    fun thisWeek_startsOnMondayAndSpansSevenDays() {
        val range = AssistantTime.resolve(AssistantPeriod.THIS_WEEK, wednesdayMillis)!!
        val expectedMonday = wednesday
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay(BudgetCalendar.KATHMANDU_ZONE)
            .toInstant().toEpochMilli()
        assertEquals(expectedMonday, range.startInclusive)
        assertEquals(7L * 86_400_000L, range.endExclusive - range.startInclusive)
        assertEquals("this week", range.label)
        assertEquals(DayOfWeek.MONDAY, ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(range.startInclusive),
            BudgetCalendar.KATHMANDU_ZONE
        ).dayOfWeek)
    }

    @Test
    fun lastWeek_isTheSevenDaysBeforeThisWeek() {
        val thisWeek = AssistantTime.resolve(AssistantPeriod.THIS_WEEK, wednesdayMillis)!!
        val lastWeek = AssistantTime.resolve(AssistantPeriod.LAST_WEEK, wednesdayMillis)!!
        assertEquals(thisWeek.startInclusive - 7L * 86_400_000L, lastWeek.startInclusive)
        assertEquals(thisWeek.startInclusive, lastWeek.endExclusive)
        assertEquals("last week", lastWeek.label)
    }

    @Test
    fun weekBoundary_questionAtMondayMidnightBelongsToNewWeek() {
        // 2026-09-28 is a Monday: a question asked then belongs to the week
        // starting that day, not the previous one.
        val mondayMorning: Long = ZonedDateTime
            .of(2026, 9, 28, 0, 30, 0, 0, BudgetCalendar.KATHMANDU_ZONE)
            .toInstant().toEpochMilli()
        val range = AssistantTime.resolve(AssistantPeriod.THIS_WEEK, mondayMorning)!!
        assertEquals(
            ZonedDateTime.of(2026, 9, 28, 0, 0, 0, 0, BudgetCalendar.KATHMANDU_ZONE)
                .toInstant().toEpochMilli(),
            range.startInclusive
        )
        assertTrue(mondayMorning >= range.startInclusive)
        assertTrue(mondayMorning < range.endExclusive)
    }

    @Test
    fun thisMonth_matchesBudgetCalendarExactly() {
        val key = BudgetCalendar.currentMonthKey(wednesdayMillis)
        val range = AssistantTime.resolve(AssistantPeriod.THIS_MONTH, wednesdayMillis)!!
        assertEquals(BudgetCalendar.monthStart(key), range.startInclusive)
        assertEquals(BudgetCalendar.monthEndExclusive(key), range.endExclusive)
        assertEquals("this month", range.label)
        assertTrue(range.startInclusive <= wednesdayMillis)
        assertTrue(wednesdayMillis < range.endExclusive)
    }

    @Test
    fun lastMonth_isShiftedOneMonthBack() {
        val current = BudgetCalendar.currentMonthKey(wednesdayMillis)
        val previous = BudgetCalendar.shiftMonth(current, -1)
        val range = AssistantTime.resolve(AssistantPeriod.LAST_MONTH, wednesdayMillis)!!
        assertEquals(BudgetCalendar.monthStart(previous), range.startInclusive)
        assertEquals(BudgetCalendar.monthEndExclusive(previous), range.endExclusive)
        assertEquals("last month", range.label)
        assertTrue(wednesdayMillis >= range.endExclusive)
    }

    @Test
    fun recent_hasNoRange() {
        assertNull(AssistantTime.resolve(AssistantPeriod.RECENT, wednesdayMillis))
    }

    @Test
    fun budgetMonthKey_defaultsToCurrentAndShiftsForLastMonth() {
        val current = BudgetCalendar.currentMonthKey(wednesdayMillis)
        assertEquals(current, AssistantTime.budgetMonthKey(null, wednesdayMillis))
        assertEquals(current, AssistantTime.budgetMonthKey(AssistantPeriod.THIS_MONTH, wednesdayMillis))
        assertEquals(current, AssistantTime.budgetMonthKey(AssistantPeriod.TODAY, wednesdayMillis))
        assertEquals(
            BudgetCalendar.shiftMonth(current, -1),
            AssistantTime.budgetMonthKey(AssistantPeriod.LAST_MONTH, wednesdayMillis)
        )
    }

    @Test
    fun budgetMonthLabel_usesBudgetCalendarLabel() {
        val current = BudgetCalendar.currentMonthKey(wednesdayMillis)
        assertEquals(
            BudgetCalendar.monthLabel(current),
            AssistantTime.budgetMonthLabel(null, wednesdayMillis)
        )
        assertEquals(
            BudgetCalendar.monthLabel(BudgetCalendar.shiftMonth(current, -1)),
            AssistantTime.budgetMonthLabel(AssistantPeriod.LAST_MONTH, wednesdayMillis)
        )
    }

    @Test
    fun ranges_areHalfOpenAndOrdered() {
        for (period in listOf(
            AssistantPeriod.TODAY,
            AssistantPeriod.YESTERDAY,
            AssistantPeriod.THIS_WEEK,
            AssistantPeriod.LAST_WEEK,
            AssistantPeriod.THIS_MONTH,
            AssistantPeriod.LAST_MONTH
        )) {
            val range = AssistantTime.resolve(period, wednesdayMillis)!!
            assertTrue("$period start < end", range.startInclusive < range.endExclusive)
        }
    }
}
