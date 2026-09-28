package com.prasbin.shadowmoney.assistant

/**
 * Deterministic intent recognition. No network, no model, no randomness.
 *
 * ## Normalization
 * lowercase → collapse whitespace → trim → strip trailing punctuation
 * (`? ! . , ; :`). Keyword matching is whole-token; strong-phrase matching is
 * substring on the normalized text.
 *
 * ## Priority (checked in order)
 * 1. blank input → HELP
 * 2. Secret Target phrases → SECRET_TARGET_REFUSAL (hard privacy boundary)
 * 3. exact help phrasings → HELP
 * 4. future-period phrases → CLARIFY_PERIOD (never fabricate future data)
 * 5. unsupported-period phrases → CLARIFY_PERIOD
 * 6. import/csv tokens → IMPORT (workflow questions before scoring)
 * 7. scored intents: strong phrase = 3 points, keyword = 1 point;
 *    a tie between the top scorers → AMBIGUOUS (never guess);
 *    no scorer → UNSUPPORTED
 * 8. period extraction: more than one distinct period → CLARIFY_PERIOD
 * 9. intent-specific period validation (budget is monthly; income/spending
 *    need a real range for "recent")
 */
object IntentClassifier {

    private const val STRONG_WEIGHT = 3
    private const val KEYWORD_WEIGHT = 1

    const val TOPICS_TEXT =
        "balances, income, spending, budgets, goals, work, telecom, opportunities, transactions, and imports"

    const val SECRET_TARGET_REFUSAL_TEXT =
        "Secret Target information isn't available through the financial assistant."

    const val FUTURE_PERIOD_NOTE =
        "I can't answer about future periods. I can only use recorded periods: today, yesterday, this week, last week, this month, last month."

    const val UNSUPPORTED_PERIOD_NOTE =
        "I can only answer for recorded periods: today, yesterday, this week, last week, this month, last month."

    const val MULTI_PERIOD_NOTE =
        "Please ask about one period at a time: today, yesterday, this week, last week, this month, or last month."

    const val BUDGET_PERIOD_NOTE =
        "Budgets are monthly in this app. Ask about this month or last month."

    const val RECENT_PERIOD_NOTE =
        "Please ask for a specific period: today, this week, this month, or last month."

    private data class Pattern(
        val intent: AssistantIntent,
        val strong: List<String>,
        val keywords: Set<String>
    )

    private val patterns = listOf(
        Pattern(
            AssistantIntent.BALANCE,
            strong = listOf(
                "my balance", "account balances", "how much money do i have",
                "money do i have", "total balance", "how much do i have"
            ),
            keywords = setOf("balance", "balances")
        ),
        Pattern(
            AssistantIntent.INCOME,
            strong = listOf(
                "how much income", "my income", "what was my income",
                "how much did i earn", "how much have i earned", "did i earn"
            ),
            keywords = setOf("income", "earn", "earned", "earnings")
        ),
        Pattern(
            AssistantIntent.OUTFLOW,
            strong = listOf(
                "how much did i spend", "how much do i spend", "what did i spend",
                "where did my money go", "my spending", "how much have i spent"
            ),
            keywords = setOf("spend", "spending", "spent", "expenses", "expense", "outflow")
        ),
        Pattern(
            AssistantIntent.BUDGET,
            strong = listOf(
                "how is my budget", "am i over budget", "budget left",
                "budget remaining", "budget status", "my budget"
            ),
            keywords = setOf("budget", "budgets")
        ),
        Pattern(
            AssistantIntent.GOALS,
            strong = listOf(
                "how are my goals", "what goals", "goal progress", "my goals",
                "progress have i made"
            ),
            keywords = setOf("goal", "goals", "progress")
        ),
        Pattern(
            AssistantIntent.WORK,
            strong = listOf(
                "what work", "from my work", "work income", "expected work",
                "work items", "work am i", "jobs are active", "which jobs",
                "my work", "work tracker"
            ),
            keywords = setOf("work", "jobs", "job")
        ),
        Pattern(
            AssistantIntent.TELECOM,
            strong = listOf(
                "telecom cost", "expected telecom", "my telecom",
                "subscriptions", "renewal", "renewals"
            ),
            keywords = setOf(
                "telecom", "sim", "sims", "subscription", "subscriptions",
                "renew", "renews", "renewal", "renewals"
            )
        ),
        Pattern(
            AssistantIntent.OPPORTUNITIES,
            strong = listOf(
                "what opportunities", "opportunities do i", "need review",
                "expected opportunity", "my opportunities", "opportunity amounts"
            ),
            keywords = setOf("opportunity", "opportunities", "opps")
        ),
        Pattern(
            AssistantIntent.TRANSACTIONS,
            strong = listOf(
                "recent transactions", "latest transaction", "my transactions",
                "show my transactions", "transaction list", "recent transaction"
            ),
            keywords = setOf("transaction", "transactions")
        )
    )

    private val HELP_EXACT = setOf(
        "help",
        "what can you do",
        "what can i ask",
        "what can you answer",
        "what can you help with",
        "capabilities",
        "who are you",
        "what are you",
        "what do you do",
        "what are you capable of"
    )

    private val SECRET_PHRASES = listOf("secret target", "private target", "secret goal")

    private val FUTURE_PHRASES = listOf("next week", "next month", "next year", "tomorrow")

    private val UNSUPPORTED_PERIOD_PHRASES = listOf(
        "last year", "this year", "past year", "all time", "all-time",
        "lifetime", "all my life"
    )

    private val IMPORT_TOKENS = setOf("import", "imports", "imported", "importing", "csv")

    private val NON_MONTHLY_PERIODS = setOf(
        AssistantPeriod.TODAY,
        AssistantPeriod.YESTERDAY,
        AssistantPeriod.THIS_WEEK,
        AssistantPeriod.LAST_WEEK,
        AssistantPeriod.RECENT
    )

    private val PERIOD_SENSITIVE = setOf(AssistantIntent.INCOME, AssistantIntent.OUTFLOW)

    fun normalize(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('?', '!', '.', ',', ';', ':')

    fun classify(raw: String): ClassifiedQuestion {
        val normalized = normalize(raw)

        if (normalized.isBlank()) {
            return ClassifiedQuestion(AssistantIntent.HELP, normalizedInput = normalized)
        }

        if (SECRET_PHRASES.any { normalized.contains(it) }) {
            return ClassifiedQuestion(
                AssistantIntent.SECRET_TARGET_REFUSAL,
                normalizedInput = normalized
            )
        }

        if (normalized in HELP_EXACT) {
            return ClassifiedQuestion(AssistantIntent.HELP, normalizedInput = normalized)
        }

        if (FUTURE_PHRASES.any { normalized.contains(it) }) {
            return ClassifiedQuestion(
                AssistantIntent.CLARIFY_PERIOD,
                clarification = FUTURE_PERIOD_NOTE,
                normalizedInput = normalized
            )
        }

        if (UNSUPPORTED_PERIOD_PHRASES.any { normalized.contains(it) }) {
            return ClassifiedQuestion(
                AssistantIntent.CLARIFY_PERIOD,
                clarification = UNSUPPORTED_PERIOD_NOTE,
                normalizedInput = normalized
            )
        }

        val tokens = tokenize(normalized)
        if (tokens.any { it in IMPORT_TOKENS }) {
            return ClassifiedQuestion(AssistantIntent.IMPORT, normalizedInput = normalized)
        }

        val scored = patterns.mapNotNull { pattern ->
            val score = score(pattern, normalized, tokens)
            if (score > 0) pattern to score else null
        }
        if (scored.isEmpty()) {
            return ClassifiedQuestion(AssistantIntent.UNSUPPORTED, normalizedInput = normalized)
        }
        val topScore = scored.maxOf { it.second }
        val winners = scored.filter { it.second == topScore }
        if (winners.size > 1) {
            return ClassifiedQuestion(AssistantIntent.AMBIGUOUS, normalizedInput = normalized)
        }
        val intent = winners.first().first.intent

        val periods = detectPeriods(normalized, tokens)
        if (periods.size > 1) {
            return ClassifiedQuestion(
                AssistantIntent.CLARIFY_PERIOD,
                clarification = MULTI_PERIOD_NOTE,
                normalizedInput = normalized
            )
        }
        val period = periods.firstOrNull()

        if (intent == AssistantIntent.BUDGET && period != null && period in NON_MONTHLY_PERIODS) {
            return ClassifiedQuestion(
                AssistantIntent.CLARIFY_PERIOD,
                clarification = BUDGET_PERIOD_NOTE,
                normalizedInput = normalized
            )
        }
        if (intent in PERIOD_SENSITIVE && period == AssistantPeriod.RECENT) {
            return ClassifiedQuestion(
                AssistantIntent.CLARIFY_PERIOD,
                clarification = RECENT_PERIOD_NOTE,
                normalizedInput = normalized
            )
        }

        return ClassifiedQuestion(
            intent = intent,
            period = period,
            includeArchived = "archived" in tokens,
            normalizedInput = normalized
        )
    }

    private fun tokenize(normalized: String): List<String> =
        normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun score(pattern: Pattern, normalized: String, tokens: List<String>): Int {
        var score = 0
        for (phrase in pattern.strong) {
            if (normalized.contains(phrase)) score += STRONG_WEIGHT
        }
        val tokenSet = tokens.toSet()
        for (keyword in pattern.keywords) {
            if (keyword in tokenSet) score += KEYWORD_WEIGHT
        }
        return score
    }

    private fun detectPeriods(normalized: String, tokens: List<String>): List<AssistantPeriod> {
        val found = mutableListOf<AssistantPeriod>()
        fun add(period: AssistantPeriod) {
            if (period !in found) found.add(period)
        }
        if ("today" in tokens) add(AssistantPeriod.TODAY)
        if ("yesterday" in tokens) add(AssistantPeriod.YESTERDAY)
        if (normalized.contains("this week")) add(AssistantPeriod.THIS_WEEK)
        if (normalized.contains("last week")) add(AssistantPeriod.LAST_WEEK)
        if (normalized.contains("this month")) add(AssistantPeriod.THIS_MONTH)
        if (normalized.contains("last month") || normalized.contains("previous month")) {
            add(AssistantPeriod.LAST_MONTH)
        }
        if (tokens.any { it == "recent" || it == "recently" || it == "lately" || it == "latest" } ||
            normalized.contains("last few")
        ) {
            add(AssistantPeriod.RECENT)
        }
        return found
    }
}
