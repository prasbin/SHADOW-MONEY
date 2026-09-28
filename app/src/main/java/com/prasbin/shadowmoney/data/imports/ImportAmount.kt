package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW

/**
 * Exact money parsing for imported CSV values.
 *
 * Never uses Float or Double. Values are parsed with integer arithmetic into the
 * project's minor-unit Long representation (100 minor units per major unit).
 *
 * Deterministic rules:
 * - Optional leading "+" / "-" sign.
 * - Optional thousands separators, only in valid groups (1,234.56).
 * - Zero, one or two decimal places are accepted; fewer than two are zero-padded.
 * - More than two decimal places are rejected (never silently rounded).
 * - Non-numeric input, bad grouping and overflow are rejected with a reason.
 * - Zero parses to 0 minor units (the import engine rejects zero-amount rows
 *   separately, so zero never becomes a transaction).
 */
sealed interface AmountParseResult {
    data class Ok(val minorUnits: Long) : AmountParseResult
    data class Invalid(val reason: String) : AmountParseResult
}

object ImportAmount {

    private val numberPattern = Regex("^(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?$")

    fun parse(raw: String): AmountParseResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            return AmountParseResult.Invalid("Amount is required")
        }

        var signNegative = false
        var digits = text
        val first = text[0]
        if (first == '+' || first == '-') {
            signNegative = first == '-'
            digits = text.substring(1)
        }
        if (digits.isEmpty()) {
            return AmountParseResult.Invalid("Invalid amount: '$raw'")
        }

        val match = numberPattern.matchEntire(digits)
            ?: return AmountParseResult.Invalid("Invalid amount: '$raw'")

        val fraction = match.groupValues[2]
        if (fraction.length > 2) {
            return AmountParseResult.Invalid(
                "Amount has more than two decimal places: '$raw'"
            )
        }
        val fractionMinor = (fraction + "00").substring(0, 2).toIntOrNull()
            ?: return AmountParseResult.Invalid("Invalid amount: '$raw'")

        val wholeDigits = match.groupValues[1].replace(",", "")
        val whole = wholeDigits.toLongOrNull()
            ?: return AmountParseResult.Invalid("Amount overflow: '$raw'")

        val magnitude = try {
            Math.addExact(Math.multiplyExact(whole, 100L), fractionMinor.toLong())
        } catch (e: ArithmeticException) {
            return AmountParseResult.Invalid("Amount overflow: '$raw'")
        }

        val minorUnits = if (signNegative) -magnitude else magnitude
        return AmountParseResult.Ok(minorUnits)
    }

    private val incomeAliases = setOf(
        "income", "credit", "cr", "deposit", "received", "in"
    )
    private val outflowAliases = setOf(
        "outflow", "debit", "dr", "withdrawal", "expense", "spent", "out"
    )

    fun parseDirection(raw: String): DirectionParseResult {
        val normalized = raw.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")
        if (normalized.isEmpty()) {
            return DirectionParseResult.Invalid("Direction is required")
        }
        return when {
            normalized in incomeAliases ->
                DirectionParseResult.Ok(TRANSACTION_DIRECTION_INCOME)
            normalized in outflowAliases ->
                DirectionParseResult.Ok(TRANSACTION_DIRECTION_OUTFLOW)
            else -> DirectionParseResult.Invalid("Unsupported direction: '$raw'")
        }
    }
}

sealed interface DirectionParseResult {
    data class Ok(val direction: Int) : DirectionParseResult
    data class Invalid(val reason: String) : DirectionParseResult
}
