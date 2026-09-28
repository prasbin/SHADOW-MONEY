package com.prasbin.shadowmoney.assistant

import com.prasbin.shadowmoney.data.BudgetCalendar
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Resolved half-open timestamp range `[startInclusive, endExclusive)`.
 */
data class AssistantPeriodRange(
    val startInclusive: Long,
    val endExclusive: Long,
    val label: String
)

/**
 * Deterministic period boundaries for the assistant.
 *
 * Timezone: Asia/Kathmandu (UTC+05:45, no DST) — the project's established
 * zone for all financial date handling.
 *
 * Week definition: the assistant defines "this week" as the current ISO week
 * starting Monday in Kathmandu (and "last week" as the week before it). This
 * is a documented, deterministic convention; month boundaries reuse
 * [BudgetCalendar] exactly.
 */
object AssistantTime {

    fun startOfDayKathmandu(now: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), BudgetCalendar.KATHMANDU_ZONE)
            .withHour(0).withMinute(0).withSecond(0).withNano(0)

    fun resolve(period: AssistantPeriod, now: Long): AssistantPeriodRange? {
        val today = startOfDayKathmandu(now)
        return when (period) {
            AssistantPeriod.TODAY ->
                range(today, today.plusDays(1), "today")

            AssistantPeriod.YESTERDAY ->
                range(today.minusDays(1), today, "yesterday")

            AssistantPeriod.THIS_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                range(monday, monday.plusDays(7), "this week")
            }

            AssistantPeriod.LAST_WEEK -> {
                val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(7)
                range(monday, monday.plusDays(7), "last week")
            }

            AssistantPeriod.THIS_MONTH -> {
                val key = BudgetCalendar.currentMonthKey(now)
                AssistantPeriodRange(
                    BudgetCalendar.monthStart(key),
                    BudgetCalendar.monthEndExclusive(key),
                    "this month"
                )
            }

            AssistantPeriod.LAST_MONTH -> {
                val key = BudgetCalendar.shiftMonth(BudgetCalendar.currentMonthKey(now), -1)
                AssistantPeriodRange(
                    BudgetCalendar.monthStart(key),
                    BudgetCalendar.monthEndExclusive(key),
                    "last month"
                )
            }

            AssistantPeriod.RECENT -> null
        }
    }

    /** Month key a budget question refers to. */
    fun budgetMonthKey(period: AssistantPeriod?, now: Long): String {
        val current = BudgetCalendar.currentMonthKey(now)
        return if (period == AssistantPeriod.LAST_MONTH) {
            BudgetCalendar.shiftMonth(current, -1)
        } else {
            current
        }
    }

    fun budgetMonthLabel(period: AssistantPeriod?, now: Long): String =
        BudgetCalendar.monthLabel(budgetMonthKey(period, now))

    private fun range(start: ZonedDateTime, end: ZonedDateTime, label: String) =
        AssistantPeriodRange(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), label)
}
