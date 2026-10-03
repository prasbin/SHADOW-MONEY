package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.BudgetCalendar
import java.time.Instant

/** Per-row import classification. Every row is always in exactly one state. */
enum class ImportRowState {
    /** Valid row, no duplicate detected. */
    NEW,

    /** Valid row that matches an existing transaction or an earlier row in this file. */
    POSSIBLE_DUPLICATE,

    /** Row failed validation; it can never be imported. */
    INVALID
}

enum class DuplicateMatchType {
    EXISTING_TRANSACTION,
    EARLIER_ROW
}

data class DuplicateMatch(
    val type: DuplicateMatchType,
    val existingTransactionId: Long? = null,
    val earlierRowNumber: Int? = null,
    val reason: String
)

/**
 * One parsed and validated CSV row. Holds both the original raw values (for
 * user review) and the resolved typed values (for the final import).
 * Immutable: selections and mappings are applied by producing a preview again.
 */
data class ImportRow(
    val rowNumber: Int,
    val lineNumber: Int,
    val rawDate: String,
    val rawDescription: String,
    val rawAmount: String,
    val rawDirection: String,
    val rawAccount: String,
    val rawCategory: String?,
    val rawExternalRef: String?,
    val state: ImportRowState,
    val invalidReasons: List<String>,
    val duplicate: DuplicateMatch?,
    val accountId: Long?,
    val categoryId: Long?,
    val unmatchedAccountName: String?,
    val unmatchedCategoryName: String?,
    val amountMinor: Long?,
    val direction: Int?,
    val timestamp: Long?,
    val note: String,
    val externalRef: String?,
    val rawBalance: String? = null,
    val balanceMinor: Long? = null
) {
    val hasUnmatchedReference: Boolean
        get() = unmatchedAccountName != null || unmatchedCategoryName != null

    /**
     * A row may only become a transaction when it is valid, its account and
     * category references resolve to existing local records, and the user has
     * selected it.
     */
    fun isImportable(): Boolean =
        state != ImportRowState.INVALID &&
            invalidReasons.isEmpty() &&
            accountId != null &&
            !hasUnmatchedReference
}

data class ImportPreview(
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
    val newRows: Int,
    val unmatchedAccountNames: List<String>,
    val unmatchedCategoryNames: List<String>,
    val importableRows: Int,
    val rows: List<ImportRow>
)

sealed interface PreviewOutcome {
    data class Ready(val preview: ImportPreview) : PreviewOutcome
    data class Empty(val message: String) : PreviewOutcome
    data class Rejected(val reason: String) : PreviewOutcome
}

/**
 * Deterministic duplicate fingerprint built only from stable transaction
 * fields: normalized account name, Kathmandu calendar date, amount,
 * direction, normalized category name, normalized note and external
 * reference. No database primary key is involved, so imported rows (which
 * have their own identifiers) are compared on equal terms.
 */
object ImportFingerprint {

    fun normalizeText(value: String?): String {
        val trimmed = (value ?: "").trim().lowercase()
        return Regex("\\s+").replace(trimmed, " ")
    }

    fun kathmanduDate(epochMillis: Long): String =
        java.time.ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis),
            BudgetCalendar.KATHMANDU_ZONE
        ).toLocalDate().toString()

    fun of(
        accountName: String,
        timestamp: Long,
        amountMinor: Long,
        direction: Int,
        categoryName: String?,
        note: String,
        externalRef: String?
    ): String = listOf(
        normalizeText(accountName),
        kathmanduDate(timestamp),
        amountMinor.toString(),
        direction.toString(),
        normalizeText(categoryName),
        normalizeText(note),
        normalizeText(externalRef)
    ).joinToString("|")
}
