package com.prasbin.shadowmoney.data.statements

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Minimal, bounded, on-device PDF text extraction for statement ingestion.
 *
 * Scope (deliberately conservative — limitations are reported, never hidden):
 * - Reads only bytes the user selected; never touches the network.
 * - Rejects encrypted PDFs (`/Encrypt`) with a clear, safe failure — banking
 *   passwords are never requested or stored.
 * - Inflates FlateDecode content streams, understands basic text-showing
 *   operators (Tj, TJ, ', ") and text positioning (Td, TD, Tm, T*, BT, ET).
 * - Applies ToUnicode CMap character maps when exactly one consistent mapping
 *   exists; otherwise falls back to raw codes and lets the text-quality
 *   filter decide.
 * - No OCR: scanned/image-only PDFs fail with NO_TEXT_EXTRACTED.
 * - Bounded: file size, stream count and output length all have hard limits.
 */
sealed interface PdfExtractOutcome {
    data class Text(val text: String) : PdfExtractOutcome
    data class Failed(val failure: StatementFailure) : PdfExtractOutcome
}

object PdfTextExtractor {

    const val MAX_PDF_BYTES = 5 * 1024 * 1024
    private const val MAX_STREAMS = 8_192
    private const val MAX_TEXT_CHARS = 4_000_000
    private const val MAX_CMAP_CHARS = 2_000_000

    fun extract(bytes: ByteArray): PdfExtractOutcome {
        if (bytes.isEmpty()) {
            return PdfExtractOutcome.Failed(StatementFailure(StatementFailureReason.EMPTY_DOCUMENT))
        }
        if (bytes.size > MAX_PDF_BYTES) {
            return PdfExtractOutcome.Failed(StatementFailure(StatementFailureReason.FILE_TOO_LARGE))
        }
        val raw = String(bytes, Charsets.ISO_8859_1)
        if ("%PDF-" !in raw.take(1024)) {
            return PdfExtractOutcome.Failed(
                StatementFailure(StatementFailureReason.MALFORMED_PDF, "missing %PDF header")
            )
        }
        if ("/Encrypt" in raw) {
            return PdfExtractOutcome.Failed(StatementFailure(StatementFailureReason.ENCRYPTED_PDF))
        }

        val decodedStreams = collectDecodedStreams(raw)
        val mapping = buildToUnicodeMapping(decodedStreams)

        val out = StringBuilder()
        var sawTextOperator = false
        for (stream in decodedStreams) {
            if (out.length >= MAX_TEXT_CHARS) break
            if (!looksLikeContentStream(stream)) continue
            if (extractFromContent(stream, mapping, out)) sawTextOperator = true
        }

        val text = out.toString()
        if (!sawTextOperator || !looksLikeReadableText(text)) {
            return PdfExtractOutcome.Failed(StatementFailure(StatementFailureReason.NO_TEXT_EXTRACTED))
        }
        return PdfExtractOutcome.Text(text)
    }

    // ---------------------------------------------------------------- streams

    private fun collectDecodedStreams(raw: String): List<String> {
        val streams = mutableListOf<String>()
        var cursor = 0
        var count = 0
        while (count < MAX_STREAMS) {
            val keyword = raw.indexOf("stream", cursor)
            if (keyword < 0) break
            if (keyword >= 3 && raw.substring(keyword - 3, keyword) == "end") {
                cursor = keyword + 6
                continue
            }
            var start = keyword + 6
            if (start < raw.length && raw[start] == '\r') start++
            if (start < raw.length && raw[start] == '\n') start++
            val end = raw.indexOf("endstream", start)
            if (end < 0) break

            val dictStart = raw.lastIndexOf("<<", keyword)
            val dict = if (dictStart in 0 until keyword) raw.substring(dictStart, keyword) else ""
            val isImage = IMAGE_CODECS.any { it in dict }

            if (!isImage) {
                val payload = raw.substring(start, end)
                val decoded = if ("/FlateDecode" in dict) {
                    inflate(latin1ToBytes(payload))?.let { String(it, Charsets.ISO_8859_1) }
                } else {
                    payload
                }
                if (decoded != null && decoded.isNotEmpty()) {
                    streams.add(decoded)
                }
            }
            cursor = end + 9
            count++
        }
        return streams
    }

    private val IMAGE_CODECS = listOf("/DCTDecode", "/JPXDecode", "/CCITTFaxDecode", "/JBIG2Decode")

    private fun latin1ToBytes(text: String): ByteArray {
        val bytes = ByteArray(text.length)
        for (i in text.indices) {
            bytes[i] = text[i].code.toByte()
        }
        return bytes
    }

    private fun inflate(data: ByteArray): ByteArray? {
        for (rawMode in booleanArrayOf(false, true)) {
            try {
                val inflater = Inflater(rawMode)
                inflater.setInput(data)
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                var idleRounds = 0
                while (!inflater.finished()) {
                    val n = inflater.inflate(buffer)
                    if (n > 0) {
                        idleRounds = 0
                        out.write(buffer, 0, n)
                        if (out.size() > MAX_TEXT_CHARS) break
                    } else {
                        if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary()) break
                        idleRounds++
                        if (idleRounds > 2) break
                    }
                }
                val result = out.toByteArray()
                inflater.end()
                if (result.isNotEmpty()) return result
            } catch (_: Exception) {
                // try the next inflate mode
            }
        }
        return null
    }

    // -------------------------------------------------------------- ToUnicode

    private fun buildToUnicodeMapping(streams: List<String>): Map<Int, String>? {
        var merged: MutableMap<Int, String>? = null
        var conflict = false
        for (stream in streams) {
            if (stream.length > MAX_CMAP_CHARS) continue
            if ("begincmap" !in stream) continue
            val parsed = parseCMap(stream) ?: continue
            if (merged == null) {
                merged = HashMap(parsed)
            } else {
                for ((code, value) in parsed) {
                    val existing = merged.put(code, value)
                    if (existing != null && existing != value) {
                        conflict = true
                    }
                }
            }
        }
        return if (conflict) null else merged
    }

    private fun parseCMap(text: String): Map<Int, String>? {
        val map = HashMap<Int, String>()
        try {
            BFCHAR_BLOCK.findAll(text).forEach { block ->
                CHAR_PAIR.findAll(block.groupValues[1]).forEach { pair ->
                    val src = pair.groupValues[1].toLongOrNull(16) ?: return@forEach
                    val dst = hexToUtf16(pair.groupValues[2]) ?: return@forEach
                    map[src.toInt()] = dst
                }
            }
            BFRANGE_BLOCK.findAll(text).forEach { block ->
                val body = block.groupValues[1]
                SINGLE_RANGE.findAll(body).forEach { range ->
                    val from = range.groupValues[1].toLongOrNull(16) ?: return@forEach
                    val to = range.groupValues[2].toLongOrNull(16) ?: return@forEach
                    val base = hexToUtf16(range.groupValues[3]) ?: return@forEach
                    if (to - from > 65_535) return@forEach
                    var offset = 0L
                    var code = from
                    while (code <= to) {
                        map[code.toInt()] = incrementLastCodeUnit(base, offset)
                        code++
                        offset++
                    }
                }
                ARRAY_RANGE.findAll(body).forEach { range ->
                    val from = range.groupValues[1].toLongOrNull(16) ?: return@forEach
                    val to = range.groupValues[2].toLongOrNull(16) ?: return@forEach
                    val targets = CHAR_ONLY.findAll(range.groupValues[3]).map { it.groupValues[1] }.toList()
                    if (to - from + 1 != targets.size.toLong()) return@forEach
                    targets.forEachIndexed { index, hex ->
                        val dst = hexToUtf16(hex) ?: return@forEach
                        map[(from + index).toInt()] = dst
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return map.ifEmpty { null }
    }

    private val BFCHAR_BLOCK = Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL)
    private val BFRANGE_BLOCK = Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL)
    private val CHAR_PAIR = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
    private val SINGLE_RANGE = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
    private val ARRAY_RANGE = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*\[([^\]]*)\]""")
    private val CHAR_ONLY = Regex("""<([0-9A-Fa-f]+)>""")

    private fun hexToUtf16(hex: String): String? {
        if (hex.isEmpty() || hex.length % 2 != 0) return null
        // ToUnicode destinations are UTF-16BE code units: four hex digits per
        // BMP character ("005A" is 'Z', not NUL followed by 'Z'). Two-digit
        // groupings are only tolerated for short hand-written maps.
        val step = if (hex.length % 4 == 0) 4 else 2
        val builder = StringBuilder(hex.length / step)
        var i = 0
        while (i + step <= hex.length) {
            val code = hex.substring(i, i + step).toIntOrNull(16) ?: return null
            builder.append(code.toChar())
            i += step
        }
        return builder.toString()
    }

    private fun incrementLastCodeUnit(base: String, delta: Long): String {
        if (delta == 0L || base.isEmpty()) return base
        val builder = StringBuilder(base)
        val last = builder[builder.length - 1].code + delta.toInt()
        builder.setLength(builder.length - 1)
        builder.append((last and 0xFFFF).toChar())
        return builder.toString()
    }

    // -------------------------------------------------------- content parsing

    private fun looksLikeContentStream(text: String): Boolean {
        val sample = if (text.length > 65_536) text.take(65_536) else text
        return "BT" in sample || "Tj" in sample || "TJ" in sample
    }

    private fun extractFromContent(
        content: String,
        mapping: Map<Int, String>?,
        out: StringBuilder
    ): Boolean {
        var sawTextOp = false
        val stack = ArrayList<Any>()
        var i = 0
        val n = content.length
        var lastTmE: Double? = null
        var lastTmF: Double? = null

        while (i < n && out.length < MAX_TEXT_CHARS) {
            val c = content[i]
            when {
                c.isWhitespace() -> i++
                c == '%' -> {
                    while (i < n && content[i] != '\n' && content[i] != '\r') i++
                }
                c == '(' -> {
                    val (value, next) = readLiteralString(content, i)
                    stack.add(value)
                    i = next
                }
                c == '<' -> {
                    if (i + 1 < n && content[i + 1] == '<') {
                        val close = content.indexOf(">>", i + 2)
                        i = if (close < 0) n else close + 2
                    } else {
                        val (value, next) = readHexString(content, i)
                        stack.add(value)
                        i = next
                    }
                }
                c == '[' -> {
                    val (value, next) = readArray(content, i)
                    stack.add(value)
                    i = next
                }
                c == ']' || c == ')' -> i++
                c == '/' -> {
                    i++
                    while (i < n && !content[i].isWhitespace() && content[i] !in "()<>[]{}/%") i++
                    stack.add(NameToken)
                }
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    val start = i
                    i++
                    while (i < n && (content[i].isDigit() || content[i] == '.' || content[i] == '-' || content[i] == '+')) i++
                    val num = content.substring(start, i).toDoubleOrNull()
                    if (num != null) stack.add(num)
                }
                else -> {
                    val start = i
                    while (i < n && !content[i].isWhitespace() && content[i] !in "()<>[]{}/%") i++
                    val op = content.substring(start, i)
                    when (op) {
                        "Tj" -> {
                            val text = stack.filterIsInstance<String>().lastOrNull()
                            if (text != null) {
                                appendMapped(out, text, mapping)
                                sawTextOp = true
                            }
                        }
                        "'" -> {
                            val text = stack.filterIsInstance<String>().lastOrNull()
                            if (text != null) {
                                appendMapped(out, text, mapping)
                                sawTextOp = true
                            }
                            appendNewline(out)
                        }
                        "\"" -> {
                            val text = stack.filterIsInstance<String>().lastOrNull()
                            if (text != null) {
                                appendMapped(out, text, mapping)
                                sawTextOp = true
                            }
                            appendNewline(out)
                        }
                        "TJ" -> {
                            val items = stack.filterIsInstance<List<Any>>().lastOrNull()
                            if (items != null) {
                                for (item in items) {
                                    when (item) {
                                        is String -> appendMapped(out, item, mapping)
                                        is Double -> if (item <= -100.0) appendSpace(out)
                                    }
                                }
                                sawTextOp = true
                            }
                        }
                        "Td", "TD" -> {
                            val nums = stack.filterIsInstance<Double>()
                            val ty = nums.getOrNull(nums.size - 1)
                            val tx = nums.getOrNull(nums.size - 2)
                            if (ty != null && ty != 0.0) appendNewline(out)
                            else if (tx != null && tx != 0.0) appendTab(out)
                        }
                        "Tm" -> {
                            val nums = stack.filterIsInstance<Double>()
                            val e = nums.getOrNull(nums.size - 2)
                            val f = nums.getOrNull(nums.size - 1)
                            when {
                                f == null -> appendNewline(out)
                                lastTmF == null || f != lastTmF -> appendNewline(out)
                                e != null && lastTmE != null && e != lastTmE -> appendTab(out)
                            }
                            lastTmE = e
                            lastTmF = f
                        }
                        "T*" -> appendNewline(out)
                        "BT" -> {
                            appendNewline(out)
                            lastTmE = null
                            lastTmF = null
                        }
                        "ET" -> appendNewline(out)
                    }
                    stack.clear()
                }
            }
        }
        return sawTextOp
    }

    private object NameToken

    private fun appendMapped(out: StringBuilder, value: String, mapping: Map<Int, String>?) {
        if (mapping == null || mapping.isEmpty()) {
            out.append(value)
            return
        }
        val twoByte = mapping.keys.any { it > 0xFF }
        if (!twoByte) {
            for (ch in value) {
                out.append(mapping[ch.code] ?: ch)
            }
            return
        }
        var i = 0
        while (i < value.length) {
            if (i + 1 < value.length) {
                val code = (value[i].code shl 8) or value[i + 1].code
                val mapped = mapping[code]
                if (mapped != null) {
                    out.append(mapped)
                    i += 2
                    continue
                }
            }
            out.append(value[i])
            i++
        }
    }

    private fun appendNewline(out: StringBuilder) {
        if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n')
    }

    private fun appendSpace(out: StringBuilder) {
        if (out.isEmpty()) return
        val last = out[out.length - 1]
        if (last != '\n' && last != ' ' && last != '\t') out.append(' ')
    }

    /**
     * A same-line positioning move is a column boundary: tabs preserve empty
     * cells (e.g. a row with no credit amount), which a space split destroys.
     * Consecutive moves without text between them are real empty cells and
     * keep their tab; only line starts stay clean.
     */
    private fun appendTab(out: StringBuilder) {
        if (out.isEmpty()) return
        if (out[out.length - 1] == '\n') return
        out.append('\t')
    }

    private fun readLiteralString(content: String, start: Int): Pair<String, Int> {
        val builder = StringBuilder()
        var depth = 0
        var i = start + 1
        val n = content.length
        while (i < n) {
            val c = content[i]
            when {
                c == '\\' -> {
                    val next = content.getOrNull(i + 1)
                    when (next) {
                        null -> i++
                        'n' -> { builder.append('\n'); i += 2 }
                        'r' -> { builder.append('\r'); i += 2 }
                        't' -> { builder.append('\t'); i += 2 }
                        'b' -> { builder.append('\b'); i += 2 }
                        'f' -> { builder.append('\u000C'); i += 2 }
                        '(', ')', '\\' -> { builder.append(next); i += 2 }
                        '\r' -> {
                            i += 2
                            if (i < n && content[i] == '\n') i++
                        }
                        '\n' -> i += 2
                        in '0'..'7' -> {
                            var value = 0
                            var digits = 0
                            while (i + 1 + digits < n && digits < 3 && content[i + 1 + digits] in '0'..'7') {
                                value = value * 8 + (content[i + 1 + digits] - '0')
                                digits++
                            }
                            builder.append((value and 0xFF).toChar())
                            i += 1 + digits
                        }
                        else -> { builder.append(next); i += 2 }
                    }
                }
                c == '(' -> { depth++; builder.append(c); i++ }
                c == ')' -> {
                    if (depth == 0) {
                        i++
                        return builder.toString() to i
                    }
                    depth--
                    builder.append(c)
                    i++
                }
                else -> { builder.append(c); i++ }
            }
        }
        return builder.toString() to i
    }

    private fun readHexString(content: String, start: Int): Pair<String, Int> {
        val builder = StringBuilder()
        var i = start + 1
        val n = content.length
        var nibble = -1
        while (i < n && content[i] != '>') {
            val c = content[i]
            val digit = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'f' -> c - 'a' + 10
                in 'A'..'F' -> c - 'A' + 10
                else -> -1
            }
            if (digit >= 0) {
                if (nibble < 0) {
                    nibble = digit
                } else {
                    builder.append(((nibble shl 4) or digit).toChar())
                    nibble = -1
                }
            }
            i++
        }
        if (nibble >= 0) builder.append((nibble shl 4).toChar())
        if (i < n && content[i] == '>') i++
        return builder.toString() to i
    }

    private fun readArray(content: String, start: Int): Pair<List<Any>, Int> {
        val items = ArrayList<Any>()
        var depth = 0
        var i = start + 1
        val n = content.length
        while (i < n) {
            val c = content[i]
            when {
                c.isWhitespace() -> i++
                c == ']' -> {
                    if (depth == 0) return items to (i + 1)
                    depth--
                    i++
                }
                c == '[' -> {
                    val (nested, next) = readArray(content, i)
                    items.add(nested)
                    i = next
                }
                c == '(' -> {
                    val (value, next) = readLiteralString(content, i)
                    items.add(value)
                    i = next
                }
                c == '<' -> {
                    if (i + 1 < n && content[i + 1] == '<') {
                        val close = content.indexOf(">>", i + 2)
                        i = if (close < 0) n else close + 2
                    } else {
                        val (value, next) = readHexString(content, i)
                        items.add(value)
                        i = next
                    }
                }
                c == '/' -> {
                    i++
                    while (i < n && !content[i].isWhitespace() && content[i] !in "()<>[]{}/%") i++
                }
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    val numStart = i
                    i++
                    while (i < n && (content[i].isDigit() || content[i] == '.' || content[i] == '-' || content[i] == '+')) i++
                    content.substring(numStart, i).toDoubleOrNull()?.let { items.add(it) }
                }
                else -> {
                    while (i < n && !content[i].isWhitespace() && content[i] !in "()<>[]{}/%") i++
                }
            }
        }
        return items to i
    }

    private fun looksLikeReadableText(text: String): Boolean {
        if (text.isBlank()) return false
        var good = 0
        var total = 0
        for (ch in text) {
            total++
            val code = ch.code
            if (
                ch.isWhitespace() ||
                code in 32..126 ||
                code in 0xA0..0xFF ||
                code in 0x900..0x97F ||
                code in 0x2000..0x206F
            ) {
                good++
            }
        }
        return total > 0 && good.toDouble() / total >= 0.85
    }
}
