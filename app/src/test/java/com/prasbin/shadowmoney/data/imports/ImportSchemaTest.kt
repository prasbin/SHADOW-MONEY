package com.prasbin.shadowmoney.data.imports

import org.junit.Assert.*
import org.junit.Test

class ImportSchemaTest {

    private val fullHeader = listOf(
        "date", "description", "amount", "direction", "account", "category", "external_ref"
    )

    @Test
    fun resolve_allRequiredColumnsPresent_ok() {
        val result = ImportSchema.resolve(fullHeader)
        assertTrue(result is MappingResult.Ok)
        val columns = (result as MappingResult.Ok).columns
        assertEquals(0, columns[ImportColumn.DATE])
        assertEquals(1, columns[ImportColumn.DESCRIPTION])
        assertEquals(2, columns[ImportColumn.AMOUNT])
        assertEquals(3, columns[ImportColumn.DIRECTION])
        assertEquals(4, columns[ImportColumn.ACCOUNT])
        assertEquals(5, columns[ImportColumn.CATEGORY])
        assertEquals(6, columns[ImportColumn.EXTERNAL_REF])
    }

    @Test
    fun resolve_headerAliases_match() {
        val result = ImportSchema.resolve(
            listOf("transaction_date", "note", "amount", "type", "account", "reference")
        )
        assertTrue(result is MappingResult.Ok)
        val columns = (result as MappingResult.Ok).columns
        assertEquals(0, columns[ImportColumn.DATE])
        assertEquals(1, columns[ImportColumn.DESCRIPTION])
        assertEquals(3, columns[ImportColumn.DIRECTION])
        assertEquals(5, columns[ImportColumn.EXTERNAL_REF])
        assertFalse(columns.containsKey(ImportColumn.CATEGORY))
    }

    @Test
    fun resolve_normalizesCaseAndSeparators() {
        val result = ImportSchema.resolve(
            listOf(" Transaction Date ", "Description", "AMOUNT", "Type", " ACCOUNT ")
        )
        assertTrue(result is MappingResult.Ok)
        val columns = (result as MappingResult.Ok).columns
        assertEquals(0, columns[ImportColumn.DATE])
        assertEquals(1, columns[ImportColumn.DESCRIPTION])
        assertEquals(2, columns[ImportColumn.AMOUNT])
        assertEquals(3, columns[ImportColumn.DIRECTION])
        assertEquals(4, columns[ImportColumn.ACCOUNT])
    }

    @Test
    fun resolve_unrecognizedNormalizedHeader_notMappedAsAccount() {
        val result = ImportSchema.resolve(
            listOf("date", "description", "amount", "direction", "Account Name")
        )
        assertTrue(result is MappingResult.Rejected)
        assertTrue((result as MappingResult.Rejected).reason.contains("account"))
    }

    @Test
    fun resolve_missingRequiredColumns_rejectedWithNames() {
        val result = ImportSchema.resolve(listOf("date", "description", "amount", "category"))
        assertTrue(result is MappingResult.Rejected)
        val reason = (result as MappingResult.Rejected).reason
        assertTrue(reason.contains("Missing required columns"))
        assertTrue(reason.contains("direction"))
        assertTrue(reason.contains("account"))
    }

    @Test
    fun resolve_ambiguousColumns_rejected() {
        val result = ImportSchema.resolve(
            listOf("date", "transaction_date", "description", "amount", "direction", "account")
        )
        assertTrue(result is MappingResult.Rejected)
        assertTrue((result as MappingResult.Rejected).reason.contains("Ambiguous"))
        assertTrue(result.reason.contains("date"))
    }

    @Test
    fun resolve_unrecognizedHeader_rejectedAsMissingHeader() {
        val result = ImportSchema.resolve(listOf("foo", "bar", "baz"))
        assertTrue(result is MappingResult.Rejected)
        assertTrue((result as MappingResult.Rejected).reason.contains("Missing header row"))
    }

    @Test
    fun resolve_nullHeader_rejected() {
        val result = ImportSchema.resolve(null)
        assertTrue(result is MappingResult.Rejected)
        assertTrue((result as MappingResult.Rejected).reason.contains("Missing header"))
    }

    @Test
    fun resolve_optionalColumnsAbsent_okWithoutKeys() {
        val result = ImportSchema.resolve(
            listOf("date", "description", "amount", "direction", "account")
        )
        assertTrue(result is MappingResult.Ok)
        val columns = (result as MappingResult.Ok).columns
        assertFalse(columns.containsKey(ImportColumn.CATEGORY))
        assertFalse(columns.containsKey(ImportColumn.EXTERNAL_REF))
    }

    @Test
    fun resolve_unknownExtraColumns_ignored() {
        val result = ImportSchema.resolve(
            listOf("date", "description", "amount", "direction", "account", "bank_branch", "memo9")
        )
        assertTrue(result is MappingResult.Ok)
        assertEquals(5, (result as MappingResult.Ok).columns.size)
    }

    @Test
    fun normalizeHeader_examples() {
        assertEquals("transaction_date", ImportSchema.normalizeHeader("Transaction Date"))
        assertEquals("external_ref", ImportSchema.normalizeHeader("external-ref"))
        assertEquals("amount", ImportSchema.normalizeHeader("  Amount  "))
        assertEquals("", ImportSchema.normalizeHeader("!!!"))
    }
}
