package com.prasbin.shadowmoney.assistant

import com.prasbin.shadowmoney.data.BudgetCalendar
import com.prasbin.shadowmoney.data.BudgetStatus
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.intelligence.Insight
import com.prasbin.shadowmoney.intelligence.InsightKind
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

const val ASSISTANT_SOURCE_TEXT = "Local financial records."
const val ASSISTANT_TRUST_LABEL = "Local records only — not a bank balance."
const val ASSISTANT_READ_ERROR_TEXT =
    "I couldn't read local records right now. Your records were not changed."
const val ASSISTANT_INSUFFICIENT_TEXT =
    "I don't have enough recorded data to answer that reliably."
const val ASSISTANT_HELP_TEXT =
    "I answer bounded questions about ${IntentClassifier.TOPICS_TEXT} using local records only. " +
        "I am read-only and deterministic — no network, no AI, and I never change financial records."
const val ASSISTANT_AMBIGUOUS_TEXT =
    "I can answer questions about ${IntentClassifier.TOPICS_TEXT}. Which one do you mean?"
const val ASSISTANT_UNSUPPORTED_TEXT =
    "I can answer questions about ${IntentClassifier.TOPICS_TEXT}."
const val ASSISTANT_IMPORT_TEXT =
    "Real statement imports are local and manual: you pick a file (CSV or PDF) or paste CSV " +
        "text, review a read-only preview, confirm the detected source, then confirm. Confirmed " +
        "rows become normal transactions marked IMPORT_FILE with IMPORTED / USER-PROVIDED " +
        "provenance, and a statement evidence record keeps the period, counts and the " +
        "document's own reported balance — never labeled connected or verified. Importing the " +
        "same document again shows a possible-duplicate for review instead of silently " +
        "skipping. Nothing is uploaded, and this assistant never starts an import."
const val ASSISTANT_GOALS_EMPTY_TEXT = "No goals recorded."
const val ASSISTANT_WORK_EMPTY_TEXT = "No work items recorded."
const val ASSISTANT_TELECOM_EMPTY_TEXT = "No telecom records yet."
const val ASSISTANT_OPPORTUNITIES_EMPTY_TEXT = "No opportunities recorded."
const val ASSISTANT_RECENT_EMPTY_TEXT = "No transactions recorded yet."

const val ASSISTANT_ACCOUNT_LINES_LIMIT = 8
const val ASSISTANT_TRANSACTION_LINES_LIMIT = 5
const val ASSISTANT_GOAL_LINES_LIMIT = 5
const val ASSISTANT_WORK_LINES_LIMIT = 5
const val ASSISTANT_OPPORTUNITY_LINES_LIMIT = 5
const val ASSISTANT_CATEGORY_LINES_LIMIT = 5
const val ASSISTANT_RENEWAL_LINES_LIMIT = 5
const val ASSISTANT_BUDGET_LINES_LIMIT = 5

data class AssistantSection(val kind: InsightKind?, val text: String)

data class AssistantResponse(
    val sections: List<AssistantSection>,
    val source: String = ASSISTANT_SOURCE_TEXT
)

/**
 * Pure deterministic response renderer. Answers only from the provided
 * read-only [AssistantData]; never fabricates, never writes.
 */
class AssistantEngine {

    fun answer(question: ClassifiedQuestion, data: AssistantData?): AssistantResponse {
        question.clarification?.let { return plain(it) }
        return when (question.intent) {
            AssistantIntent.HELP -> plain(ASSISTANT_HELP_TEXT)
            AssistantIntent.SECRET_TARGET_REFUSAL ->
                plain(IntentClassifier.SECRET_TARGET_REFUSAL_TEXT)
            AssistantIntent.AMBIGUOUS -> plain(ASSISTANT_AMBIGUOUS_TEXT)
            AssistantIntent.UNSUPPORTED -> plain(ASSISTANT_UNSUPPORTED_TEXT)
            AssistantIntent.CLARIFY_PERIOD ->
                plain(question.clarification ?: IntentClassifier.UNSUPPORTED_PERIOD_NOTE)
            AssistantIntent.IMPORT -> plain(ASSISTANT_IMPORT_TEXT)
            else -> {
                if (data == null) return plain(ASSISTANT_INSUFFICIENT_TEXT)
                when (question.intent) {
                    AssistantIntent.BALANCE -> balance(question, data)
                    AssistantIntent.INCOME -> income(data)
                    AssistantIntent.OUTFLOW -> outflow(data)
                    AssistantIntent.BUDGET -> budget(question, data)
                    AssistantIntent.GOALS -> goals(data)
                    AssistantIntent.WORK -> work(data)
                    AssistantIntent.TELECOM -> telecom(data)
                    AssistantIntent.OPPORTUNITIES -> opportunities(data)
                    AssistantIntent.TRANSACTIONS -> transactions(question, data)
                    else -> plain(ASSISTANT_UNSUPPORTED_TEXT)
                }
            }
        }
    }

    private fun balance(question: ClassifiedQuestion, data: AssistantData): AssistantResponse {
        val sections = mutableListOf<AssistantSection>()
        val active = data.accounts.filter { it.isActive }
        if (active.isEmpty()) {
            sections.add(
                fact("$ASSISTANT_TRUST_LABEL No active accounts recorded yet.")
            )
        } else {
            val noun = if (active.size == 1) "account" else "accounts"
            sections.add(
                fact(
                    "$ASSISTANT_TRUST_LABEL Recorded balance across ${active.size} active " +
                        "$noun: ${fmt(data.totalBalanceActiveMinor)}."
                )
            )
            sections.add(
                fact(
                    "Active accounts:\n" +
                        accountLines(active.take(ASSISTANT_ACCOUNT_LINES_LIMIT)) +
                        moreLines(active.size, ASSISTANT_ACCOUNT_LINES_LIMIT)
                )
            )
        }
        if (question.includeArchived) {
            val archived = data.accounts.filter { !it.isActive }
            if (archived.isEmpty()) {
                sections.add(fact("No archived accounts recorded."))
            } else {
                sections.add(
                    fact(
                        "Archived accounts (reference only, not part of the active balance):\n" +
                            accountLines(archived.take(ASSISTANT_ACCOUNT_LINES_LIMIT)) +
                            moreLines(archived.size, ASSISTANT_ACCOUNT_LINES_LIMIT)
                    )
                )
            }
        }
        return AssistantResponse(sections)
    }

    private fun income(data: AssistantData): AssistantResponse {
        val totals = data.periodTotals ?: return plain(ASSISTANT_INSUFFICIENT_TEXT)
        val label = data.periodLabel ?: "this month"
        if (totals.transactionCount == 0) {
            return AssistantResponse(listOf(fact("No transactions recorded $label.")))
        }
        val noun = if (totals.incomeCount == 1) "transaction" else "transactions"
        return AssistantResponse(
            listOf(
                fact(
                    "Recorded income $label: ${fmt(totals.incomeMinor)} across " +
                        "${totals.incomeCount} income $noun."
                ),
                calculation(
                    "Recorded income minus recorded outflow $label: ${fmt(totals.netMinor)}."
                )
            )
        )
    }

    private fun outflow(data: AssistantData): AssistantResponse {
        val totals = data.periodTotals ?: return plain(ASSISTANT_INSUFFICIENT_TEXT)
        val label = data.periodLabel ?: "this month"
        if (totals.transactionCount == 0) {
            return AssistantResponse(listOf(fact("No transactions recorded $label.")))
        }
        val sections = mutableListOf<AssistantSection>()
        val noun = if (totals.outflowCount == 1) "transaction" else "transactions"
        sections.add(
            fact(
                "Recorded outflow $label: ${fmt(totals.outflowMinor)} across " +
                    "${totals.outflowCount} outflow $noun."
            )
        )
        val breakdown = totals.categoryBreakdown
        if (breakdown.isNotEmpty()) {
            val top = breakdown.first()
            sections.add(
                analysis(
                    "Your largest recorded spending category $label is ${top.name} " +
                        "(${fmt(top.totalMinor)})."
                )
            )
            sections.add(
                fact(
                    "Spending by category $label (recorded outflow):\n" +
                        categoryLines(breakdown.take(ASSISTANT_CATEGORY_LINES_LIMIT)) +
                        moreLines(breakdown.size, ASSISTANT_CATEGORY_LINES_LIMIT)
                )
            )
        }
        data.projectionInsight?.let { projection ->
            sections.add(projection(projection))
        }
        return AssistantResponse(sections)
    }

    private fun budget(question: ClassifiedQuestion, data: AssistantData): AssistantResponse {
        val info = if (question.resolvedPeriod() == AssistantPeriod.LAST_MONTH) {
            data.budgetLast
        } else {
            data.budgetCurrent
        }
        if (!info.hasAny) {
            return AssistantResponse(listOf(fact("No budget recorded for ${info.monthLabel}.")))
        }
        val sections = mutableListOf<AssistantSection>()
        info.overall?.let { overall ->
            sections.add(
                calculation(
                    "Overall budget ${info.monthLabel}: ${fmt(overall.amountMinor)}. " +
                        "Spent: ${fmt(overall.spentMinor)} (recorded outflow only). " +
                        "Remaining: ${fmt(overall.remainingMinor)} — ${overall.percentUsed}% used. " +
                        "Status: ${statusLabel(overall.status)}."
                )
            )
        }
        if (info.categories.isNotEmpty()) {
            val lines = info.categories.take(ASSISTANT_BUDGET_LINES_LIMIT).joinToString("\n") { item ->
                "• ${item.label}: ${fmt(item.amountMinor)} — spent ${fmt(item.spentMinor)} " +
                    "(${item.percentUsed}% used)"
            }
            sections.add(
                calculation(
                    "Category budgets ${info.monthLabel}:\n$lines" +
                        moreLines(info.categories.size, ASSISTANT_BUDGET_LINES_LIMIT)
                )
            )
        }
        return AssistantResponse(sections)
    }

    private fun goals(data: AssistantData): AssistantResponse {
        if (data.goals.isEmpty()) {
            return AssistantResponse(listOf(fact(ASSISTANT_GOALS_EMPTY_TEXT)))
        }
        val noun = if (data.goals.size == 1) "goal" else "goals"
        val lines = data.goals.take(ASSISTANT_GOAL_LINES_LIMIT).joinToString("\n") { goal ->
            if (goal.accountName == null) {
                "• ${goal.name}: no account linked (target ${fmt(goal.targetMinor)})"
            } else {
                "• ${goal.name}: ${fmt(goal.currentMinor)} of ${fmt(goal.targetMinor)} " +
                    "(${goal.percentUsed.coerceIn(0, 100)}%) — account ${goal.accountName}"
            }
        }
        return AssistantResponse(
            listOf(
                fact("${data.goals.size} $noun recorded."),
                calculation(
                    "Goal progress (linked-account balance against target):\n$lines" +
                        moreLines(data.goals.size, ASSISTANT_GOAL_LINES_LIMIT)
                )
            )
        )
    }

    private fun work(data: AssistantData): AssistantResponse {
        if (data.work.isEmpty()) {
            return AssistantResponse(listOf(fact(ASSISTANT_WORK_EMPTY_TEXT)))
        }
        val activeCount = data.work.count { it.isActive }
        val lines = data.work.take(ASSISTANT_WORK_LINES_LIMIT).joinToString("\n") { item ->
            "• ${item.title} — ${item.statusLabel}"
        }
        val expectedTotal = data.work.sumOf { it.expectedMinor }
        val receivedTotal = data.work.sumOf { it.receivedMinor }
        val remainingTotal = data.work.sumOf { it.remainingExpectedMinor }
        return AssistantResponse(
            listOf(
                fact(
                    "${data.work.size} work items tracked ($activeCount active):\n$lines" +
                        moreLines(data.work.size, ASSISTANT_WORK_LINES_LIMIT)
                ),
                calculation(
                    "Expected work amounts total ${fmt(expectedTotal)}. Actually received from " +
                        "linked income: ${fmt(receivedTotal)}. Remaining expected: " +
                        "${fmt(remainingTotal)}. Expected amounts are estimates — not income."
                )
            )
        )
    }

    private fun telecom(data: AssistantData): AssistantResponse {
        val info = data.telecom ?: return plain(ASSISTANT_TELECOM_EMPTY_TEXT)
        val sections = mutableListOf<AssistantSection>()
        sections.add(
            fact(
                "${info.activeSimCount} active SIMs, " +
                    "${info.activeSubscriptionCount} active subscriptions."
            )
        )
        sections.add(
            calculation(
                "Expected monthly telecom cost: ${fmt(info.expectedMonthlyCostMinor)} — " +
                    "expected cost, not recorded spending."
            )
        )
        if (info.upcomingRenewals.isEmpty()) {
            sections.add(fact("No renewals due within the next 60 days."))
        } else {
            val lines = info.upcomingRenewals.take(ASSISTANT_RENEWAL_LINES_LIMIT)
                .joinToString("\n") { renewal ->
                    "• ${formatDate(renewal.renewalTimestamp)} · ${renewal.simLabel} · " +
                        renewal.packageName
                }
            sections.add(
                fact(
                    "Renewals due within the next 60 days:\n$lines" +
                        moreLines(info.upcomingRenewals.size, ASSISTANT_RENEWAL_LINES_LIMIT)
                )
            )
        }
        return AssistantResponse(sections)
    }

    private fun opportunities(data: AssistantData): AssistantResponse {
        val info = data.opportunities
            ?: return plain(ASSISTANT_OPPORTUNITIES_EMPTY_TEXT)
        val sections = mutableListOf<AssistantSection>()
        sections.add(
            fact("${info.activeCount} active opportunities. ${info.needsReviewCount} need review.")
        )
        sections.add(
            fact(
                "Recorded expected opportunity amounts total " +
                    "${fmt(info.totalExpectedAmountMinor)} — expected amounts, not income."
            )
        )
        if (info.tracked.isNotEmpty()) {
            val lines = info.tracked.take(ASSISTANT_OPPORTUNITY_LINES_LIMIT)
                .joinToString("\n") { (title, status) -> "• $title — $status" }
            sections.add(
                fact(
                    "Tracked (stored order, not ranked):\n$lines" +
                        moreLines(info.tracked.size, ASSISTANT_OPPORTUNITY_LINES_LIMIT)
                )
            )
        }
        return AssistantResponse(sections)
    }

    private fun transactions(question: ClassifiedQuestion, data: AssistantData): AssistantResponse {
        val period = question.resolvedPeriod()
        val withPeriod = period != null && period != AssistantPeriod.RECENT && data.periodRange != null
        val list = if (withPeriod) data.windowTransactions else data.recentTransactions
        if (list.isEmpty()) {
            return AssistantResponse(
                listOf(
                    fact(
                        if (withPeriod) "No transactions recorded ${data.periodLabel}."
                        else ASSISTANT_RECENT_EMPTY_TEXT
                    )
                )
            )
        }
        val shown = list.take(ASSISTANT_TRANSACTION_LINES_LIMIT)
        val header = if (withPeriod) {
            "${data.periodTotals?.transactionCount ?: list.size} transactions recorded " +
                "${data.periodLabel}, newest first:"
        } else {
            "Your ${shown.size} most recent recorded transactions:"
        }
        val lines = shown.joinToString("\n") { tx ->
            val direction = if (tx.direction == TRANSACTION_DIRECTION_INCOME) "in" else "out"
            val note = tx.note.ifBlank { "Transaction" }
            val category = tx.categoryName?.let { " · $it" } ?: ""
            "• ${formatDate(tx.timestamp)} — $note — ${fmt(tx.amountMinor)} $direction · " +
                "${tx.accountName}$category"
        }
        return AssistantResponse(
            listOf(fact("$header\n$lines" + moreLines(list.size, ASSISTANT_TRANSACTION_LINES_LIMIT)))
        )
    }

    private fun projection(insight: Insight): AssistantSection {
        val amount = insight.amountMinor?.let { fmt(it) } ?: "an amount not recorded"
        return AssistantSection(
            kind = InsightKind.PROJECTION,
            text = "PROJECTION — based on the existing historical-spending projection: " +
                "$amount projected monthly outflow. Projection only — not a guarantee."
        )
    }

    private fun accountLines(accounts: List<AssistantAccountInfo>): String =
        accounts.joinToString("\n") { "• ${it.name}: ${fmt(it.balanceMinor)}" }

    private fun categoryLines(categories: List<AssistantCategorySpend>): String =
        categories.joinToString("\n") { "• ${it.name}: ${fmt(it.totalMinor)}" }

    private fun moreLines(total: Int, limit: Int): String =
        if (total > limit) "\n…and ${total - limit} more" else ""

    private fun statusLabel(status: BudgetStatus): String = when (status) {
        BudgetStatus.NORMAL -> "on track"
        BudgetStatus.APPROACHING -> "approaching limit"
        BudgetStatus.OVER_BUDGET -> "over budget"
    }

    private fun fmt(minor: Long): String = Money.formatNpr(minor)

    private fun formatDate(timestamp: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(
            ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), BudgetCalendar.KATHMANDU_ZONE)
        )

    private fun fact(text: String) = AssistantSection(InsightKind.FACT, text)
    private fun calculation(text: String) = AssistantSection(InsightKind.CALCULATION, text)
    private fun analysis(text: String) = AssistantSection(InsightKind.ANALYSIS, text)
    private fun plain(text: String) = AssistantResponse(listOf(AssistantSection(null, text)))
}
