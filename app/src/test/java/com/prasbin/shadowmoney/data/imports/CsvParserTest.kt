package com.prasbin.shadowmoney.data.imports

import org.junit.Assert.*
import org.junit.Test

class CsvParserTest {

    @Test
    fun normalCsv_parsesHeaderAndRows() {
        val doc = CsvParser.parse("date,amount\n2026-01-01,10.00\n2026-01-02,20.00\n")
        assertEquals(listOf("date", "amount"), doc.header)
        assertNull(doc.headerError)
        assertEquals(2, doc.records.size)
        assertEquals(listOf("2026-01-01", "10.00"), doc.records[0].values)
        assertEquals(listOf("2026-01-02", "20.00"), doc.records[1].values)
        assertNull(doc.records[0].parseError)
    }

    @Test
    fun quotedFields_preserveCommas() {
        val doc = CsvParser.parse("date,description,amount\n2026-01-01,\"Coffee, beans\",10.00\n")
        assertEquals(1, doc.records.size)
        assertEquals("Coffee, beans", doc.records[0].values[1])
    }

    @Test
    fun commasInsideQuotes_areNotFieldSeparators() {
        val doc = CsvParser.parse("a,b\n\"x,y,z\",1\n")
        assertEquals(2, doc.records[0].values.size)
        assertEquals("x,y,z", doc.records[0].values[0])
        assertEquals("1", doc.records[0].values[1])
    }

    @Test
    fun escapedQuotes_unquoteCorrectly() {
        val doc = CsvParser.parse("a\n\"say \"\"hi\"\"\"\n")
        assertEquals("say \"hi\"", doc.records[0].values[0])
    }

    @Test
    fun blankLines_areSkippedAndLineNumbersStayCorrect() {
        val doc = CsvParser.parse("h1,h2\n\n\nv1,v2\n\nv3,v4\n")
        assertEquals(2, doc.records.size)
        assertEquals(4, doc.records[0].lineNumber)
        assertEquals(6, doc.records[1].lineNumber)
    }

    @Test
    fun crlfLineEndings_parse() {
        val doc = CsvParser.parse("h1,h2\r\nv1,v2\r\nv3,v4\r\n")
        assertEquals(listOf("h1", "h2"), doc.header)
        assertEquals(2, doc.records.size)
        assertEquals(listOf("v1", "v2"), doc.records[0].values)
    }

    @Test
    fun loneCrLineEndings_parse() {
        val doc = CsvParser.parse("h1,h2\rv1,v2\rv3,v4")
        assertEquals(listOf("h1", "h2"), doc.header)
        assertEquals(2, doc.records.size)
        assertEquals(listOf("v3", "v4"), doc.records[1].values)
    }

    @Test
    fun utf8Bom_isStripped() {
        val doc = CsvParser.parse("\uFEFFh1,h2\nv1,v2\n")
        assertEquals(listOf("h1", "h2"), doc.header)
    }

    @Test
    fun unterminatedQuote_reportedAsInvalidRecord() {
        val doc = CsvParser.parse("h1,h2\n\"broken,v2\n")
        assertEquals(1, doc.records.size)
        assertNotNull(doc.records[0].parseError)
        assertTrue(doc.records[0].parseError!!.contains("Unterminated"))
    }

    @Test
    fun unexpectedQuoteInUnquotedField_reportedAsInvalidRecord() {
        val doc = CsvParser.parse("h1,h2\nab\"cd,v2\n")
        assertEquals(1, doc.records.size)
        assertNotNull(doc.records[0].parseError)
    }

    @Test
    fun trailingNewline_doesNotCreateExtraRecord() {
        val doc = CsvParser.parse("h\nv\n")
        assertEquals(1, doc.records.size)
        assertEquals(listOf("v"), doc.records[0].values)
    }

    @Test
    fun emptyInput_reportsMissingHeader() {
        val doc = CsvParser.parse("")
        assertNull(doc.header)
        assertNotNull(doc.headerError)
        assertTrue(doc.headerError!!.contains("Missing header"))
    }

    @Test
    fun headerOnly_noDataRecords() {
        val doc = CsvParser.parse("date,amount")
        assertEquals(listOf("date", "amount"), doc.header)
        assertTrue(doc.records.isEmpty())
    }

    @Test
    fun quotedFieldContainingNewline_staysOneRecord() {
        val doc = CsvParser.parse("h1,h2\n\"line1\nline2\",v2\nnext,row\n")
        assertEquals(2, doc.records.size)
        assertEquals("line1\nline2", doc.records[0].values[0])
        assertEquals(listOf("next", "row"), doc.records[1].values)
        assertEquals(4, doc.records[1].lineNumber)
    }

    @Test
    fun whitespaceOnlyLine_isSkipped() {
        val doc = CsvParser.parse("h\n   \nv\n")
        assertEquals(1, doc.records.size)
        assertEquals("v", doc.records[0].values[0])
    }

    @Test
    fun missingHeader_whenOnlyBlankLines() {
        val doc = CsvParser.parse("\n\n\n")
        assertNull(doc.header)
        assertNotNull(doc.headerError)
    }

    @Test
    fun extraFields_recordedWithAllValues() {
        val doc = CsvParser.parse("h1,h2,h3\na,b,c,d\n")
        assertEquals(4, doc.records[0].values.size)
        assertEquals("d", doc.records[0].values[3])
    }

    @Test
    fun characterAfterClosingQuote_reportedInvalid() {
        val doc = CsvParser.parse("h\n\"abc\"def\n")
        assertEquals(1, doc.records.size)
        assertNotNull(doc.records[0].parseError)
    }
}
