package com.prasbin.shadowmoney.data.imports

/**
 * Supported CSV import columns and deterministic header resolution.
 *
 * Required columns: date, description, amount, direction, account.
 * Optional columns: category, external reference, balance.
 *
 * Header names are normalized (trim, lower-case, non-alphanumeric runs become
 * "_") and matched against a bounded alias set. Ambiguous headers (two columns
 * resolving to the same logical column) are rejected instead of guessed.
 */
enum class ImportColumn {
    DATE, DESCRIPTION, AMOUNT, DIRECTION, ACCOUNT, CATEGORY, EXTERNAL_REF, BALANCE;

    val isRequired: Boolean
        get() = this == DATE || this == DESCRIPTION || this == AMOUNT ||
            this == DIRECTION || this == ACCOUNT

    val label: String
        get() = when (this) {
            DATE -> "date"
            DESCRIPTION -> "description"
            AMOUNT -> "amount"
            DIRECTION -> "direction"
            ACCOUNT -> "account"
            CATEGORY -> "category"
            EXTERNAL_REF -> "external reference"
            BALANCE -> "balance"
        }
}

object ImportSchema {

    private val aliases: Map<ImportColumn, Set<String>> = mapOf(
        ImportColumn.DATE to setOf("date", "transaction_date"),
        ImportColumn.DESCRIPTION to setOf("description", "note"),
        ImportColumn.AMOUNT to setOf("amount"),
        ImportColumn.DIRECTION to setOf("direction", "type"),
        ImportColumn.ACCOUNT to setOf("account"),
        ImportColumn.CATEGORY to setOf("category"),
        ImportColumn.EXTERNAL_REF to setOf("external_ref", "reference", "transaction_id"),
        ImportColumn.BALANCE to setOf(
            "balance", "closing_balance", "running_balance", "available_balance",
            "closing_bal", "balance_amount"
        )
    )

    fun normalizeHeader(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')

    fun resolve(header: List<String>?): MappingResult {
        if (header == null || header.isEmpty()) {
            return MappingResult.Rejected("Missing header row")
        }

        val normalized = header.map { normalizeHeader(it) }
        val recognizedCount = aliases.values.flatten().count { it in normalized }
        if (recognizedCount == 0) {
            return MappingResult.Rejected(
                "Missing header row: no recognized column names found"
            )
        }

        val columns = mutableMapOf<ImportColumn, Int>()
        for (column in ImportColumn.entries) {
            val aliasSet = aliases.getValue(column)
            val matches = normalized.withIndex()
                .filter { (_, name) -> name in aliasSet }
                .map { (index, _) -> index }
            if (matches.size > 1) {
                val names = matches.joinToString(", ") { header[it].trim() }
                return MappingResult.Rejected(
                    "Ambiguous columns for ${column.label}: $names"
                )
            }
            if (matches.size == 1) {
                columns[column] = matches[0]
            }
        }

        val missing = ImportColumn.entries
            .filter { it.isRequired && it !in columns }
            .map { it.label }
        if (missing.isNotEmpty()) {
            return MappingResult.Rejected(
                "Missing required columns: ${missing.joinToString(", ")}"
            )
        }
        return MappingResult.Ok(columns)
    }
}

sealed interface MappingResult {
    data class Ok(val columns: Map<ImportColumn, Int>) : MappingResult
    data class Rejected(val reason: String) : MappingResult
}
