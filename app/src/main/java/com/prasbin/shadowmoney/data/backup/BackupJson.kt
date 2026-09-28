package com.prasbin.shadowmoney.data.backup

/**
 * Minimal strict JSON used only by the local backup format.
 *
 * Parser rules (deliberately stricter than general JSON):
 * - numbers must be whole integers (no fractions or exponents); money stays
 *   in exact minor units and can never become a floating point value
 * - duplicate object keys are rejected (checksum ambiguity)
 * - trailing content, raw control characters and excessive nesting rejected
 *
 * Writer rules (deterministic, used for both export and checksum input):
 * - object keys are emitted in sorted order
 * - no insignificant whitespace
 * - [Long] values are written as plain digits
 */
sealed interface JsonValue {
    data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue
    data class JsonArray(val items: List<JsonValue>) : JsonValue
    data class JsonString(val value: String) : JsonValue
    data class JsonLong(val value: Long) : JsonValue
    data class JsonBool(val value: Boolean) : JsonValue
    data object JsonNull : JsonValue
}

object BackupJson {

    const val MAX_DEPTH = 32

    sealed interface ParseResult {
        data class Ok(val value: JsonValue) : ParseResult
        data class Malformed(val reason: String) : ParseResult
    }

    fun parse(text: String): ParseResult {
        if (text.isEmpty()) return ParseResult.Malformed("The document is empty")
        val parser = Parser(text)
        val value = parser.parseDocument() ?: return ParseResult.Malformed(parser.reason)
        return ParseResult.Ok(value)
    }

    fun write(value: JsonValue): String {
        val builder = StringBuilder()
        writeValue(value, builder)
        return builder.toString()
    }

    private fun writeValue(value: JsonValue, builder: StringBuilder) {
        when (value) {
            is JsonValue.JsonObject -> {
                builder.append('{')
                val sorted = value.fields.entries.sortedBy { it.key }
                var first = true
                for ((key, child) in sorted) {
                    if (!first) builder.append(',')
                    first = false
                    writeString(key, builder)
                    builder.append(':')
                    writeValue(child, builder)
                }
                builder.append('}')
            }
            is JsonValue.JsonArray -> {
                builder.append('[')
                var first = true
                for (item in value.items) {
                    if (!first) builder.append(',')
                    first = false
                    writeValue(item, builder)
                }
                builder.append(']')
            }
            is JsonValue.JsonString -> writeString(value.value, builder)
            is JsonValue.JsonLong -> builder.append(value.value.toString())
            is JsonValue.JsonBool -> builder.append(if (value.value) "true" else "false")
            is JsonValue.JsonNull -> builder.append("null")
        }
    }

    private fun writeString(value: String, builder: StringBuilder) {
        builder.append('"')
        for (char in value) {
            when (char) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        builder.append("\\u")
                        builder.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        builder.append(char)
                    }
                }
            }
        }
        builder.append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0
        var reason: String = "The document could not be parsed"
            private set

        fun parseDocument(): JsonValue? {
            skipWhitespace()
            val value = parseValue(0) ?: return null
            skipWhitespace()
            if (index != text.length) {
                reason = "Unexpected trailing content"
                return null
            }
            return value
        }

        private fun parseValue(depth: Int): JsonValue? {
            if (depth > MAX_DEPTH) {
                reason = "The document nests deeper than $MAX_DEPTH levels"
                return null
            }
            if (index >= text.length) {
                reason = "Unexpected end of document"
                return null
            }
            return when (text[index]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> parseString()
                't' -> parseKeyword("true", JsonValue.JsonBool(true))
                'f' -> parseKeyword("false", JsonValue.JsonBool(false))
                'n' -> parseKeyword("null", JsonValue.JsonNull)
                in '0'..'9', '-' -> parseNumber()
                else -> {
                    reason = "Unexpected character at position $index"
                    null
                }
            }
        }

        private fun parseObject(depth: Int): JsonValue? {
            index++ // consume '{'
            skipWhitespace()
            val fields = LinkedHashMap<String, JsonValue>()
            if (peek() == '}') {
                index++
                return JsonValue.JsonObject(fields)
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') {
                    reason = "Expected an object key at position $index"
                    return null
                }
                val key = parseString() as? JsonValue.JsonString ?: return null
                if (fields.containsKey(key.value)) {
                    reason = "Duplicate object key"
                    return null
                }
                skipWhitespace()
                if (peek() != ':') {
                    reason = "Expected ':' at position $index"
                    return null
                }
                index++
                skipWhitespace()
                val child = parseValue(depth + 1) ?: return null
                fields[key.value] = child
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonValue.JsonObject(fields)
                    }
                    else -> {
                        reason = "Expected ',' or '}' at position $index"
                        return null
                    }
                }
            }
        }

        private fun parseArray(depth: Int): JsonValue? {
            index++ // consume '['
            skipWhitespace()
            val items = ArrayList<JsonValue>()
            if (peek() == ']') {
                index++
                return JsonValue.JsonArray(items)
            }
            while (true) {
                skipWhitespace()
                val child = parseValue(depth + 1) ?: return null
                items.add(child)
                skipWhitespace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonValue.JsonArray(items)
                    }
                    else -> {
                        reason = "Expected ',' or ']' at position $index"
                        return null
                    }
                }
            }
        }

        private fun parseString(): JsonValue? {
            index++ // consume opening quote
            val builder = StringBuilder()
            while (true) {
                if (index >= text.length) {
                    reason = "Unterminated string"
                    return null
                }
                val char = text[index]
                when {
                    char == '"' -> {
                        index++
                        return JsonValue.JsonString(builder.toString())
                    }
                    char == '\\' -> {
                        index++
                        if (index >= text.length) {
                            reason = "Unterminated escape sequence"
                            return null
                        }
                        when (val escaped = text[index]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 >= text.length) {
                                    reason = "Incomplete unicode escape"
                                    return null
                                }
                                val hex = text.substring(index + 1, index + 5)
                                val code = hex.toIntOrNull(16)
                                if (code == null || hex.any { it !in "0123456789abcdefABCDEF" }) {
                                    reason = "Invalid unicode escape"
                                    return null
                                }
                                builder.append(code.toChar())
                                index += 4
                            }
                            else -> {
                                reason = "Invalid escape sequence"
                                return null
                            }
                        }
                        index++
                    }
                    char.code < 0x20 -> {
                        reason = "Raw control character in string"
                        return null
                    }
                    else -> {
                        builder.append(char)
                        index++
                    }
                }
            }
        }

        private fun parseNumber(): JsonValue? {
            val start = index
            if (peek() == '-') index++
            if (index >= text.length || text[index] !in '0'..'9') {
                reason = "Invalid number at position $start"
                return null
            }
            if (text[index] == '0') {
                index++
                if (index < text.length && text[index] in '0'..'9') {
                    reason = "Leading zeros are not allowed"
                    return null
                }
            } else {
                while (index < text.length && text[index] in '0'..'9') index++
            }
            if (index < text.length && (text[index] == '.' || text[index] == 'e' || text[index] == 'E')) {
                reason = "Numbers must be whole integers"
                return null
            }
            val value = text.substring(start, index).toLongOrNull()
            if (value == null) {
                reason = "Number out of range at position $start"
                return null
            }
            return JsonValue.JsonLong(value)
        }

        private fun parseKeyword(keyword: String, value: JsonValue): JsonValue? {
            if (text.regionMatches(index, keyword, 0, keyword.length)) {
                index += keyword.length
                return value
            }
            reason = "Unexpected keyword at position $index"
            return null
        }

        private fun peek(): Char? = if (index < text.length) text[index] else null

        private fun skipWhitespace() {
            while (index < text.length && text[index] in " \t\n\r") index++
        }
    }
}
