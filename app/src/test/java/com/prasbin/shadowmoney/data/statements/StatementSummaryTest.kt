package com.prasbin.shadowmoney.data.statements

import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.imports.CsvParser
import com.prasbin.shadowmoney.data.imports.ImportEngine
import com.prasbin.shadowmoney.data.imports.ImportPreview
import com.prasbin.shadowmoney.data.imports.ImportReference
import com.prasbin.shadowmoney.data.imports.PreviewOutcome
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class StatementSummaryTest {

    private val reference = ImportReference(
        accounts = listOf(Account(id = 1L, name = "Wallet")),
        categories = listOf(Category(id = 1L, name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)),
        existingFingerprints = emptyMap()
    )

    private val header = "date,description,amount,direction,account,balance"

    private fun preview(csv: String): ImportPreview {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse(csv),
            reference,
            defaultAccountId = 1L
        )
        assertTrue(outcome is PreviewOutcome.Ready)
        return (outcome as PreviewOutcome.Ready).preview
    }

    private fun dayStart(vararg days: Int): Long = days.map {
        LocalDate.of(2026, 1, it)
            .atStartOfDay(BudgetCalendar.KATHMANDU_ZONE).toInstant().toEpochMilli()
    }.first()

    @Test
    fun summaryDerivesPeriodFlowAndEndingBalanceFromParsedRows() {
        val rows = listOf(
            header,
            "2026-01-05,Coffee,150.00,outflow,,10850.00",
            "2026-01-06,Salary,50000.00,income,,60850.00",
            "2026-13-45,Broken date,10.00,outflow,,"
        ).joinToString("\n")
        val summary = StatementSummary.from(preview(rows), unparsedLines = 3)

        assertEquals(dayStart(5), summary.periodStartMs)
        assertEquals(dayStart(6), summary.periodEndMs)
        assertEquals(6_085_000L, summary.endBalanceMinor)
        assertEquals(5_000_000L, summary.moneyInMinor)
        assertEquals(15_000L, summary.moneyOutMinor)
        assertEquals(3, summary.totalRows)
        assertEquals(1, summary.invalidRows)
        assertEquals(0, summary.duplicateRows)
        assertEquals(2, summary.importableRows)
        assertEquals(3, summary.unparsedLines)
    }

    @Test
    fun summaryWithNoBalanceReportsNullBalanceNeverZero() {
        val rows = listOf(
            header,
            "2026-01-05,Coffee,150.00,outflow,,"
        ).joinToString("\n")
        val summary = StatementSummary.from(preview(rows))
        assertEquals(null, summary.endBalanceMinor)
    }

    @Test
    fun emptyStatement_headerOnlyReportsHonestEmptyOutcomeNeverFakeRows() {
        val outcome = ImportEngine.buildPreview(
            CsvParser.parse(header),
            reference,
            defaultAccountId = 1L
        )
        assertTrue(outcome is PreviewOutcome.Empty)
    }

    // ---------------------------------------------------------------- age

    private val now = 1_800_000_000_000L
    private fun daysAgo(days: Long): Long = now - TimeUnit.DAYS.toMillis(days)

    @Test
    fun recentPeriodIsLabelledImportedRecent() {
        val info = StatementAgeClassifier.classify(now, periodEndMs = daysAgo(10), importedAtMs = daysAgo(10))
        assertEquals(StatementAgeClass.RECENT, info.ageClass)
        assertEquals("IMPORTED — RECENT", info.label)
        assertTrue(info.detail.contains("period ended 10 day(s) ago"))
    }

    @Test
    fun oldPeriodIsLabelledImportedOldWithExactAge() {
        val info = StatementAgeClassifier.classify(now, periodEndMs = daysAgo(45), importedAtMs = daysAgo(45))
        assertEquals(StatementAgeClass.OLD, info.ageClass)
        assertEquals("IMPORTED — OLD", info.label)
        assertTrue(info.detail.contains("period ended 45 day(s) ago"))
    }

    @Test
    fun missingPeriodFallsBackToImportTime() {
        val info = StatementAgeClassifier.classify(now, periodEndMs = null, importedAtMs = daysAgo(3))
        assertEquals(StatementAgeClass.RECENT, info.ageClass)
        assertTrue(info.detail.contains("imported 3 day(s) ago"))
    }

    @Test
    fun thresholdIsInclusiveAtThirtyOneDays() {
        assertEquals(
            StatementAgeClass.RECENT,
            StatementAgeClassifier.classify(now, daysAgo(31), daysAgo(31)).ageClass
        )
        assertEquals(
            StatementAgeClass.OLD,
            StatementAgeClassifier.classify(now, daysAgo(32), daysAgo(32)).ageClass
        )
    }

    @Test
    fun futureTimestampsNeverGoNegative() {
        val info = StatementAgeClassifier.classify(now, periodEndMs = now + 86_400_000L, importedAtMs = now)
        assertEquals(StatementAgeClass.RECENT, info.ageClass)
        assertTrue(info.detail.contains("0 day(s) ago"))
    }
}
