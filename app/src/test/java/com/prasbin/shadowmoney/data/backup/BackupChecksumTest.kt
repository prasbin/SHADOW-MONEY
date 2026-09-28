package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupChecksumTest {

    private val payload = BackupTestData.richPayload()
    private val envelope = BackupBuilder.build(payload, BackupTestData.NOW)
    private val json = BackupSerializer.write(envelope)

    private fun readOutcome(text: String): EnvelopeReadOutcome =
        BackupSerializer.read(text, APP_SCHEMA_VERSION)

    private fun assertRejected(text: String, expected: BackupErrorCode): BackupError {
        val outcome = readOutcome(text)
        assertTrue("expected Rejected but was $outcome", outcome is EnvelopeReadOutcome.Rejected)
        val error = (outcome as EnvelopeReadOutcome.Rejected).error
        assertEquals(expected, error.code)
        return error
    }

    private fun tamper(from: String, to: String): String {
        assertTrue("fixture must contain '$from'", json.contains(from))
        return json.replace(from, to)
    }

    /** Adds whitespace only outside string literals, like a pretty-printer. */
    private fun reformat(text: String): String {
        val builder = StringBuilder()
        var inString = false
        var escaped = false
        for (char in text) {
            if (inString) {
                builder.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else when (char) {
                '"' -> {
                    inString = true
                    builder.append(char)
                }
                '{', '}', '[', ']', ',', ':' -> {
                    builder.append(' ')
                    builder.append(char)
                    builder.append(' ')
                }
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun scramble(value: JsonValue): JsonValue = when (value) {
        is JsonValue.JsonObject -> JsonValue.JsonObject(
            value.fields.entries.reversed().map { it.key to scramble(it.value) }.toMap()
        )
        is JsonValue.JsonArray -> JsonValue.JsonArray(value.items.map { scramble(it) })
        else -> value
    }

    /** Writes JSON in insertion order (not sorted) to model foreign encoders. */
    private fun writeUnsorted(value: JsonValue): String = when (value) {
        is JsonValue.JsonObject -> value.fields.entries.joinToString(separator = ",", prefix = "{", postfix = "}") {
            "${BackupJson.write(JsonValue.JsonString(it.key))}:${writeUnsorted(it.value)}"
        }
        is JsonValue.JsonArray -> value.items.joinToString(separator = ",", prefix = "[", postfix = "]") {
            writeUnsorted(it)
        }
        else -> BackupJson.write(value)
    }

    @Test
    fun matchesStandardSha256Vectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BackupChecksum.ofCanonicalJson("")
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            BackupChecksum.ofCanonicalJson("abc")
        )
    }

    @Test
    fun validBackup_verifiesChecksum() {
        val outcome = readOutcome(json)
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
    }

    @Test
    fun checksumHashesExactlyTheCanonicalPayloadBytes() {
        val canonicalPayload = BackupJson.write(BackupSerializer.payloadTree(payload))
        assertEquals(BackupChecksum.ofCanonicalJson(canonicalPayload), envelope.checksum.value)
    }

    @Test
    fun tamperedPayloadValue_isRejectedWithChecksumMismatch() {
        assertRejected(tamper("\"amountMinor\":12345", "\"amountMinor\":12346"),
            BackupErrorCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun tamperedRecordReference_isRejectedWithChecksumMismatch() {
        assertRejected(tamper("\"accountId\":1,", "\"accountId\":7,"),
            BackupErrorCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun tamperedDeclaredChecksum_isRejected() {
        val other = BackupChecksum.ofCanonicalJson("something else")
        assertRejected(tamper(envelope.checksum.value, other), BackupErrorCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun malformedDeclaredChecksum_isRejected() {
        assertRejected(tamper(envelope.checksum.value, envelope.checksum.value.dropLast(8)),
            BackupErrorCode.CHECKSUM_MISMATCH)
    }

    @Test
    fun unsupportedChecksumAlgorithm_isRejectedAsUnsupportedFormat() {
        val text = json.replace(
            "\"algorithm\":\"$BACKUP_CHECKSUM_ALGORITHM\"",
            "\"algorithm\":\"MD5\""
        )
        assertRejected(text, BackupErrorCode.UNSUPPORTED_FORMAT)
    }

    @Test
    fun reformattedWhitespace_stillVerifiesBecauseHashUsesCanonicalForm() {
        val outcome = readOutcome(reformat(json))
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
    }

    @Test
    fun reorderedKeys_stillVerifyBecauseHashUsesCanonicalForm() {
        val root = (BackupJson.parse(json) as BackupJson.ParseResult.Ok).value as JsonValue.JsonObject
        val scrambledRoot = scramble(root) as JsonValue.JsonObject
        val text = writeUnsorted(scrambledRoot)
        val outcome = readOutcome(text)
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
    }

    @Test
    fun uppercaseChecksumHex_isAcceptedCaseInsensitively() {
        val text = json.replace(envelope.checksum.value, envelope.checksum.value.uppercase())
        val outcome = readOutcome(text)
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
    }

    @Test
    fun checksumComparison_rejectsDifferentValues() {
        val a = BackupChecksum.ofCanonicalJson("a")
        val b = BackupChecksum.ofCanonicalJson("b")
        assertFalse(BackupChecksum.matches(a, b))
        assertTrue(BackupChecksum.matches(a, a.uppercase()))
        assertFalse(BackupChecksum.matches(a, a.dropLast(1)))
    }

    @Test
    fun emptyBackup_checksumVerifies() {
        val outcome = readOutcome(BackupTestData.write(BackupTestData.emptyPayload()))
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
    }
}
