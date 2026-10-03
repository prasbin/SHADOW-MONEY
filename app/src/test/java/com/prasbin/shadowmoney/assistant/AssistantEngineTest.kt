package com.prasbin.shadowmoney.assistant

import com.prasbin.shadowmoney.data.BudgetStatus
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.intelligence.Insight
import com.prasbin.shadowmoney.intelligence.InsightKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {

    private val engine = AssistantEngine()

    private fun balanceQuestion(includeArchived: Boolean = false) = ClassifiedQuestion(
        intent = AssistantIntent.BALANCE,
        includeArchived = includeArchived,
        normalizedInput = "balance"
    )

    private fun budgetQuestion(period: AssistantPeriod?) =
        ClassifiedQuestion(intent = AssistantIntent.BUDGET, period = period)

    private fun emptyBudget(label: String) =
        AssistantBudgetInfo(
            monthKey = "2026-09",
            monthLabel = label,
            hasAny = false,
            overall = null,
            categories = emptyList()
        )

    private fun data(
        accounts: List<AssistantAccountInfo> = emptyList(),
        periodLabel: String? = null,
        periodRange: AssistantPeriodRange? = null,
        periodTotals: AssistantPeriodTotals? = null,
        recentTransactions: List<AssistantTransactionInfo> = emptyList(),
        windowTransactions: List<AssistantTransactionInfo> = emptyList(),
        budgetCurrent: AssistantBudgetInfo = emptyBudget("CurrentMonth"),
        budgetLast: AssistantBudgetInfo = emptyBudget("PreviousMonth"),
        goals: List<AssistantGoalInfo> = emptyList(),
        work: List<AssistantWorkInfo> = emptyList(),
        telecom: AssistantTelecomInfo? = null,
        opportunities: AssistantOpportunityInfo? = null,
        projectionInsight: Insight? = null
    ) = AssistantData(
        accounts = accounts,
        totalBalanceActiveMinor = accounts.filter { it.isActive }.sumOf { it.balanceMinor },
        totalIncomeActiveMinor = 0L,
        totalOutflowActiveMinor = 0L,
        periodLabel = periodLabel,
        periodRange = periodRange,
        periodTotals = periodTotals,
        recentTransactions = recentTransactions,
        windowTransactions = windowTransactions,
        budgetCurrent = budgetCurrent,
        budgetLast = budgetLast,
        goals = goals,
        work = work,
        telecom = telecom,
        opportunities = opportunities,
        projectionInsight = projectionInsight
    )

    private fun texts(response: AssistantResponse): String =
        response.sections.joinToString("\n") { it.text }

    // ---- BALANCE -----------------------------------------------------------

    @Test
    fun balance_sumsOnlyActiveAccounts() {
        val fixture = data(
            accounts = listOf(
                AssistantAccountInfo("Bank", true, 70_000L),
                AssistantAccountInfo("Wallet", true, 30_000L),
                AssistantAccountInfo("Old", false, 999_999L)
            )
        )
        val response = engine.answer(balanceQuestion(), fixture)

        val all = texts(response)
        assertTrue(all.contains(ASSISTANT_TRUST_LABEL))
        assertTrue(all.contains(Money.formatNpr(100_000L)))
        assertFalse(all.contains(Money.formatNpr(999_999L)))
        assertFalse(all.contains("Old"))
        assertEquals(InsightKind.FACT, response.sections[0].kind)
        assertEquals(ASSISTANT_SOURCE_TEXT, response.source)
    }

    @Test
    fun balance_neverClaimsABankCurrentlyHoldsMoney() {
        val fixture = data(accounts = listOf(AssistantAccountInfo("Bank", true, 5_000L)))
        val all = texts(engine.answer(balanceQuestion(), fixture))
        assertTrue(all.contains(ASSISTANT_TRUST_LABEL))
        assertFalse(all.contains("bank currently", ignoreCase = true))
        assertFalse(all.contains("currently has", ignoreCase = true))
        assertFalse(all.contains("your bank", ignoreCase = true))
    }

    @Test
    fun importText_describesStatementFileIngestionHonestly() {
        assertTrue(ASSISTANT_IMPORT_TEXT.contains("CSV or PDF"))
        assertTrue(ASSISTANT_IMPORT_TEXT.contains("IMPORTED / USER-PROVIDED"))
        assertTrue(ASSISTANT_IMPORT_TEXT.contains("possible-duplicate"))
        assertTrue(ASSISTANT_IMPORT_TEXT.contains("never starts an import"))
        assertTrue(ASSISTANT_IMPORT_TEXT.contains("never labeled connected or verified"))
    }

    @Test
    fun balance_withoutArchivedFlag_neverMentionsArchivedAccounts() {
        val fixture = data(accounts = listOf(AssistantAccountInfo("Bank", true, 5_000L)))
        val response = engine.answer(balanceQuestion(includeArchived = false), fixture)
        assertFalse(texts(response).contains("Archived accounts"))
    }

    @Test
    fun balance_withArchivedFlag_listsArchivedSeparately() {
        val fixture = data(
            accounts = listOf(
                AssistantAccountInfo("Bank", true, 5_000L),
                AssistantAccountInfo("Old", false, 42_000L)
            )
        )
        val response = engine.answer(balanceQuestion(includeArchived = true), fixture)
        val all = texts(response)
        assertTrue(all.contains("Archived accounts (reference only"))
        assertTrue(all.contains(Money.formatNpr(42_000L)))
        assertFalse(all.contains(Money.formatNpr(47_000L)))
    }

    @Test
    fun balance_noActiveAccounts_reportsHonestly() {
        val response = engine.answer(balanceQuestion(), data())
        assertTrue(texts(response).contains("No active accounts recorded yet."))
    }

    @Test
    fun balance_moreThanEightAccounts_truncatesWithMoreLine() {
        val accounts = (1..9).map { AssistantAccountInfo("A$it", true, it.toLong()) }
        val response = engine.answer(balanceQuestion(), data(accounts = accounts))
        val all = texts(response)
        assertTrue(all.contains("A8"))
        assertFalse(all.contains("A9"))
        assertTrue(all.contains("…and 1 more"))
    }

    // ---- INCOME / OUTFLOW --------------------------------------------------

    @Test
    fun income_reportsTotalsAndNetCalculation() {
        val fixture = data(
            periodLabel = "this month",
            periodTotals = AssistantPeriodTotals(
                incomeMinor = 50_000L,
                outflowMinor = 20_000L,
                transactionCount = 4,
                incomeCount = 3,
                outflowCount = 1,
                categoryBreakdown = emptyList()
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.INCOME), fixture
        )
        assertEquals(2, response.sections.size)
        assertEquals(InsightKind.FACT, response.sections[0].kind)
        assertTrue(response.sections[0].text.contains(Money.formatNpr(50_000L)))
        assertTrue(response.sections[0].text.contains("3 income transactions"))
        assertEquals(InsightKind.CALCULATION, response.sections[1].kind)
        assertTrue(response.sections[1].text.contains(Money.formatNpr(30_000L)))
    }

    @Test
    fun income_withNoTransactions_saysSo() {
        val fixture = data(
            periodLabel = "this month",
            periodTotals = AssistantPeriodTotals(0L, 0L, 0, 0, 0, emptyList())
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.INCOME), fixture
        )
        assertEquals("No transactions recorded this month.", response.sections[0].text)
        assertEquals(InsightKind.FACT, response.sections[0].kind)
    }

    @Test
    fun income_withoutTotals_isInsufficientNotFabricated() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.INCOME), data()
        )
        assertEquals(ASSISTANT_INSUFFICIENT_TEXT, response.sections[0].text)
        assertNull(response.sections[0].kind)
    }

    @Test
    fun outflow_reportsLargestCategoryAndProjection() {
        val fixture = data(
            periodLabel = "this month",
            periodTotals = AssistantPeriodTotals(
                incomeMinor = 0L,
                outflowMinor = 9_000L,
                transactionCount = 3,
                incomeCount = 0,
                outflowCount = 3,
                categoryBreakdown = listOf(
                    AssistantCategorySpend("Food", 6_000L),
                    AssistantCategorySpend("Transport", 3_000L)
                )
            ),
            projectionInsight = Insight(
                kind = InsightKind.PROJECTION,
                title = "Projected outflow",
                summary = "Historical average",
                amountMinor = 88_888L
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.OUTFLOW), fixture
        )
        val kinds = response.sections.map { it.kind }
        assertEquals(InsightKind.FACT, kinds[0])
        assertEquals(InsightKind.ANALYSIS, kinds[1])
        assertEquals(InsightKind.FACT, kinds[2])
        assertEquals(InsightKind.PROJECTION, kinds[3])

        val all = texts(response)
        assertTrue(all.contains(Money.formatNpr(9_000L)))
        assertTrue(all.contains("Food"))
        assertTrue(all.contains(Money.formatNpr(6_000L)))
        assertTrue(response.sections[3].text.startsWith("PROJECTION —"))
        assertTrue(all.contains(Money.formatNpr(88_888L)))
        assertTrue(all.contains("not a guarantee"))
    }

    @Test
    fun outflow_withoutProjection_hasNoProjectionSection() {
        val fixture = data(
            periodLabel = "this month",
            periodTotals = AssistantPeriodTotals(0L, 1_000L, 1, 0, 1, emptyList())
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.OUTFLOW), fixture
        )
        assertTrue(response.sections.none { it.kind == InsightKind.PROJECTION })
    }

    // ---- BUDGET ------------------------------------------------------------

    @Test
    fun budget_noBudgetRecorded_saysWhichMonth() {
        val response = engine.answer(budgetQuestion(null), data())
        assertEquals(
            AssistantSection(InsightKind.FACT, "No budget recorded for CurrentMonth."),
            response.sections[0]
        )
    }

    @Test
    fun budget_lastMonthQuestion_readsLastMonthBudget() {
        val fixture = data(
            budgetCurrent = emptyBudget("CurrentMonth"),
            budgetLast = AssistantBudgetInfo(
                monthKey = "2026-08",
                monthLabel = "PreviousMonth",
                hasAny = true,
                overall = AssistantBudgetViewInfo(
                    label = "Overall",
                    amountMinor = 50_000L,
                    spentMinor = 60_000L,
                    remainingMinor = -10_000L,
                    percentUsed = 120,
                    status = BudgetStatus.OVER_BUDGET
                ),
                categories = emptyList()
            )
        )
        val response = engine.answer(budgetQuestion(AssistantPeriod.LAST_MONTH), fixture)
        val all = texts(response)
        assertTrue(all.contains("PreviousMonth"))
        assertFalse(all.contains("CurrentMonth"))
        assertEquals(InsightKind.CALCULATION, response.sections[0].kind)
        assertTrue(all.contains(Money.formatNpr(50_000L)))
        assertTrue(all.contains(Money.formatNpr(-10_000L)))
        assertTrue(all.contains("over budget"))
        assertTrue(all.contains("120% used"))
    }

    @Test
    fun budget_categoryLines_truncatedAtFive() {
        val categories = (1..7).map { index ->
            AssistantBudgetViewInfo(
                label = "Cat$index",
                amountMinor = index * 1_000L,
                spentMinor = 0L,
                remainingMinor = index * 1_000L,
                percentUsed = 0,
                status = BudgetStatus.NORMAL
            )
        }
        val fixture = data(
            budgetCurrent = AssistantBudgetInfo(
                monthKey = "2026-09",
                monthLabel = "CurrentMonth",
                hasAny = true,
                overall = null,
                categories = categories
            )
        )
        val all = texts(engine.answer(budgetQuestion(null), fixture))
        assertTrue(all.contains("Cat5"))
        assertFalse(all.contains("Cat6"))
        assertTrue(all.contains("…and 2 more"))
    }

    // ---- GOALS / WORK ------------------------------------------------------

    @Test
    fun goals_empty_saysSo() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.GOALS), data()
        )
        assertEquals(ASSISTANT_GOALS_EMPTY_TEXT, response.sections[0].text)
    }

    @Test
    fun goals_reportsProgressPercentages() {
        val fixture = data(
            goals = listOf(
                AssistantGoalInfo("Laptop", 100_000L, 25_000L, 25, "Bank"),
                AssistantGoalInfo("Trip", 50_000L, 0L, 0, null)
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.GOALS), fixture
        )
        val all = texts(response)
        assertTrue(all.contains("2 goals recorded."))
        assertTrue(all.contains("Laptop"))
        assertTrue(all.contains("25%"))
        assertTrue(all.contains(Money.formatNpr(25_000L)))
        assertTrue(all.contains("no account linked"))
    }

    @Test
    fun goals_overTargetPercent_clampedForDisplay() {
        val fixture = data(
            goals = listOf(AssistantGoalInfo("Laptop", 10_000L, 106_500L, 1065, "Bank"))
        )
        val all = texts(engine.answer(ClassifiedQuestion(intent = AssistantIntent.GOALS), fixture))
        assertTrue(all.contains("(100%)"))
        assertFalse(all.contains("1065%"))
    }

    @Test
    fun work_empty_saysSo() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.WORK), data()
        )
        assertEquals(ASSISTANT_WORK_EMPTY_TEXT, response.sections[0].text)
    }

    @Test
    fun work_reportsExpectedVsReceivedAsEstimates() {
        val fixture = data(
            work = listOf(
                AssistantWorkInfo("Site", "Active", true, 100_000L, 40_000L, 60_000L),
                AssistantWorkInfo("Logo", "Completed", false, 20_000L, 20_000L, 0L)
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.WORK), fixture
        )
        val all = texts(response)
        assertTrue(all.contains("2 work items tracked (1 active)"))
        assertTrue(all.contains(Money.formatNpr(120_000L)))
        assertTrue(all.contains(Money.formatNpr(60_000L)))
        assertTrue(all.contains("not income"))
    }

    // ---- TELECOM / OPPORTUNITIES ------------------------------------------

    @Test
    fun telecom_noRecords_saysSoWithPlainSection() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TELECOM), data()
        )
        assertEquals(ASSISTANT_TELECOM_EMPTY_TEXT, response.sections[0].text)
        assertNull(response.sections[0].kind)
    }

    @Test
    fun telecom_reportsCountsCostAndRenewals() {
        val fixture = data(
            telecom = AssistantTelecomInfo(
                activeSimCount = 2,
                activeSubscriptionCount = 1,
                expectedMonthlyCostMinor = 999L,
                upcomingRenewals = listOf(
                    AssistantRenewalInfo("Personal", "Data 4G", 1_800_000_000_000L)
                )
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TELECOM), fixture
        )
        val all = texts(response)
        assertTrue(all.contains("2 active SIMs, 1 active subscriptions."))
        assertTrue(all.contains(Money.formatNpr(999L)))
        assertTrue(all.contains("expected cost, not recorded spending"))
        assertTrue(all.contains("Data 4G"))
    }

    @Test
    fun telecom_withoutUpcomingRenewals_saysNoneDue() {
        val fixture = data(
            telecom = AssistantTelecomInfo(1, 0, 0L, emptyList())
        )
        val all = texts(engine.answer(ClassifiedQuestion(intent = AssistantIntent.TELECOM), fixture))
        assertTrue(all.contains("No renewals due within the next 60 days."))
    }

    @Test
    fun opportunities_noRecords_saysSoWithPlainSection() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.OPPORTUNITIES), data()
        )
        assertEquals(ASSISTANT_OPPORTUNITIES_EMPTY_TEXT, response.sections[0].text)
        assertNull(response.sections[0].kind)
    }

    @Test
    fun opportunities_reportsCountsAndTrackedTitles() {
        val fixture = data(
            opportunities = AssistantOpportunityInfo(
                activeCount = 2,
                needsReviewCount = 1,
                totalExpectedAmountMinor = 75_000L,
                tracked = listOf("Website" to "New", "App" to "Needs review")
            )
        )
        val all = texts(
            engine.answer(ClassifiedQuestion(intent = AssistantIntent.OPPORTUNITIES), fixture)
        )
        assertTrue(all.contains("2 active opportunities. 1 need review."))
        assertTrue(all.contains(Money.formatNpr(75_000L)))
        assertTrue(all.contains("expected amounts, not income"))
        assertTrue(all.contains("Website"))
    }

    // ---- TRANSACTIONS ------------------------------------------------------

    private fun txInfo(
        timestamp: Long,
        amount: Long,
        direction: Int,
        note: String,
        account: String = "Bank",
        category: String? = null
    ) = AssistantTransactionInfo(timestamp, amount, direction, note, account, category)

    @Test
    fun transactions_recentList_showsFiveAndTruncates() {
        val fixture = data(
            recentTransactions = (1..7).map { index ->
                txInfo(
                    timestamp = 1_700_000_000_000L + index,
                    amount = index * 100L,
                    direction = TRANSACTION_DIRECTION_OUTFLOW,
                    note = "Note$index"
                )
            }
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TRANSACTIONS, period = AssistantPeriod.RECENT),
            fixture
        )
        val all = texts(response)
        assertTrue(all.contains("Your 5 most recent recorded transactions:"))
        assertTrue(all.contains("Note1"))
        assertTrue(all.contains("Note5"))
        assertFalse(all.contains("Note6"))
        assertTrue(all.contains("…and 2 more"))
        assertTrue(all.contains("out"))
    }

    @Test
    fun transactions_withPeriod_usesWindowAndCount() {
        val fixture = data(
            periodLabel = "today",
            periodRange = AssistantPeriodRange(0L, 100L, "today"),
            periodTotals = AssistantPeriodTotals(0L, 3_000L, 3, 0, 3, emptyList()),
            windowTransactions = listOf(
                txInfo(50L, 3_000L, TRANSACTION_DIRECTION_INCOME, "Salary")
            )
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TRANSACTIONS, period = AssistantPeriod.TODAY),
            fixture
        )
        val all = texts(response)
        assertTrue(all.contains("3 transactions recorded today, newest first:"))
        assertTrue(all.contains("Salary"))
        assertTrue(all.contains("in"))
    }

    @Test
    fun transactions_withPeriodButNoWindow_saysNoTransactions() {
        val fixture = data(
            periodLabel = "today",
            periodRange = AssistantPeriodRange(0L, 100L, "today"),
            periodTotals = AssistantPeriodTotals(0L, 0L, 0, 0, 0, emptyList())
        )
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TRANSACTIONS, period = AssistantPeriod.TODAY),
            fixture
        )
        assertEquals("No transactions recorded today.", response.sections[0].text)
    }

    @Test
    fun transactions_emptyRecent_saysSo() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.TRANSACTIONS, period = AssistantPeriod.RECENT),
            data()
        )
        assertEquals(ASSISTANT_RECENT_EMPTY_TEXT, response.sections[0].text)
    }

    // ---- BOUNDED / NON-DATA RESPONSES -------------------------------------

    @Test
    fun help_secretAmbiguousUnsupportedImport_arePlainGuidance() {
        val cases = mapOf(
            AssistantIntent.HELP to ASSISTANT_HELP_TEXT,
            AssistantIntent.SECRET_TARGET_REFUSAL to IntentClassifier.SECRET_TARGET_REFUSAL_TEXT,
            AssistantIntent.AMBIGUOUS to ASSISTANT_AMBIGUOUS_TEXT,
            AssistantIntent.UNSUPPORTED to ASSISTANT_UNSUPPORTED_TEXT,
            AssistantIntent.IMPORT to ASSISTANT_IMPORT_TEXT
        )
        for ((intent, expected) in cases) {
            val response = engine.answer(ClassifiedQuestion(intent = intent), data())
            assertEquals(1, response.sections.size)
            assertNull(response.sections[0].kind)
            assertEquals(expected, response.sections[0].text)
        }
    }

    @Test
    fun secretTargetRefusal_neverContainsFinancialData() {
        val secret = 77_777_777L
        val fixture = data(accounts = listOf(AssistantAccountInfo("Bank", true, secret)))
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.SECRET_TARGET_REFUSAL), fixture
        )
        assertEquals(IntentClassifier.SECRET_TARGET_REFUSAL_TEXT, response.sections[0].text)
        assertFalse(response.sections[0].text.contains(Money.formatNpr(secret)))
        assertFalse(response.sections[0].text.contains(secret.toString()))
    }

    @Test
    fun clarification_alwaysWinsOverDataAnswer() {
        val fixture = data(accounts = listOf(AssistantAccountInfo("Bank", true, 1_000L)))
        val question = IntentClassifier.classify("how much did i spend next month")
        val response = engine.answer(question, fixture)
        assertEquals(1, response.sections.size)
        assertEquals(IntentClassifier.FUTURE_PERIOD_NOTE, response.sections[0].text)
        assertNull(response.sections[0].kind)
    }

    @Test
    fun clarifyPeriod_withoutNote_fallsBackToRecordedPeriodsNote() {
        val response = engine.answer(
            ClassifiedQuestion(intent = AssistantIntent.CLARIFY_PERIOD), data()
        )
        assertEquals(IntentClassifier.UNSUPPORTED_PERIOD_NOTE, response.sections[0].text)
    }

    @Test
    fun dataIntentWithoutData_isInsufficientNotFabricated() {
        for (intent in listOf(
            AssistantIntent.BALANCE,
            AssistantIntent.INCOME,
            AssistantIntent.OUTFLOW,
            AssistantIntent.BUDGET,
            AssistantIntent.GOALS,
            AssistantIntent.WORK,
            AssistantIntent.TELECOM,
            AssistantIntent.OPPORTUNITIES,
            AssistantIntent.TRANSACTIONS
        )) {
            val response = engine.answer(ClassifiedQuestion(intent = intent), null)
            assertEquals(intent.name, ASSISTANT_INSUFFICIENT_TEXT, response.sections[0].text)
            assertNull(response.sections[0].kind)
        }
    }

    @Test
    fun everyResponseCarriesSourceLabel() {
        val responses = listOf(
            engine.answer(ClassifiedQuestion(intent = AssistantIntent.HELP), null),
            engine.answer(balanceQuestion(), data(accounts = listOf(
                AssistantAccountInfo("Bank", true, 1_000L)
            ))),
            engine.answer(ClassifiedQuestion(intent = AssistantIntent.GOALS), data())
        )
        for (response in responses) {
            assertEquals(ASSISTANT_SOURCE_TEXT, response.source)
        }
    }

    @Test
    fun engine_isPure_sameInputSameOutput() {
        val fixture = data(
            accounts = listOf(AssistantAccountInfo("Bank", true, 12_345L)),
            periodLabel = "this month",
            periodTotals = AssistantPeriodTotals(500L, 100L, 2, 1, 1, emptyList())
        )
        val question = balanceQuestion()
        val first = engine.answer(question, fixture)
        repeat(5) { assertEquals(first, engine.answer(question, fixture)) }
    }
}
