package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.BudgetCalendar
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Deterministic date parsing for imported CSV values.
 *
 * Supported formats (bounded set, all interpreted in Asia/Kathmandu):
 * - yyyy-MM-dd
 * - yyyy/MM/dd
 * - yyyy-MM-dd HH:mm:ss  /  yyyy-MM-ddTHH:mm:ss
 * - yyyy-MM-dd HH:mm     /  yyyy-MM-ddTHH:mm
 *
 * Rejected with an explicit reason:
 * - timezone-qualified timestamps (Z or +HH:MM offsets) — never reinterpreted
 * - ambiguous day-first/month-first formats such as 01/02/2026
 * - impossible calendar dates such as 2026-02-30
 * - anything outside the supported set
 */
sealed interface DateParseResult {
    data class Ok(val epochMillis: Long) : DateParseResult
    data class Invalid(val reason: String) : DateParseResult
}

object ImportDate {

    private val dateDash = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$")
    private val dateSlash = Regex("^(\\d{4})/(\\d{2})/(\\d{2})$")
    private val dateTime = Regex("^(\\d{4})-(\\d{2})-(\\d{2})[T ](\\d{2}):(\\d{2})(?::(\\d{2}))?$")
    private val timezoneQualified = Regex(
        "^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}(:\\d{2})?(Z|[+-]\\d{2}:?\\d{2})$"
    )
    private val ambiguousDayFirst = Regex("^\\d{1,2}/\\d{1,2}/\\d{4}$")
    private val ambiguousDayFirstDash = Regex("^\\d{1,2}-\\d{1,2}-\\d{4}$")

    fun parse(raw: String): DateParseResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            return DateParseResult.Invalid("Date is required")
        }

        dateDash.matchEntire(text)?.let { match ->
            return buildDate(match.groupValues[1], match.groupValues[2], match.groupValues[3], raw)
        }
        dateSlash.matchEntire(text)?.let { match ->
            return buildDate(match.groupValues[1], match.groupValues[2], match.groupValues[3], raw)
        }
        dateTime.matchEntire(text)?.let { match ->
            return buildDateTime(
                match.groupValues[1], match.groupValues[2], match.groupValues[3],
                match.groupValues[4], match.groupValues[5],
                match.groupValues[6].ifEmpty { "0" },
                raw
            )
        }
        if (timezoneQualified.matches(text)) {
            return DateParseResult.Invalid(
                "Timezone-qualified timestamps are not supported; " +
                    "dates are interpreted in Asia/Kathmandu"
            )
        }
        if (ambiguousDayFirst.matches(text) || ambiguousDayFirstDash.matches(text)) {
            return DateParseResult.Invalid(
                "Ambiguous date format (day-first vs month-first): '$raw'; " +
                    "use yyyy-MM-dd or yyyy/MM/dd"
            )
        }
        return DateParseResult.Invalid(
            "Unsupported date format: '$raw'; use yyyy-MM-dd or yyyy/MM/dd"
        )
    }

    private fun buildDate(year: String, month: String, day: String, raw: String): DateParseResult {
        val date = try {
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } catch (e: DateTimeException) {
            return DateParseResult.Invalid("Invalid date: '$raw'")
        }
        val timestamp = date.atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
        return DateParseResult.Ok(timestamp)
    }

    private fun buildDateTime(
        year: String, month: String, day: String,
        hour: String, minute: String, second: String,
        raw: String
    ): DateParseResult {
        val dateTime = try {
            LocalDateTime.of(
                year.toInt(), month.toInt(), day.toInt(),
                hour.toInt(), minute.toInt(), second.toInt()
            )
        } catch (e: DateTimeException) {
            return DateParseResult.Invalid("Invalid date: '$raw'")
        }
        val timestamp = dateTime.atZone(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
        return DateParseResult.Ok(timestamp)
    }
}
