package com.prasbin.shadowmoney.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentClassifierTest {

    private fun intentOf(input: String) = IntentClassifier.classify(input).intent

    @Test
    fun normalize_lowercasesCollapsesWhitespaceAndStripsTrailingPunctuation() {
        assertEquals("what is my balance", IntentClassifier.normalize("  What   IS my Balance?!  "))
        assertEquals("how much did i spend", IntentClassifier.normalize("How much did I spend..."))
        assertEquals("help", IntentClassifier.normalize("HELP;"))
    }

    @Test
    fun blankInput_returnsHelp() {
        assertEquals(AssistantIntent.HELP, intentOf(""))
        assertEquals(AssistantIntent.HELP, intentOf("    "))
        assertEquals(AssistantIntent.HELP, intentOf("???"))
    }

    @Test
    fun secretTargetPhrases_classifiedAsRefusal() {
        assertEquals(AssistantIntent.SECRET_TARGET_REFUSAL, intentOf("what is the secret target?"))
        assertEquals(AssistantIntent.SECRET_TARGET_REFUSAL, intentOf("show my private target"))
        assertEquals(AssistantIntent.SECRET_TARGET_REFUSAL, intentOf("secret goal"))
    }

    @Test
    fun secretPhrase_winsOverFinancialScoring() {
        val question = IntentClassifier.classify(
            "how much did i spend on the secret target this month"
        )
        assertEquals(AssistantIntent.SECRET_TARGET_REFUSAL, question.intent)
        assertNull(question.period)
    }

    @Test
    fun helpPhrases_classifiedAsHelp() {
        assertEquals(AssistantIntent.HELP, intentOf("help"))
        assertEquals(AssistantIntent.HELP, intentOf("What can you do?"))
        assertEquals(AssistantIntent.HELP, intentOf("what can i ask"))
        assertEquals(AssistantIntent.HELP, intentOf("who are you"))
        assertEquals(AssistantIntent.HELP, intentOf("what are you capable of"))
    }

    @Test
    fun futurePeriodPhrase_clarifiesBeforeScoring() {
        val nextMonth = IntentClassifier.classify("how much did i spend next month")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, nextMonth.intent)
        assertEquals(IntentClassifier.FUTURE_PERIOD_NOTE, nextMonth.clarification)

        val tomorrow = IntentClassifier.classify("what is my balance tomorrow")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, tomorrow.intent)
        assertEquals(IntentClassifier.FUTURE_PERIOD_NOTE, tomorrow.clarification)
    }

    @Test
    fun unsupportedPeriodPhrase_clarifiesBeforeScoring() {
        val question = IntentClassifier.classify("what did i spend all time")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, question.intent)
        assertEquals(IntentClassifier.UNSUPPORTED_PERIOD_NOTE, question.clarification)
        assertEquals(AssistantIntent.CLARIFY_PERIOD, intentOf("all my earnings this year"))
    }

    @Test
    fun importTokens_winBeforeScoring() {
        assertEquals(AssistantIntent.IMPORT, intentOf("how does import work"))
        assertEquals(AssistantIntent.IMPORT, intentOf("import transactions"))
        assertEquals(AssistantIntent.IMPORT, intentOf("how do I paste a CSV?"))
        assertEquals(AssistantIntent.IMPORT, intentOf("importing my bank export"))
        assertEquals(AssistantIntent.IMPORT, intentOf("how do I import a PDF statement?"))
        assertEquals(AssistantIntent.IMPORT, intentOf("can this app read a pdf?"))
    }

    @Test
    fun balance_strongPhraseAndKeyword() {
        assertEquals(AssistantIntent.BALANCE, intentOf("what is my balance?"))
        assertEquals(AssistantIntent.BALANCE, intentOf("How much money do I have?"))
        assertEquals(AssistantIntent.BALANCE, intentOf("show account balances"))
        assertEquals(AssistantIntent.BALANCE, intentOf("check balances"))
    }

    @Test
    fun income_withPeriod_classified() {
        val question = IntentClassifier.classify("how much income did i make this month")
        assertEquals(AssistantIntent.INCOME, question.intent)
        assertEquals(AssistantPeriod.THIS_MONTH, question.period)
    }

    @Test
    fun outflow_withLastWeekPeriod_classified() {
        val question = IntentClassifier.classify("what did i spend last week?")
        assertEquals(AssistantIntent.OUTFLOW, question.intent)
        assertEquals(AssistantPeriod.LAST_WEEK, question.period)
    }

    @Test
    fun budget_monthlyPeriodAllowed_nonMonthlyClarifies() {
        val allowed = IntentClassifier.classify("how is my budget this month")
        assertEquals(AssistantIntent.BUDGET, allowed.intent)
        assertEquals(AssistantPeriod.THIS_MONTH, allowed.period)

        val noPeriod = IntentClassifier.classify("how is my budget")
        assertEquals(AssistantIntent.BUDGET, noPeriod.intent)
        assertNull(noPeriod.period)
        assertEquals(AssistantPeriod.THIS_MONTH, noPeriod.intent.defaultPeriod)

        val today = IntentClassifier.classify("how is my budget today")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, today.intent)
        assertEquals(IntentClassifier.BUDGET_PERIOD_NOTE, today.clarification)

        val yesterday = IntentClassifier.classify("budget status yesterday")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, yesterday.intent)
        assertEquals(IntentClassifier.BUDGET_PERIOD_NOTE, yesterday.clarification)
    }

    @Test
    fun equalTopScores_classifiedAsAmbiguous() {
        val question = IntentClassifier.classify("income and spending")
        assertEquals(AssistantIntent.AMBIGUOUS, question.intent)
        assertNull(question.clarification)
    }

    @Test
    fun noScoringIntent_unsupported() {
        assertEquals(AssistantIntent.UNSUPPORTED, intentOf("what's the weather like"))
        assertEquals(AssistantIntent.UNSUPPORTED, intentOf("tell me a joke"))
    }

    @Test
    fun multiplePeriods_clarify() {
        val question = IntentClassifier.classify("what did i spend today and yesterday")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, question.intent)
        assertEquals(IntentClassifier.MULTI_PERIOD_NOTE, question.clarification)

        val weekAndMonth = IntentClassifier.classify("income this week and last month")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, weekAndMonth.intent)
        assertEquals(IntentClassifier.MULTI_PERIOD_NOTE, weekAndMonth.clarification)
    }

    @Test
    fun recentPeriod_forPeriodSensitiveIntents_clarifies() {
        val income = IntentClassifier.classify("how much did i earn recently")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, income.intent)
        assertEquals(IntentClassifier.RECENT_PERIOD_NOTE, income.clarification)

        val outflow = IntentClassifier.classify("what have i spent lately")
        assertEquals(AssistantIntent.CLARIFY_PERIOD, outflow.intent)
        assertEquals(IntentClassifier.RECENT_PERIOD_NOTE, outflow.clarification)
    }

    @Test
    fun recentPeriod_forTransactions_allowed() {
        val question = IntentClassifier.classify("show my recent transactions")
        assertEquals(AssistantIntent.TRANSACTIONS, question.intent)
        assertEquals(AssistantPeriod.RECENT, question.period)
    }

    @Test
    fun goalsWorkTelecomOpportunities_classified() {
        assertEquals(AssistantIntent.GOALS, intentOf("how are my goals?"))
        assertEquals(AssistantIntent.WORK, intentOf("which jobs are active?"))
        assertEquals(AssistantIntent.TELECOM, intentOf("when are my renewals?"))
        assertEquals(AssistantIntent.OPPORTUNITIES, intentOf("which opportunities need review"))
    }

    @Test
    fun singlePeriod_detection() {
        assertEquals(AssistantPeriod.TODAY, IntentClassifier.classify("spending today").period)
        assertEquals(AssistantPeriod.YESTERDAY, IntentClassifier.classify("spending yesterday").period)
        assertEquals(
            AssistantPeriod.THIS_WEEK,
            IntentClassifier.classify("what did i spend this week").period
        )
        assertEquals(
            AssistantPeriod.LAST_MONTH,
            IntentClassifier.classify("income last month").period
        )
    }

    @Test
    fun archivedToken_setsIncludeArchived() {
        val archived = IntentClassifier.classify("archived account balances")
        assertEquals(AssistantIntent.BALANCE, archived.intent)
        assertTrue(archived.includeArchived)

        val normal = IntentClassifier.classify("account balances")
        assertFalse(normal.includeArchived)
    }

    @Test
    fun resolvedPeriod_explicitWinsElseDefault() {
        val explicit = IntentClassifier.classify("income yesterday")
        assertEquals(AssistantPeriod.YESTERDAY, explicit.resolvedPeriod())

        val defaulted = IntentClassifier.classify("how much did i spend")
        assertEquals(AssistantPeriod.THIS_MONTH, defaulted.resolvedPeriod())

        val neverPeriod = IntentClassifier.classify("what is my balance")
        assertNull(neverPeriod.resolvedPeriod())
    }

    @Test
    fun requiresData_onlyDataIntentsReadRecords() {
        assertTrue(AssistantIntent.BALANCE.requiresData)
        assertTrue(AssistantIntent.INCOME.requiresData)
        assertTrue(AssistantIntent.OUTFLOW.requiresData)
        assertTrue(AssistantIntent.BUDGET.requiresData)
        assertTrue(AssistantIntent.GOALS.requiresData)
        assertTrue(AssistantIntent.WORK.requiresData)
        assertTrue(AssistantIntent.TELECOM.requiresData)
        assertTrue(AssistantIntent.OPPORTUNITIES.requiresData)
        assertTrue(AssistantIntent.TRANSACTIONS.requiresData)
        assertFalse(AssistantIntent.HELP.requiresData)
        assertFalse(AssistantIntent.IMPORT.requiresData)
        assertFalse(AssistantIntent.SECRET_TARGET_REFUSAL.requiresData)
        assertFalse(AssistantIntent.AMBIGUOUS.requiresData)
        assertFalse(AssistantIntent.CLARIFY_PERIOD.requiresData)
        assertFalse(AssistantIntent.UNSUPPORTED.requiresData)
    }

    @Test
    fun classification_isDeterministic() {
        val inputs = listOf(
            "what is my balance?",
            "how much did i spend this month",
            "income and spending",
            "what's the weather like",
            "secret target"
        )
        for (input in inputs) {
            val first = IntentClassifier.classify(input)
            repeat(5) { assertEquals(first, IntentClassifier.classify(input)) }
        }
    }
}
