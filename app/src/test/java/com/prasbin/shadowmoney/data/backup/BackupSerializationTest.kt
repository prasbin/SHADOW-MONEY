package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializationTest {

    private fun readOk(text: String, schema: Int = APP_SCHEMA_VERSION): BackupEnvelope {
        val outcome = BackupSerializer.read(text, schema)
        assertTrue("expected Ok but was $outcome", outcome is EnvelopeReadOutcome.Ok)
        return (outcome as EnvelopeReadOutcome.Ok).envelope
    }

    private fun readRejected(text: String, schema: Int = APP_SCHEMA_VERSION): BackupError {
        val outcome = BackupSerializer.read(text, schema)
        assertTrue("expected Rejected but was $outcome", outcome is EnvelopeReadOutcome.Rejected)
        return (outcome as EnvelopeReadOutcome.Rejected).error
    }

    @Test
    fun roundtrip_preservesEveryFieldOfEveryEntityGroup() {
        val payload = BackupTestData.richPayload()
        val envelope = readOk(BackupTestData.write(payload))
        assertEquals(payload, envelope.payload)
    }

    @Test
    fun roundtrip_preservesExactLargeLongAmounts() {
        val envelope = readOk(BackupTestData.write(BackupTestData.richPayload()))
        assertEquals(9_007_199_254_740_993L, envelope.payload.transactions[1].amountMinor)
        assertEquals(999_000L, envelope.payload.telecomPackages[0].priceMinor)
        assertEquals(1_500_000L, envelope.payload.opportunities[0].expectedAmountMinor)
    }

    @Test
    fun roundtrip_preservesNullsAndExternalReferences() {
        val envelope = readOk(BackupTestData.write(BackupTestData.richPayload()))
        val linked = envelope.payload.transactions[0]
        assertEquals("TX-42", linked.externalRef)
        assertEquals("IMPORT_FILE", linked.source)
        val unlinked = envelope.payload.transactions[1]
        assertNull(unlinked.categoryId)
        assertNull(unlinked.workItemId)
        assertNull(unlinked.externalRef)
        assertNull(envelope.payload.goals[1].accountId)
        assertNull(envelope.payload.budgets[1].categoryId)
        assertNull(envelope.payload.opportunities[1].expectedAmountMinor)
    }

    @Test
    fun roundtrip_preservesArchivedAndInactiveStates() {
        val envelope = readOk(BackupTestData.write(BackupTestData.richPayload()))
        assertEquals(3, envelope.payload.workItems[0].status)
        assertEquals(1, envelope.payload.telecomSims[0].status)
        assertEquals(0, envelope.payload.telecomSims[1].status)
        assertTrue(!envelope.payload.telecomPackages[0].isActive)
        assertTrue(!envelope.payload.telecomSubscriptions[0].isActive)
        assertEquals(5, envelope.payload.opportunities[0].status)
        assertTrue(envelope.payload.goals[0].isCompleted)
        assertTrue(!envelope.payload.goals[0].isActive)
        assertTrue(!envelope.payload.accounts[1].isActive)
        assertTrue(envelope.payload.categories[0].isSystem)
    }

    @Test
    fun roundtrip_preservesUnicodeAndControlCharacterStrings() {
        val envelope = readOk(BackupTestData.write(BackupTestData.richPayload()))
        assertEquals("Café ☕ line\nbreak\ttab", envelope.payload.transactions[0].note)
        assertEquals("Bank \"Primary\" €", envelope.payload.accounts[1].name)
        assertEquals("Salary काठमाडौं", envelope.payload.categories[1].name)
        assertEquals(
            "Client work with \"quotes\" and \\slashes\\",
            envelope.payload.workItems[0].description
        )
    }

    @Test
    fun envelope_containsRequiredVersionedStructure() {
        val json = BackupTestData.write(BackupTestData.richPayload(), createdAt = 1_700_000_000_000L)
        val root = BackupJson.parse(json) as BackupJson.ParseResult.Ok
        val obj = root.value as JsonValue.JsonObject
        assertEquals(BACKUP_FORMAT_NAME, (obj.fields["format"] as JsonValue.JsonString).value)
        assertEquals(
            BACKUP_FORMAT_VERSION.toLong(),
            (obj.fields["formatVersion"] as JsonValue.JsonLong).value
        )
        assertEquals(
            APP_SCHEMA_VERSION.toLong(),
            (obj.fields["appSchemaVersion"] as JsonValue.JsonLong).value
        )
        assertEquals(
            1_700_000_000_000L,
            (obj.fields["createdAtEpochMillis"] as JsonValue.JsonLong).value
        )
        val checksum = obj.fields["checksum"] as JsonValue.JsonObject
        assertEquals(
            BACKUP_CHECKSUM_ALGORITHM,
            (checksum.fields["algorithm"] as JsonValue.JsonString).value
        )
        val value = (checksum.fields["value"] as JsonValue.JsonString).value
        assertEquals(64, value.length)
        assertTrue(value.all { it in "0123456789abcdef" })
        val payload = obj.fields["payload"] as JsonValue.JsonObject
        for (group in listOf(
            "accounts", "categories", "transactions", "goals", "budgets", "workItems",
            "telecomSims", "telecomPackages", "telecomSubscriptions", "opportunities"
        )) {
            assertNotNull("missing group $group", payload.fields[group])
            assertTrue(payload.fields[group] is JsonValue.JsonArray)
        }
    }

    @Test
    fun serialization_isDeterministic_byteIdenticalAcrossRuns() {
        val payload = BackupTestData.richPayload()
        assertEquals(BackupTestData.write(payload), BackupTestData.write(payload))
        assertEquals(
            BackupTestData.write(payload, createdAt = 5L),
            BackupTestData.write(payload, createdAt = 5L)
        )
    }

    @Test
    fun serialization_omitsStructuralWhitespaceEntirely() {
        val json = BackupTestData.write(BackupTestData.richPayload())
        val withoutStrings = json.replace(Regex("\"(?:[^\"\\\\]|\\\\.)*\""), "")
        assertTrue(
            "structural part must be whitespace-free: $withoutStrings",
            withoutStrings.none { it.isWhitespace() }
        )
        assertEquals(json, json.trim())
    }

    @Test
    fun emptyPayload_serializesAsExplicitEmptyGroups() {
        val envelope = readOk(BackupTestData.write(BackupTestData.emptyPayload()))
        assertEquals(BackupTestData.emptyPayload(), envelope.payload)
        assertTrue(BackupValidator.countsOf(envelope.payload).isEmpty)
    }

    @Test
    fun schemaVersionMismatch_isRejectedAsIncompatible() {
        val json = BackupTestData.write(BackupTestData.richPayload(), schemaVersion = 6)
        val error = readRejected(json)
        assertEquals(BackupErrorCode.INCOMPATIBLE_SCHEMA, error.code)
    }

    @Test
    fun unknownFormatName_isRejectedAsUnsupported() {
        val json = BackupTestData.write(BackupTestData.richPayload())
            .replace("\"$BACKUP_FORMAT_NAME\"", "\"some-other-format\"")
        assertEquals(BackupErrorCode.UNSUPPORTED_FORMAT, readRejected(json).code)
    }

    @Test
    fun futureFormatVersion_isRejectedAsUnsupported() {
        val json = BackupTestData.write(BackupTestData.richPayload())
            .replace("\"formatVersion\":$BACKUP_FORMAT_VERSION", "\"formatVersion\":99")
        assertEquals(BackupErrorCode.UNSUPPORTED_FORMAT, readRejected(json).code)
    }

    @Test
    fun blankDocument_isRejectedAsNoData() {
        assertEquals(BackupErrorCode.NO_DATA, readRejected("").code)
        assertEquals(BackupErrorCode.NO_DATA, readRejected("   \n").code)
    }

    @Test
    fun syntacticallyBrokenDocument_isRejectedAsInvalidJson() {
        assertEquals(BackupErrorCode.INVALID_JSON, readRejected("{not json").code)
        assertEquals(
            BackupErrorCode.INVALID_JSON,
            readRejected("""{"format":"$BACKUP_FORMAT_NAME"}""").code
        )
    }

    @Test
    fun fractionalAmount_isRejectedAsInvalidJson() {
        val json = BackupTestData.write(BackupTestData.richPayload())
            .replace("\"amountMinor\":12345", "\"amountMinor\":123.45")
        assertEquals(BackupErrorCode.INVALID_JSON, readRejected(json).code)
    }

    @Test
    fun missingChecksum_isRejected() {
        val json = BackupTestData.write(BackupTestData.richPayload())
            .replace(Regex(""""checksum":\{[^}]*\},"""), "")
        assertEquals(BackupErrorCode.INVALID_JSON, readRejected(json).code)
    }
}
