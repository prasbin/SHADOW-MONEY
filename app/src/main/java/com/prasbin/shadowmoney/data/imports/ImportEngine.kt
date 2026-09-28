package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import kotlin.math.abs

/**
 * Snapshot of the local reference data an import preview validates against.
 * Built once per preview; account/category names are normalized with the same
 * function used for duplicate fingerprints.
 */
data class ImportReference(
    val accounts: List<Account>,
    val categories: List<Category>,
    val existingFingerprints: Map<String, List<Long>>
) {
    internal val accountsById: Map<Long, Account> by lazy { accounts.associateBy { it.id } }
    internal val accountsByName: Map<String, Account> by lazy {
        accounts.associateBy { ImportFingerprint.normalizeText(it.name) }
    }
    internal val categoriesById: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    internal val categoriesByName: Map<String, Category> by lazy {
        categories.associateBy { ImportFingerprint.normalizeText(it.name) }
    }
}

/**
 * Pure CSV → preview validation. Performs no database writes of any kind:
 * the preview is built entirely in memory from the parsed document and the
 * reference snapshot.
 *
 * Sign / direction rules (documented, deterministic, tested):
 * - The direction column is required and authoritative for direction.
 * - INCOME requires a positive amount; a negative income amount is a
 *   contradiction and rejects the row (never reinterpreted).
 * - OUTFLOW stores the absolute magnitude; a negative amount agrees with the
 *   direction (money leaving), a positive amount is a magnitude.
 * - Zero amounts never become transactions.
 */
object ImportEngine {

    fun buildPreview(
        document: CsvDocument,
        reference: ImportReference,
        accountMappings: Map<String, Long> = emptyMap(),
        categoryMappings: Map<String, Long> = emptyMap()
    ): PreviewOutcome {
        document.headerError?.let { return PreviewOutcome.Rejected(it) }

        val columns = when (val mapping = ImportSchema.resolve(document.header)) {
            is MappingResult.Rejected -> return PreviewOutcome.Rejected(mapping.reason)
            is MappingResult.Ok -> mapping.columns
        }

        if (document.records.isEmpty()) {
            return PreviewOutcome.Empty("No data rows found in the CSV input.")
        }

        val headerSize = document.header?.size ?: 0
        val rowsInFile = mutableListOf<ImportRow>()
        val seenFingerprints = mutableMapOf<String, Int>()

        document.records.forEachIndexed { index, record ->
            val rowNumber = index + 1
            rowsInFile.add(
                validateRecord(
                    rowNumber = rowNumber,
                    record = record,
                    headerSize = headerSize,
                    columns = columns,
                    reference = reference,
                    accountMappings = accountMappings,
                    categoryMappings = categoryMappings,
                    seenFingerprints = seenFingerprints
                )
            )
        }

        val preview = ImportPreview(
            totalRows = rowsInFile.size,
            validRows = rowsInFile.count { it.state != ImportRowState.INVALID },
            invalidRows = rowsInFile.count { it.state == ImportRowState.INVALID },
            duplicateRows = rowsInFile.count { it.state == ImportRowState.POSSIBLE_DUPLICATE },
            newRows = rowsInFile.count { it.state == ImportRowState.NEW },
            unmatchedAccountNames = rowsInFile.mapNotNull { it.unmatchedAccountName }
                .distinct().sorted(),
            unmatchedCategoryNames = rowsInFile.mapNotNull { it.unmatchedCategoryName }
                .distinct().sorted(),
            importableRows = rowsInFile.count { it.isImportable() },
            rows = rowsInFile
        )
        return PreviewOutcome.Ready(preview)
    }

    private fun validateRecord(
        rowNumber: Int,
        record: CsvRecord,
        headerSize: Int,
        columns: Map<ImportColumn, Int>,
        reference: ImportReference,
        accountMappings: Map<String, Long>,
        categoryMappings: Map<String, Long>,
        seenFingerprints: MutableMap<String, Int>
    ): ImportRow {
        val reasons = mutableListOf<String>()

        fun cell(column: ImportColumn): String? =
            columns[column]?.let { record.values.getOrNull(it) }?.trim()

        if (record.parseError != null) {
            reasons.add("Malformed CSV row: ${record.parseError}")
        } else if (record.values.size != headerSize) {
            reasons.add(
                "Malformed row: expected $headerSize columns, found ${record.values.size}"
            )
        }

        val rawDate = cell(ImportColumn.DATE) ?: ""
        val rawDescription = cell(ImportColumn.DESCRIPTION) ?: ""
        val rawAmount = cell(ImportColumn.AMOUNT) ?: ""
        val rawDirection = cell(ImportColumn.DIRECTION) ?: ""
        val rawAccount = cell(ImportColumn.ACCOUNT) ?: ""
        val rawCategory = cell(ImportColumn.CATEGORY)
        val rawExternalRef = cell(ImportColumn.EXTERNAL_REF)?.ifEmpty { null }

        var timestamp: Long? = null
        var amountMinor: Long? = null
        var direction: Int? = null

        if (reasons.isEmpty()) {
            when (val dateResult = ImportDate.parse(rawDate)) {
                is DateParseResult.Invalid -> reasons.add(dateResult.reason)
                is DateParseResult.Ok -> timestamp = dateResult.epochMillis
            }

            if (rawDescription.isEmpty()) {
                reasons.add("Missing description")
            }

            val parsedAmount = when (val amountResult = ImportAmount.parse(rawAmount)) {
                is AmountParseResult.Invalid -> {
                    reasons.add(amountResult.reason)
                    null
                }
                is AmountParseResult.Ok -> amountResult.minorUnits
            }

            val parsedDirection = when (val directionResult = ImportAmount.parseDirection(rawDirection)) {
                is DirectionParseResult.Invalid -> {
                    reasons.add(directionResult.reason)
                    null
                }
                is DirectionParseResult.Ok -> directionResult.direction
            }

            if (parsedAmount != null && parsedDirection != null) {
                when {
                    parsedAmount == 0L ->
                        reasons.add("Amount must be non-zero")
                    parsedDirection == TRANSACTION_DIRECTION_INCOME && parsedAmount < 0L ->
                        reasons.add(
                            "Amount/direction conflict: INCOME with negative amount"
                        )
                    parsedDirection == TRANSACTION_DIRECTION_INCOME -> {
                        amountMinor = parsedAmount
                        direction = TRANSACTION_DIRECTION_INCOME
                    }
                    else -> {
                        amountMinor = abs(parsedAmount)
                        direction = TRANSACTION_DIRECTION_OUTFLOW
                    }
                }
            }

            if (rawAccount.isEmpty()) {
                reasons.add("Missing account")
            }
            if (columns.containsKey(ImportColumn.CATEGORY) && rawCategory.isNullOrEmpty()) {
                reasons.add("Missing category")
            }
        }

        val unmatchedAccountName: String?
        var accountId: Long? = null
        val fieldValid = reasons.isEmpty()
        if (!fieldValid || rawAccount.isEmpty()) {
            unmatchedAccountName = null
        } else {
            val accountKey = ImportFingerprint.normalizeText(rawAccount)
            val resolved = reference.accountsByName[accountKey]
                ?: accountMappings[accountKey]?.let { reference.accountsById[it] }
            if (resolved != null) {
                accountId = resolved.id
                unmatchedAccountName = null
            } else {
                unmatchedAccountName = rawAccount
            }
        }

        val unmatchedCategoryName: String?
        var categoryId: Long? = null
        val categoryCell = rawCategory?.trim().orEmpty()
        when {
            !fieldValid -> unmatchedCategoryName = null
            !columns.containsKey(ImportColumn.CATEGORY) -> unmatchedCategoryName = null
            categoryCell.isEmpty() -> unmatchedCategoryName = null
            else -> {
                val categoryKey = ImportFingerprint.normalizeText(categoryCell)
                val resolved = reference.categoriesByName[categoryKey]
                    ?: categoryMappings[categoryKey]?.let { reference.categoriesById[it] }
                if (resolved != null) {
                    categoryId = resolved.id
                    unmatchedCategoryName = null
                } else {
                    unmatchedCategoryName = categoryCell
                }
            }
        }

        var state = ImportRowState.NEW
        var duplicate: DuplicateMatch? = null
        if (reasons.isEmpty()) {
            val fingerprint = ImportFingerprint.of(
                accountName = rawAccount,
                timestamp = timestamp!!,
                amountMinor = amountMinor!!,
                direction = direction!!,
                categoryName = categoryCell.ifEmpty { null },
                note = rawDescription,
                externalRef = rawExternalRef
            )
            val existingIds = reference.existingFingerprints[fingerprint]
            if (!existingIds.isNullOrEmpty()) {
                state = ImportRowState.POSSIBLE_DUPLICATE
                duplicate = DuplicateMatch(
                    type = DuplicateMatchType.EXISTING_TRANSACTION,
                    existingTransactionId = existingIds.first(),
                    reason = "Matches existing transaction #${existingIds.first()}"
                )
            } else {
                val earlierRow = seenFingerprints[fingerprint]
                if (earlierRow != null) {
                    state = ImportRowState.POSSIBLE_DUPLICATE
                    duplicate = DuplicateMatch(
                        type = DuplicateMatchType.EARLIER_ROW,
                        earlierRowNumber = earlierRow,
                        reason = "Matches row #$earlierRow in this file"
                    )
                } else {
                    seenFingerprints[fingerprint] = rowNumber
                }
            }
        }

        if (reasons.isNotEmpty()) {
            state = ImportRowState.INVALID
        }

        return ImportRow(
            rowNumber = rowNumber,
            lineNumber = record.lineNumber,
            rawDate = rawDate,
            rawDescription = rawDescription,
            rawAmount = rawAmount,
            rawDirection = rawDirection,
            rawAccount = rawAccount,
            rawCategory = rawCategory?.trim()?.ifEmpty { null },
            rawExternalRef = rawExternalRef,
            state = state,
            invalidReasons = reasons,
            duplicate = duplicate,
            accountId = accountId,
            categoryId = categoryId,
            unmatchedAccountName = unmatchedAccountName,
            unmatchedCategoryName = unmatchedCategoryName,
            amountMinor = amountMinor,
            direction = direction,
            timestamp = timestamp,
            note = rawDescription,
            externalRef = rawExternalRef
        )
    }
}
