package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_UNKNOWN
import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_SANIMA
import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_GLOBAL_IME
import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_ESEWA

/**
 * Source of an imported statement. UNKNOWN is a first-class value: when the
 * file gives no usable evidence and the user does not pick a provider, the
 * statement is stored as UNKNOWN rather than guessed.
 */
enum class StatementSource(val label: String, val storageName: String) {
    SANIMA("Sanima", STATEMENT_SOURCE_SANIMA),
    GLOBAL_IME("Global IME", STATEMENT_SOURCE_GLOBAL_IME),
    ESEWA("eSewa", STATEMENT_SOURCE_ESEWA),
    UNKNOWN("Unknown source", STATEMENT_SOURCE_UNKNOWN);

    val isKnown: Boolean
        get() = this != UNKNOWN

    companion object {
        fun fromStorage(raw: String?): StatementSource =
            entries.firstOrNull { it.storageName == raw } ?: UNKNOWN
    }
}

enum class StatementFormat(val storageName: String) {
    CSV("CSV"),
    PDF("PDF"),
    PASTED("PASTED");

    companion object {
        fun fromStorage(raw: String?): StatementFormat =
            entries.firstOrNull { it.storageName == raw } ?: CSV
    }
}

/**
 * Content-based provider evidence. [candidate] is only ever a suggestion:
 * the filename is deliberately not an input anywhere in detection, and the
 * UI must show the candidate and let the user confirm or change it.
 */
data class StatementDetection(
    val candidate: StatementSource,
    val evidence: String
)

enum class StatementFailureReason(val userMessage: String) {
    FILE_TOO_LARGE("The file is larger than the allowed import size (5 MB)."),
    READ_ERROR("The selected file could not be read."),
    UNSUPPORTED_FORMAT("Unsupported file type. Select a CSV or PDF statement."),
    EMPTY_DOCUMENT("The selected file is empty."),
    MALFORMED_PDF("This PDF could not be read as a statement. Export the file again from your banking app, or use CSV."),
    ENCRYPTED_PDF(
        "This PDF is password-protected. Remove the PDF password in your banking " +
            "app's export options and try again — SHADOW MONEY never asks for or stores banking passwords."
    ),
    NO_TEXT_EXTRACTED(
        "No selectable text was found in this PDF. Scanned or image-only PDFs are not " +
            "supported; export a text-based PDF or a CSV statement instead."
    ),
    NO_TRANSACTIONS_DETECTED("No transactions were detected in the selected file."),
    AMBIGUOUS_COLUMNS(
        "The statement's columns could not be identified safely. Import a CSV with a " +
            "header row (date, description, amount, direction, account) instead."
    )
}

data class StatementFailure(
    val reason: StatementFailureReason,
    val detail: String = ""
) {
    val userMessage: String
        get() = if (detail.isEmpty()) reason.userMessage else "${reason.userMessage} ($detail)"
}

/** Input to a [StatementParser]: either decoded text or raw PDF bytes. */
sealed interface StatementInput {
    val documentName: String
    val format: StatementFormat

    data class TextDocument(
        val text: String,
        override val documentName: String,
        override val format: StatementFormat = StatementFormat.CSV
    ) : StatementInput

    class PdfDocument(
        val bytes: ByteArray,
        override val documentName: String
    ) : StatementInput {
        override val format: StatementFormat
            get() = StatementFormat.PDF
    }
}

/**
 * Normalized result of parsing one statement document. [document] is fed to
 * the existing [com.prasbin.shadowmoney.data.imports.ImportEngine] so that
 * validation, duplicates and mapping keep a single code path. Rows that could
 * not be interpreted are listed in [unparsedLines] and surfaced in the
 * preview — nothing is dropped silently.
 */
data class StatementParseResult(
    val format: StatementFormat,
    val document: com.prasbin.shadowmoney.data.imports.CsvDocument,
    val detection: StatementDetection,
    val unparsedLines: List<String>,
    val notes: List<String>
)

sealed interface StatementParseOutcome {
    data class Parsed(val result: StatementParseResult) : StatementParseOutcome
    data class Failed(val failure: StatementFailure) : StatementParseOutcome
}

object StatementHash {
    fun sha256Hex(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
