package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.imports.ImportPreview
import com.prasbin.shadowmoney.data.imports.ImportRowState

/**
 * Preview-level summary of one statement: period, money flow, ending balance
 * and review counts. All figures derive from the parsed document only —
 * nothing is projected or estimated.
 */
data class StatementSummary(
    val periodStartMs: Long?,
    val periodEndMs: Long?,
    val endBalanceMinor: Long?,
    val moneyInMinor: Long,
    val moneyOutMinor: Long,
    val totalRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
    val importableRows: Int,
    val unparsedLines: Int
) {
    companion object {
        fun from(preview: ImportPreview, unparsedLines: Int = 0): StatementSummary {
            val usable = preview.rows.filter {
                it.timestamp != null && it.state != ImportRowState.INVALID
            }
            val importable = preview.rows.filter { it.isImportable() }
            return StatementSummary(
                periodStartMs = usable.minOfOrNull { it.timestamp!! },
                periodEndMs = usable.maxOfOrNull { it.timestamp!! },
                endBalanceMinor = importable.lastOrNull { it.balanceMinor != null }?.balanceMinor,
                moneyInMinor = importable
                    .filter { it.direction == com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME }
                    .sumOf { it.amountMinor ?: 0L },
                moneyOutMinor = importable
                    .filter { it.direction == com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW }
                    .sumOf { it.amountMinor ?: 0L },
                totalRows = preview.totalRows,
                invalidRows = preview.invalidRows,
                duplicateRows = preview.duplicateRows,
                importableRows = preview.importableRows,
                unparsedLines = unparsedLines
            )
        }
    }
}

enum class StatementAgeClass { RECENT, OLD }

data class StatementAgeInfo(
    val ageClass: StatementAgeClass,
    val label: String,
    val detail: String
)

/**
 * Imported statement age (never "live"): an imported document is recent only
 * while its own statement period (or, if unknown, its import time) is within
 * [RECENT_DAYS] of now. The UI must show [label] together with [detail], so a
 * 45-day-old period is always spoken as imported-and-old, never as a current
 * balance.
 */
object StatementAgeClassifier {

    const val RECENT_DAYS = 31L

    fun classify(nowMs: Long, periodEndMs: Long?, importedAtMs: Long): StatementAgeInfo {
        val reference = periodEndMs ?: importedAtMs
        val elapsed = (nowMs - reference).coerceAtLeast(0L)
        val days = elapsed / MILLIS_PER_DAY
        val recent = days <= RECENT_DAYS
        return StatementAgeInfo(
            ageClass = if (recent) StatementAgeClass.RECENT else StatementAgeClass.OLD,
            label = if (recent) "IMPORTED — RECENT" else "IMPORTED — OLD",
            detail = if (periodEndMs != null) {
                "period ended $days day(s) ago"
            } else {
                "imported $days day(s) ago"
            }
        )
    }

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
}
