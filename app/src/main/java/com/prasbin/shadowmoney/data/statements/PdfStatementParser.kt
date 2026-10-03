package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.imports.CsvDocument
import com.prasbin.shadowmoney.data.imports.CsvRecord

/**
 * One table row extracted from PDF statement text. Values are raw strings:
 * validation, typing, direction rules and duplicate detection all stay in the
 * existing ImportEngine, so a PDF row can never bypass a CSV row's checks.
 */
data class StatementRow(
    val lineNumber: Int,
    val date: String,
    val description: String,
    val amount: String,
    val direction: String,
    val externalRef: String?,
    val balance: String?
)

data class PdfTableResult(
    val rows: List<StatementRow>,
    val unparsedLines: List<String>,
    val notes: List<String>,
    val failure: StatementFailure?
)

/**
 * Generic statement-table inference from extracted PDF text.
 *
 * Conservative by design:
 * - The header must clearly expose date + description and at least one of
 *   debit/credit/amount columns; otherwise the whole file fails with
 *   AMBIGUOUS_COLUMNS instead of guessing.
 * - Direction is read from the document itself: a populated debit cell is an
 *   OUTFLOW, a populated credit cell is an INCOME, and a single amount column
 *   only yields direction from an explicit sign. Nothing is inferred beyond
 *   what the row states.
 * - Lines that do not fit the detected column count are returned as
 *   [unparsedLines] for user review — never dropped silently.
 */
object PdfStatementTable {

    private const val MAX_UNPARSED_LINES = 500

    private val dateTokens = setOf(
        "date", "txn date", "transaction date", "value date", "posting date",
        "tran date", "date time", "datetime", "s.n."
    )
    private val descriptionTokens = setOf(
        "description", "particulars", "narration", "details", "remarks", "remark",
        "transaction details", "transaction particulars", "payee", "narrative",
        "transaction remark", "particular"
    )
    private val debitTokens = setOf(
        "debit", "withdrawal", "withdrawals", "dr", "paid out", "debit amount",
        "withdrawal amount", "amount(dr)", "debit(dr)"
    )
    private val creditTokens = setOf(
        "credit", "deposit", "deposits", "cr", "paid in", "credit amount",
        "deposit amount", "amount(cr)", "credit(cr)"
    )
    private val amountTokens = setOf("amount", "transaction amount", "amt", "value")
    private val balanceTokens = setOf(
        "balance", "closing balance", "available balance", "running balance",
        "closing bal", "balance amount", "avail balance"
    )
    private val refTokens = setOf(
        "reference", "ref", "ref no", "reference no", "transaction id", "txn id",
        "cheque no", "cheque number", "cheque", "utr", "utr no", "reference number"
    )

    private enum class Column { DATE, DESCRIPTION, DEBIT, CREDIT, AMOUNT, BALANCE, REF }

    fun build(text: String): PdfTableResult {
        val lines = text.split('\n')
        val notes = mutableListOf<String>()

        var headerIndex = -1
        var headerCells: List<String> = emptyList()
        var columns: Map<Column, Int> = emptyMap()

        for ((index, line) in lines.withIndex()) {
            val cells = splitCells(line)
            if (cells.size < 3) continue
            val resolved = matchHeader(cells) ?: continue
            headerIndex = index
            headerCells = cells
            columns = resolved
            break
        }

        if (headerIndex < 0) {
            return PdfTableResult(
                rows = emptyList(),
                unparsedLines = emptyList(),
                notes = notes,
                failure = StatementFailure(
                    StatementFailureReason.AMBIGUOUS_COLUMNS,
                    "no statement table header found"
                )
            )
        }
        notes.add("statement table header detected at line ${headerIndex + 1}")

        val leadingTextLines = lines.take(headerIndex).count { it.isNotBlank() }
        if (leadingTextLines > 0) {
            notes.add("$leadingTextLines header/title lines above the table were ignored")
        }

        val rows = mutableListOf<StatementRow>()
        val unparsed = mutableListOf<String>()
        var repeatedHeaders = 0

        for (index in (headerIndex + 1) until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            var cells = splitCells(line)
            if (cells.size >= 3 && matchHeader(cells) != null) {
                repeatedHeaders++
                continue
            }
            val fits = when {
                cells.size > headerCells.size -> false
                cells.size == headerCells.size -> true
                // Tab-separated rows can lose trailing empty cells (a row that
                // ends in an empty balance column draws nothing after its last
                // text); columns fill left to right, so pad the right side.
                line.contains('\t') -> {
                    cells = cells + List(headerCells.size - cells.size) { "" }
                    true
                }
                else -> false
            }
            if (!fits) {
                if (unparsed.size < MAX_UNPARSED_LINES) {
                    unparsed.add(line.replace(Regex("[\\s]+"), " ").trim())
                }
                continue
            }
            rows.add(buildRow(lineNumber = index + 1, cells = cells, columns = columns))
        }

        if (repeatedHeaders > 0) {
            notes.add("$repeatedHeaders repeated header lines were skipped")
        }
        if (rows.isEmpty()) {
            return PdfTableResult(
                rows = emptyList(),
                unparsedLines = unparsed,
                notes = notes,
                failure = StatementFailure(
                    StatementFailureReason.NO_TRANSACTIONS_DETECTED,
                    "table header found but no rows matched it"
                )
            )
        }
        return PdfTableResult(rows = rows, unparsedLines = unparsed, notes = notes, failure = null)
    }

    fun toCsvDocument(rows: List<StatementRow>): CsvDocument {
        val hasRef = rows.any { !it.externalRef.isNullOrBlank() }
        val hasBalance = rows.any { !it.balance.isNullOrBlank() }

        val header = mutableListOf("date", "description", "amount", "direction", "account")
        if (hasRef) header.add("external_ref")
        if (hasBalance) header.add("balance")

        val records = rows.map { row ->
            val values = mutableListOf(
                row.date, row.description, row.amount, row.direction, ""
            )
            if (hasRef) values.add(row.externalRef ?: "")
            if (hasBalance) values.add(row.balance ?: "")
            CsvRecord(lineNumber = row.lineNumber, values = values)
        }
        return CsvDocument(header = header, headerError = null, records = records)
    }

    private fun splitCells(line: String): List<String> =
        if (line.contains('\t')) {
            // Tab-separated extraction preserves empty cells (e.g. an empty
            // credit column), so the column count stays trustworthy.
            line.split('\t').map { it.trim() }
        } else {
            line.split(Regex("\\s{2,}|\\t")).map { it.trim() }.filter { it.isNotEmpty() }
        }

    private fun matchHeader(cells: List<String>): Map<Column, Int>? {
        val columns = mutableMapOf<Column, Int>()
        for ((index, cell) in cells.withIndex()) {
            val normalized = normalizeHeaderCell(cell)
            val column = when {
                normalized in dateTokens -> Column.DATE
                normalized in descriptionTokens -> Column.DESCRIPTION
                normalized in debitTokens -> Column.DEBIT
                normalized in creditTokens -> Column.CREDIT
                normalized in amountTokens -> Column.AMOUNT
                normalized in balanceTokens -> Column.BALANCE
                normalized in refTokens -> Column.REF
                else -> continue
            }
            if (columns.containsKey(column)) return null
            columns[column] = index
        }
        val hasAmountSource =
            Column.DEBIT in columns || Column.CREDIT in columns || Column.AMOUNT in columns
        if (Column.DATE !in columns || Column.DESCRIPTION !in columns || !hasAmountSource) {
            return null
        }
        return columns
    }

    private fun normalizeHeaderCell(cell: String): String {
        var normalized = cell.lowercase()
        normalized = normalized.removeSuffix(":")
        normalized = Regex("""\s*\((npr|npr\.|rs\.?|amount)\)\s*$""").replace(normalized, "")
        normalized = normalized.replace(Regex("[^a-z0-9]+"), " ")
        return normalized.replace(Regex("\\s+"), " ").trim()
    }

    private fun buildRow(lineNumber: Int, cells: List<String>, columns: Map<Column, Int>): StatementRow {
        val date = columns[Column.DATE]?.let { cells.getOrNull(it) } ?: ""
        val description = columns[Column.DESCRIPTION]?.let { cells.getOrNull(it) } ?: ""

        val debit = columns[Column.DEBIT]?.let { cells.getOrNull(it)?.trim() }.orEmpty()
        val credit = columns[Column.CREDIT]?.let { cells.getOrNull(it)?.trim() }.orEmpty()
        val amountCell = columns[Column.AMOUNT]?.let { cells.getOrNull(it)?.trim() }.orEmpty()

        var amount = ""
        var direction = ""
        val debitPresent = debit.isNotEmpty()
        val creditPresent = credit.isNotEmpty()

        when {
            debitPresent && creditPresent -> {
                amount = ""
                direction = ""
            }
            debitPresent -> {
                amount = debit
                direction = "OUTFLOW"
            }
            creditPresent -> {
                amount = credit
                direction = "INCOME"
            }
            amountCell.isNotEmpty() && amountCell.startsWith("-") -> {
                amount = amountCell
                direction = "OUTFLOW"
            }
            amountCell.isNotEmpty() && amountCell.startsWith("+") -> {
                amount = amountCell
                direction = "INCOME"
            }
            amountCell.isNotEmpty() -> {
                amount = amountCell
                direction = ""
            }
        }

        val balance = columns[Column.BALANCE]?.let { index ->
            cells.getOrNull(index)?.trim()?.ifEmpty { null }
        }
        val ref = columns[Column.REF]?.let { index ->
            cells.getOrNull(index)?.trim()?.ifEmpty { null }
        }

        return StatementRow(
            lineNumber = lineNumber,
            date = date,
            description = description,
            amount = amount,
            direction = direction,
            externalRef = ref,
            balance = balance
        )
    }
}

class PdfStatementParser : StatementParser {

    override fun parse(input: StatementInput): StatementParseOutcome {
        val bytes = (input as? StatementInput.PdfDocument)?.bytes
            ?: return StatementParseOutcome.Failed(
                StatementFailure(StatementFailureReason.READ_ERROR, "PDF input requires bytes")
            )

        val extracted = when (val outcome = PdfTextExtractor.extract(bytes)) {
            is PdfExtractOutcome.Failed ->
                return StatementParseOutcome.Failed(outcome.failure)
            is PdfExtractOutcome.Text -> outcome.text
        }

        val detection = StatementSourceDetector.detect(extracted)
        val table = PdfStatementTable.build(extracted)
        if (table.failure != null) {
            return StatementParseOutcome.Failed(table.failure)
        }

        return StatementParseOutcome.Parsed(
            StatementParseResult(
                format = StatementFormat.PDF,
                document = PdfStatementTable.toCsvDocument(table.rows),
                detection = detection,
                unparsedLines = table.unparsedLines,
                notes = table.notes
            )
        )
    }
}
