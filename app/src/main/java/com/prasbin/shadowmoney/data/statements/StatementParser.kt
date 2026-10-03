package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.imports.CsvParser

/**
 * Parses one user-selected statement document into a normalized
 * [StatementParseOutcome]. Implementations perform no database access and
 * never contact a network: the file bytes the user picked through the
 * Storage Access Framework are processed entirely on device.
 */
interface StatementParser {
    fun parse(input: StatementInput): StatementParseOutcome
}

/**
 * CSV statements reuse the existing [CsvParser] so pasted text, picked CSV
 * files and statement-derived rows all behave identically. Header and row
 * validation remain the [com.prasbin.shadowmoney.data.imports.ImportEngine]
 * job: the parser only supplies the document plus source detection.
 */
class CsvStatementParser : StatementParser {

    override fun parse(input: StatementInput): StatementParseOutcome {
        val text = (input as? StatementInput.TextDocument)?.text
            ?: return StatementParseOutcome.Failed(
                StatementFailure(StatementFailureReason.READ_ERROR, "CSV input requires text")
            )

        val document = CsvParser.parse(text)
        val detection = StatementSourceDetector.detect(text)
        return StatementParseOutcome.Parsed(
            StatementParseResult(
                format = input.format,
                document = document,
                detection = detection,
                unparsedLines = emptyList(),
                notes = emptyList()
            )
        )
    }
}

object StatementParserRegistry {

    fun parserFor(format: StatementFormat): StatementParser = when (format) {
        StatementFormat.PDF -> PdfStatementParser()
        StatementFormat.CSV, StatementFormat.PASTED -> CsvStatementParser()
    }
}
