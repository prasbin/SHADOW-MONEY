package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ImportedStatement
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import com.prasbin.shadowmoney.data.model.Transaction
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

/**
 * Metadata captured from the UI at confirm time for one statement import.
 * Everything here is user-provided evidence; nothing implies a connection.
 * The three row counts describe the whole preview the user reviewed — the
 * imported row list passed to the repository is only their selected subset.
 */
data class StatementImportContext(
    val provider: String,
    val documentName: String,
    val format: String,
    val detectionEvidence: String,
    val fileSha256: String?,
    val unparsedLineCount: Int,
    val rowCount: Int,
    val invalidRowCount: Int,
    val duplicateRowCount: Int
)

data class ImportOutcome(
    val importedCount: Int,
    val statementId: Long?
)

/**
 * Wires the pure import engine to the local database and performs the final
 * confirmed import.
 *
 * Financial source of truth: imported rows become ordinary transactions in the
 * existing `transactions` table (source = IMPORT_FILE, optional externalRef
 * preserved). No parallel ledger or permanent import table exists.
 * The final write runs inside a single Room transaction, so a failed batch
 * never leaves a partially imported result.
 *
 * Statement metadata: when a [StatementImportContext] is supplied, one
 * [ImportedStatement] record is created in the same transaction and every
 * created transaction links to it via `statementId`, so reconciliation can
 * show the imported document as evidence without ever merging it into
 * connected/verified figures.
 */
class ImportRepository(
    private val database: ShadowMoneyDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun loadReference(): ImportReference {
        val accounts = database.accountDao().getAll().first()
        val categories = database.categoryDao().getAll().first()
        val transactions = database.transactionDao().getAll().first()

        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }

        val fingerprints = mutableMapOf<String, MutableList<Long>>()
        for (transaction in transactions) {
            val accountName = accountsById[transaction.accountId]?.name ?: ""
            val categoryName = transaction.categoryId?.let { categoriesById[it]?.name }
            val fingerprint = ImportFingerprint.of(
                accountName = accountName,
                timestamp = transaction.transactionTimestamp,
                amountMinor = transaction.amountMinor,
                direction = transaction.direction,
                categoryName = categoryName,
                note = transaction.note,
                externalRef = transaction.externalRef
            )
            fingerprints.getOrPut(fingerprint) { mutableListOf() }.add(transaction.id)
        }

        return ImportReference(
            accounts = accounts,
            categories = categories,
            existingFingerprints = fingerprints.mapValues { it.value.toList() }
        )
    }

    /**
     * Imports the selected rows atomically. Only rows that pass
     * [ImportRow.isImportable] are written; the whole batch succeeds or fails
     * together.
     *
     * @return the number of transactions created.
     */
    suspend fun importSelected(rows: List<ImportRow>): Int =
        importSelectedWithStatement(rows, null).importedCount

    /**
     * Imports the selected rows and, when [statement] is provided, records one
     * imported-statement evidence row linked to every created transaction —
     * all inside the same Room transaction.
     */
    suspend fun importSelectedWithStatement(
        rows: List<ImportRow>,
        statement: StatementImportContext?
    ): ImportOutcome {
        val importable = rows.filter { it.isImportable() }
        if (importable.isEmpty()) return ImportOutcome(0, null)

        var statementId: Long? = null
        database.withTransaction {
            if (statement != null) {
                statementId = database.importedStatementDao().insert(
                    buildStatement(statement, importable)
                )
            }
            val transactionDao = database.transactionDao()
            for (row in importable) {
                transactionDao.insert(
                    Transaction(
                        accountId = row.accountId ?: 0L,
                        categoryId = row.categoryId,
                        workItemId = null,
                        amountMinor = row.amountMinor ?: 0L,
                        direction = row.direction ?: 0,
                        transactionTimestamp = row.timestamp ?: 0L,
                        note = row.note,
                        createdTimestamp = clock(),
                        source = TRANSACTION_SOURCE_IMPORT_FILE,
                        externalRef = row.externalRef,
                        statementId = statementId
                    )
                )
            }
        }
        return ImportOutcome(importable.size, statementId)
    }

    private fun buildStatement(
        statement: StatementImportContext,
        importable: List<ImportRow>
    ): ImportedStatement {
        val timestamps = importable.mapNotNull { it.timestamp }
        val moneyIn = importable
            .filter { it.direction == com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME }
            .sumOf { it.amountMinor ?: 0L }
        val moneyOut = importable
            .filter { it.direction == com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW }
            .sumOf { it.amountMinor ?: 0L }
        val endBalance = importable.lastOrNull { it.balanceMinor != null }?.balanceMinor

        val notes = buildString {
            append(statement.detectionEvidence)
            if (statement.unparsedLineCount > 0) {
                append("; ${statement.unparsedLineCount} unrecognized line(s) kept for review")
            }
        }

        return ImportedStatement(
            provider = statement.provider,
            documentName = statement.documentName,
            format = statement.format,
            periodStartMs = timestamps.minOrNull(),
            periodEndMs = timestamps.maxOrNull(),
            endBalanceMinor = endBalance,
            endBalanceAccountId = importable.firstOrNull()?.accountId,
            importedAtMs = clock(),
            transactionCount = importable.size,
            moneyInMinor = moneyIn,
            moneyOutMinor = moneyOut,
            rowCount = statement.rowCount,
            invalidRowCount = statement.invalidRowCount,
            duplicateRowCount = statement.duplicateRowCount,
            fileSha256 = statement.fileSha256,
            notes = notes
        )
    }
}
