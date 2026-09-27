package com.prasbin.shadowmoney.data

import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object BudgetCalendar {

    val KATHMANDU_ZONE: ZoneId = ZoneId.of("Asia/Kathmandu")

    private val monthKeyFormat = DateTimeFormatter.ofPattern("yyyy-MM")

    fun currentMonthKey(now: Long = System.currentTimeMillis()): String =
        monthKeyFormat.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), KATHMANDU_ZONE))

    fun monthKey(year: Int, month: Int): String =
        monthKeyFormat.format(YearMonth.of(year, month))

    fun monthStart(monthKey: String): Long =
        YearMonth.parse(monthKey)
            .atDay(1)
            .atStartOfDay(KATHMANDU_ZONE)
            .toInstant()
            .toEpochMilli()

    fun monthEndExclusive(monthKey: String): Long =
        YearMonth.parse(monthKey)
            .plusMonths(1)
            .atDay(1)
            .atStartOfDay(KATHMANDU_ZONE)
            .toInstant()
            .toEpochMilli()

    fun shiftMonth(monthKey: String, delta: Int): String =
        monthKeyFormat.format(YearMonth.parse(monthKey).plusMonths(delta.toLong()))

    fun monthLabel(monthKey: String): String =
        ZonedDateTime.ofInstant(
            YearMonth.parse(monthKey).atDay(1).atStartOfDay(KATHMANDU_ZONE).toInstant(),
            KATHMANDU_ZONE
        ).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}
