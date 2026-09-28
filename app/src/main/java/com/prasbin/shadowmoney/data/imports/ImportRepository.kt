package com.prasbin.shadowmoney.data.imports

import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.TRANSACTION_SOURCE_IMPORT_FILE
import com.prasbin.shadowmoney.data.model.Transaction
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first

/**
 * Wires the pure import engine to the local database and performs the final
 * confirmed import.
 *
 * Financial source of truth: imported rows become ordinary transactions in the
 * existing `transactions` table (source = IMPORT_FILE, optional externalRef
 * preserved). No parallel ledger or permanent import table exists.
 * The final write runs inside a single Room transaction, so a failed batch
 * never leaves a partially imported result.
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
    suspend fun importSelected(rows: List<ImportRow>): Int {
        val importable = rows.filter { it.isImportable() }
        if (importable.isEmpty()) return 0

        val transactions = importable.map { row ->
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
                externalRef = row.externalRef
            )
        }

        database.withTransaction {
            val transactionDao = database.transactionDao()
            for (transaction in transactions) {
                transactionDao.insert(transaction)
            }
        }
        return transactions.size
    }
}
