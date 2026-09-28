package com.prasbin.shadowmoney.data.imports

/**
 * Deterministic CSV parser (RFC 4180 style, bounded).
 *
 * Rules:
 * - Quoted fields may contain commas, newlines and escaped quotes ("").
 * - Line endings LF, CRLF and lone CR are all accepted.
 * - Blank lines (no fields, or a single empty/whitespace-only field) are skipped.
 * - A leading UTF-8 BOM is stripped.
 * - A record that cannot be safely interpreted is reported as an invalid record
 *   with a reason; rows are never silently guessed or dropped.
 */
data class CsvRecord(
    val lineNumber: Int,
    val values: List<String>,
    val parseError: String? = null
)

data class CsvDocument(
    val header: List<String>?,
    val headerError: String?,
    val records: List<CsvRecord>
)

object CsvParser {

    fun parse(text: String): CsvDocument {
        val source = stripBom(text)
        if (source.isEmpty()) {
            return CsvDocument(
                header = null,
                headerError = "Missing header row: input is empty",
                records = emptyList()
            )
        }

        val records = mutableListOf<CsvRecord>()
        val values = mutableListOf<String>()
        val field = StringBuilder()
        val length = source.length
        var inQuotes = false
        var quoteClosedField = false
        var malformed: String? = null
        var lineNumber = 1
        var recordStartLine = 1
        var recordHasContent = false

        fun endField() {
            values.add(field.toString())
            field.setLength(0)
            quoteClosedField = false
        }

        fun endRecord() {
            endField()
            val singleBlank = values.size == 1 && values[0].isEmpty()
            val singleWhitespace = values.size == 1 && values[0].isBlank()
            if (malformed != null || (!singleBlank && !singleWhitespace)) {
                records.add(CsvRecord(recordStartLine, values.toList(), malformed))
            }
            values.clear()
            malformed = null
            recordHasContent = false
            recordStartLine = lineNumber
        }

        var i = 0
        while (i < length) {
            val c = source[i]
            when {
                inQuotes -> {
                    when {
                        c == '"' -> {
                            if (i + 1 < length && source[i + 1] == '"') {
                                field.append('"')
                                i += 2
                            } else {
                                inQuotes = false
                                quoteClosedField = true
                                i++
                            }
                        }
                        c == '\n' -> {
                            field.append('\n')
                            lineNumber++
                            i++
                        }
                        c == '\r' -> {
                            field.append('\n')
                            lineNumber++
                            if (i + 1 < length && source[i + 1] == '\n') i += 2 else i++
                        }
                        else -> {
                            field.append(c)
                            i++
                        }
                    }
                    recordHasContent = true
                }
                quoteClosedField -> when {
                    c == ',' -> {
                        endField()
                        i++
                    }
                    c == '\n' || c == '\r' -> {
                        if (c == '\r' && i + 1 < length && source[i + 1] == '\n') i += 2 else i++
                        lineNumber++
                        endRecord()
                    }
                    else -> {
                        malformed = malformed ?: "Unexpected character after closing quote"
                        field.append(c)
                        quoteClosedField = false
                        recordHasContent = true
                        i++
                    }
                }
                c == '"' -> {
                    if (field.length == 0) {
                        inQuotes = true
                    } else {
                        malformed = malformed ?: "Unexpected quote character in unquoted field"
                        field.append(c)
                    }
                    recordHasContent = true
                    i++
                }
                c == ',' -> {
                    endField()
                    recordHasContent = true
                    i++
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < length && source[i + 1] == '\n') i += 2 else i++
                    lineNumber++
                    endRecord()
                }
                else -> {
                    field.append(c)
                    recordHasContent = true
                    i++
                }
            }
        }

        if (inQuotes && malformed == null) {
            malformed = "Unterminated quoted field"
        }
        if (recordHasContent || field.length > 0 || values.isNotEmpty() || malformed != null) {
            endRecord()
        }

        val headerRecord = records.firstOrNull()
            ?: return CsvDocument(
                header = null,
                headerError = "Missing header row: no header found",
                records = emptyList()
            )
        if (headerRecord.parseError != null) {
            return CsvDocument(
                header = null,
                headerError = "Malformed header row: ${headerRecord.parseError}",
                records = emptyList()
            )
        }
        return CsvDocument(
            header = headerRecord.values,
            headerError = null,
            records = records.drop(1)
        )
    }

    private fun stripBom(text: String): String =
        if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text
}
