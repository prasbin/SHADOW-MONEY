package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {

    private fun parseOk(text: String): JsonValue {
        val result = BackupJson.parse(text)
        assertTrue("expected success but was $result", result is BackupJson.ParseResult.Ok)
        return (result as BackupJson.ParseResult.Ok).value
    }

    private fun parseError(text: String): String {
        val result = BackupJson.parse(text)
        assertTrue("expected malformed but was $result", result is BackupJson.ParseResult.Malformed)
        return (result as BackupJson.ParseResult.Malformed).reason
    }

    @Test
    fun parsesObjectArrayAndScalarTypes() {
        val value = parseOk("""{"a":[1,"x",true,false,null]}""")
        val obj = value as JsonValue.JsonObject
        val array = obj.fields["a"] as JsonValue.JsonArray
        assertEquals(JsonValue.JsonLong(1), array.items[0])
        assertEquals(JsonValue.JsonString("x"), array.items[1])
        assertEquals(JsonValue.JsonBool(true), array.items[2])
        assertEquals(JsonValue.JsonBool(false), array.items[3])
        assertEquals(JsonValue.JsonNull, array.items[4])
    }

    @Test
    fun parsesNegativeAndLargeLongsExactly() {
        assertEquals(JsonValue.JsonLong(-42), parseOk("-42"))
        assertEquals(JsonValue.JsonLong(9_007_199_254_740_993L), parseOk("9007199254740993"))
        assertEquals(JsonValue.JsonLong(Long.MIN_VALUE), parseOk("-9223372036854775808"))
        assertEquals(JsonValue.JsonLong(Long.MAX_VALUE), parseOk("9223372036854775807"))
    }

    @Test
    fun rejectsFractionalNumbers() {
        assertTrue(parseError("1.5").contains("whole integers"))
        assertTrue(parseError("""{"a":1.0}""").contains("whole integers"))
        assertTrue(parseError("1e3").contains("whole integers"))
    }

    @Test
    fun rejectsLeadingZeros() {
        parseError("01")
        assertTrue(parseError("01").contains("Leading zeros"))
    }

    @Test
    fun rejectsDuplicateObjectKeys() {
        assertTrue(parseError("""{"a":1,"a":2}""").contains("Duplicate object key"))
    }

    @Test
    fun rejectsTrailingContent() {
        assertTrue(parseError("""{"a":1} extra""").contains("trailing content"))
    }

    @Test
    fun rejectsUnterminatedStringAndBadEscape() {
        assertTrue(parseError("""{"a":"abc}""").contains("Unterminated string"))
        assertTrue(parseError("""{"a":"\q"}""").contains("Invalid escape"))
        assertTrue(parseError("""{"a":"\u12g4"}""").contains("Invalid unicode escape"))
    }

    @Test
    fun rejectsRawControlCharacterInString() {
        assertTrue(parseError("{\"a\":\"line\nbreak\"}").contains("control character"))
    }

    @Test
    fun rejectsExcessiveNesting() {
        val deep = "[".repeat(BackupJson.MAX_DEPTH + 2) + "]".repeat(BackupJson.MAX_DEPTH + 2)
        assertTrue(parseError(deep).contains("nests deeper"))
    }

    @Test
    fun rejectsEmptyDocument() {
        assertTrue(parseError("").contains("empty"))
        assertTrue(parseError("   ").contains("Unexpected character") || parseError("   ").contains("end of document"))
    }

    @Test
    fun writerEmitsCanonicalSortedWhitespaceFreeJson() {
        val value = JsonValue.JsonObject(
            linkedMapOf(
                "z" to JsonValue.JsonLong(1),
                "a" to JsonValue.JsonObject(linkedMapOf("b" to JsonValue.JsonString("x"), "a" to JsonValue.JsonNull)),
                "m" to JsonValue.JsonArray(listOf(JsonValue.JsonLong(-2), JsonValue.JsonBool(true)))
            )
        )
        assertEquals(
            """{"a":{"a":null,"b":"x"},"m":[-2,true],"z":1}""",
            BackupJson.write(value)
        )
    }

    @Test
    fun writerEscapesQuotesBackslashesAndControlCharacters() {
        val value = JsonValue.JsonObject(
            linkedMapOf("s" to JsonValue.JsonString("a\"b\\c\nd\te\u0001"))
        )
        assertEquals("""{"s":"a\"b\\c\nd\te\u0001"}""", BackupJson.write(value))
    }

    @Test
    fun writerRoundtripsUnicodeAndEmojis() {
        val original = JsonValue.JsonObject(linkedMapOf("s" to JsonValue.JsonString("काठमाडौं ☕ naïve")))
        assertEquals(original, parseOk(BackupJson.write(original)))
    }

    @Test
    fun parseWriteRoundtripIsStable() {
        val text = """{"b":[1,2],"a":{"c":"x\ny","d":null},"e":-9007199254740993}"""
        val first = BackupJson.write(parseOk(text))
        val second = BackupJson.write(parseOk(first))
        assertEquals(first, second)
        assertEquals(
            """{"a":{"c":"x\ny","d":null},"b":[1,2],"e":-9007199254740993}""",
            first
        )
    }
}
