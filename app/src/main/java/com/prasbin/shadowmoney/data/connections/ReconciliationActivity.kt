package com.prasbin.shadowmoney.data.connections

import com.prasbin.shadowmoney.data.TransactionDao

/**
 * Recorded money movement since the baseline was set, split by provenance. The
 * verified total comes from CONNECTED_VERIFIED transactions, imported and manual
 * activity are tracked separately so an explanation never presents user-provided
 * movement as verified movement.
 */
interface ReconciliationActivity {
    data class RecordedActivity(
        val verifiedNetChangeMinor: Long,
        val importedNetChangeMinor: Long,
        val manualNetChangeMinor: Long
    )

    suspend fun netActivitySince(sinceMs: Long): RecordedActivity
}

/**
 * Production implementation backed by the transaction ledger: one bounded SQL
 * aggregate grouped by source, mapped to provenance with
 * [provenanceOfTransactionSource]. Provenance comes from the stored source string —
 * imported transactions stay imported regardless of what their amounts imply.
 */
class LedgerReconciliationActivity(
    private val transactionDao: TransactionDao
) : ReconciliationActivity {

    override suspend fun netActivitySince(sinceMs: Long): ReconciliationActivity.RecordedActivity {
        var verified = 0L
        var imported = 0L
        var manual = 0L
        transactionDao.netChangeBySourceSince(sinceMs).forEach { row ->
            when (provenanceOfTransactionSource(row.source)) {
                Provenance.CONNECTED_VERIFIED -> verified += row.netMinor
                Provenance.IMPORTED -> imported += row.netMinor
                Provenance.MANUAL_ENTRY -> manual += row.netMinor
            }
        }
        return ReconciliationActivity.RecordedActivity(
            verifiedNetChangeMinor = verified,
            importedNetChangeMinor = imported,
            manualNetChangeMinor = manual
        )
    }
}
