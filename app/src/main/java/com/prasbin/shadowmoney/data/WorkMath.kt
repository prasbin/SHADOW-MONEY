package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_COMPLETED
import com.prasbin.shadowmoney.data.model.WORK_STATUS_PAUSED
import java.time.Instant
import java.time.ZonedDateTime

enum class DeadlineStatus { NO_DEADLINE, UPCOMING, DUE_TODAY, OVERDUE }

object WorkMath {

    fun deadlineStatus(deadlineTimestamp: Long, now: Long): DeadlineStatus {
        if (deadlineTimestamp <= 0L) return DeadlineStatus.NO_DEADLINE
        val zone = BudgetCalendar.KATHMANDU_ZONE
        val deadlineDate = ZonedDateTime.ofInstant(Instant.ofEpochMilli(deadlineTimestamp), zone).toLocalDate()
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        return when {
            deadlineDate.isBefore(today) -> DeadlineStatus.OVERDUE
            deadlineDate.isEqual(today) -> DeadlineStatus.DUE_TODAY
            else -> DeadlineStatus.UPCOMING
        }
    }

    fun remainingExpectedMinor(expectedAmountMinor: Long, receivedMinor: Long): Long =
        expectedAmountMinor - receivedMinor

    fun statusLabel(status: Int): String = when (status) {
        WORK_STATUS_ACTIVE -> "Active"
        WORK_STATUS_PAUSED -> "Paused"
        WORK_STATUS_COMPLETED -> "Completed"
        WORK_STATUS_ARCHIVED -> "Archived"
        else -> "Unknown"
    }

    fun deadlineLabel(status: DeadlineStatus): String = when (status) {
        DeadlineStatus.NO_DEADLINE -> "No deadline"
        DeadlineStatus.UPCOMING -> "Upcoming"
        DeadlineStatus.DUE_TODAY -> "Due today"
        DeadlineStatus.OVERDUE -> "Overdue"
    }
}
