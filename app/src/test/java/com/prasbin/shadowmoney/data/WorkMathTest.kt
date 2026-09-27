package com.prasbin.shadowmoney.data

import org.junit.Test
import org.junit.Assert.*

class WorkMathTest {

    private val now = 1_800_000_000_000L

    @Test
    fun deadlineStatus_noDeadline() {
        assertEquals(DeadlineStatus.NO_DEADLINE, WorkMath.deadlineStatus(0L, now))
    }

    @Test
    fun deadlineStatus_upcoming() {
        val future = now + 10L * 86_400_000L
        assertEquals(DeadlineStatus.UPCOMING, WorkMath.deadlineStatus(future, now))
    }

    @Test
    fun deadlineStatus_dueToday() {
        val zone = BudgetCalendar.KATHMANDU_ZONE
        val todayStart = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(now), zone
        ).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(DeadlineStatus.DUE_TODAY, WorkMath.deadlineStatus(todayStart, now))
    }

    @Test
    fun deadlineStatus_overdue() {
        val past = now - 5L * 86_400_000L
        assertEquals(DeadlineStatus.OVERDUE, WorkMath.deadlineStatus(past, now))
    }

    @Test
    fun deadlineStatus_usesKathmanduDate() {
        val zone = BudgetCalendar.KATHMANDU_ZONE
        val tomorrowInKathmandu = java.time.ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(now), zone
        ).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(DeadlineStatus.UPCOMING, WorkMath.deadlineStatus(tomorrowInKathmandu, now))
    }

    @Test
    fun remainingExpected_canGoNegative() {
        assertEquals(5_000L, WorkMath.remainingExpectedMinor(10_000L, 5_000L))
        assertEquals(0L, WorkMath.remainingExpectedMinor(10_000L, 10_000L))
        assertEquals(-3_000L, WorkMath.remainingExpectedMinor(10_000L, 13_000L))
    }

    @Test
    fun statusLabels_areDeterministic() {
        assertEquals("Active", WorkMath.statusLabel(com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE))
        assertEquals("Paused", WorkMath.statusLabel(com.prasbin.shadowmoney.data.model.WORK_STATUS_PAUSED))
        assertEquals("Completed", WorkMath.statusLabel(com.prasbin.shadowmoney.data.model.WORK_STATUS_COMPLETED))
        assertEquals("Archived", WorkMath.statusLabel(com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED))
    }

    @Test
    fun deadlineLabels_areNeutral() {
        assertEquals("No deadline", WorkMath.deadlineLabel(DeadlineStatus.NO_DEADLINE))
        assertEquals("Upcoming", WorkMath.deadlineLabel(DeadlineStatus.UPCOMING))
        assertEquals("Due today", WorkMath.deadlineLabel(DeadlineStatus.DUE_TODAY))
        assertEquals("Overdue", WorkMath.deadlineLabel(DeadlineStatus.OVERDUE))
    }
}
