package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val STATEMENT_SOURCE_SANIMA = "SANIMA"
const val STATEMENT_SOURCE_GLOBAL_IME = "GLOBAL_IME"
const val STATEMENT_SOURCE_ESEWA = "ESEWA"
const val STATEMENT_SOURCE_UNKNOWN = "UNKNOWN"

const val STATEMENT_FORMAT_CSV = "CSV"
const val STATEMENT_FORMAT_PDF = "PDF"
const val STATEMENT_FORMAT_PASTED = "PASTED"

/**
 * Metadata for one user-provided statement document that was imported through
 * the real statement ingestion flow. One row per confirmed import.
 *
 * Provenance: everything recorded here is IMPORTED / USER-PROVIDED evidence.
 * Nothing in this table ever represents a live or verified connection — the
 * statement balance is the balance the document reported as of its own
 * period end, not a current bank balance.
 *
 * Privacy: [documentName] stores only the display name of the file the user
 * selected (no directory paths), and the original file content is never kept.
 */
@Entity(
    tableName = "imported_statements",
    indices = [Index("fileSha256")]
)
data class ImportedStatement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** StatementSource name: SANIMA, GLOBAL_IME, ESEWA or UNKNOWN. */
    val provider: String = STATEMENT_SOURCE_UNKNOWN,

    /** Display name of the selected document only — never a filesystem path. */
    val documentName: String = "",

    /** STATEMENT_FORMAT_CSV, STATEMENT_FORMAT_PDF or STATEMENT_FORMAT_PASTED. */
    val format: String = STATEMENT_FORMAT_CSV,

    /** Kathmandu-day start of the statement period (earliest row), if known. */
    val periodStartMs: Long? = null,

    /** Kathmandu-day end of the statement period (latest row), if known. */
    val periodEndMs: Long? = null,

    /** Ending balance as reported by the document (minor units), if known. */
    val endBalanceMinor: Long? = null,

    /** Account the statement rows were assigned to at import time, if any. */
    val endBalanceAccountId: Long? = null,

    /** When the user confirmed this import. */
    val importedAtMs: Long = 0L,

    /** Transactions actually created from this statement. */
    val transactionCount: Int = 0,

    /** Sum of imported INCOME rows (minor units). */
    val moneyInMinor: Long = 0L,

    /** Sum of imported OUTFLOW rows (minor units). */
    val moneyOutMinor: Long = 0L,

    /** Total data rows presented in the preview (before selection). */
    val rowCount: Int = 0,

    /** Rows classified INVALID and therefore never imported. */
    val invalidRowCount: Int = 0,

    /** Rows classified POSSIBLE_DUPLICATE (shown for review, never silent). */
    val duplicateRowCount: Int = 0,

    /** SHA-256 of the file bytes for same-document re-import detection. */
    val fileSha256: String? = null,

    /**
     * Short detection/import notes shown to the user, e.g. content-marker
     * evidence or "source confirmed by user". Never contains file paths.
     */
    val notes: String = ""
)
