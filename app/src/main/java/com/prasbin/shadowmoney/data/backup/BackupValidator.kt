package com.prasbin.shadowmoney.data.backup

import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_DIGITAL_WALLET
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_YEARLY
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_BOTH
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_OTHER
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import java.time.YearMonth

/**
 * Semantic validation of a decoded backup payload: positive unique primary
 * keys, enum ranges, YYYY-MM month keys, non-negative minor-unit amounts and
 * referential integrity for every foreign key. A failing file is rejected as
 * a whole; nothing is repaired, skipped or written on failure.
 */
object BackupValidator {

    private val MONTH_KEY_PATTERN = Regex("\\d{4}-\\d{2}")

    fun validate(payload: BackupPayload): ValidationOutcome {
        val accountIds = mutableSetOf<Long>()
        for ((index, account) in payload.accounts.withIndex()) {
            if (account.id <= 0) return malformed("accounts[$index].id must be a positive number")
            if (!accountIds.add(account.id)) return malformed("duplicate account id ${account.id}")
            if (account.type < ACCOUNT_TYPE_WALLET || account.type > ACCOUNT_TYPE_DIGITAL_WALLET) {
                return malformed("accounts[$index].type ${account.type} is not a valid account type")
            }
        }

        val categoryIds = mutableSetOf<Long>()
        for ((index, category) in payload.categories.withIndex()) {
            if (category.id <= 0) return malformed("categories[$index].id must be a positive number")
            if (!categoryIds.add(category.id)) return malformed("duplicate category id ${category.id}")
            if (category.direction < CATEGORY_DIRECTION_INCOME || category.direction > CATEGORY_DIRECTION_BOTH) {
                return malformed("categories[$index].direction ${category.direction} is not a valid direction")
            }
        }

        val workItemIds = mutableSetOf<Long>()
        for ((index, work) in payload.workItems.withIndex()) {
            if (work.id <= 0) return malformed("workItems[$index].id must be a positive number")
            if (!workItemIds.add(work.id)) return malformed("duplicate work item id ${work.id}")
            if (work.status < 0 || work.status > WORK_STATUS_ARCHIVED) {
                return malformed("workItems[$index].status ${work.status} is not a valid status")
            }
            if (work.expectedAmountMinor < 0) {
                return malformed("workItems[$index].expectedAmountMinor must not be negative")
            }
        }

        val simIds = mutableSetOf<Long>()
        for ((index, sim) in payload.telecomSims.withIndex()) {
            if (sim.id <= 0) return malformed("telecomSims[$index].id must be a positive number")
            if (!simIds.add(sim.id)) return malformed("duplicate SIM id ${sim.id}")
            if (sim.status < SIM_STATUS_ACTIVE || sim.status > SIM_STATUS_ARCHIVED) {
                return malformed("telecomSims[$index].status ${sim.status} is not a valid status")
            }
        }

        val packageIds = mutableSetOf<Long>()
        for ((index, telecomPackage) in payload.telecomPackages.withIndex()) {
            if (telecomPackage.id <= 0) {
                return malformed("telecomPackages[$index].id must be a positive number")
            }
            if (!packageIds.add(telecomPackage.id)) {
                return malformed("duplicate telecom package id ${telecomPackage.id}")
            }
            if (telecomPackage.period < 0 || telecomPackage.period > BILLING_PERIOD_YEARLY) {
                return malformed(
                    "telecomPackages[$index].period ${telecomPackage.period} is not a valid billing period"
                )
            }
            if (telecomPackage.priceMinor < 0) {
                return malformed("telecomPackages[$index].priceMinor must not be negative")
            }
        }

        val subscriptionIds = mutableSetOf<Long>()
        for ((index, subscription) in payload.telecomSubscriptions.withIndex()) {
            if (subscription.id <= 0) {
                return malformed("telecomSubscriptions[$index].id must be a positive number")
            }
            if (!subscriptionIds.add(subscription.id)) {
                return malformed("duplicate telecom subscription id ${subscription.id}")
            }
            if (subscription.simId !in simIds) {
                return missingRef(
                    "telecomSubscriptions[$index] references SIM ${subscription.simId} which does not exist"
                )
            }
            if (subscription.packageId !in packageIds) {
                return missingRef(
                    "telecomSubscriptions[$index] references telecom package ${subscription.packageId} which does not exist"
                )
            }
            if (subscription.monthlyCostMinor < 0) {
                return malformed("telecomSubscriptions[$index].monthlyCostMinor must not be negative")
            }
        }

        val transactionIds = mutableSetOf<Long>()
        for ((index, transaction) in payload.transactions.withIndex()) {
            if (transaction.id <= 0) return malformed("transactions[$index].id must be a positive number")
            if (!transactionIds.add(transaction.id)) return malformed("duplicate transaction id ${transaction.id}")
            if (transaction.accountId !in accountIds) {
                return missingRef(
                    "transactions[$index] references account ${transaction.accountId} which does not exist"
                )
            }
            val categoryId = transaction.categoryId
            if (categoryId != null && categoryId !in categoryIds) {
                return missingRef(
                    "transactions[$index] references category $categoryId which does not exist"
                )
            }
            val workItemId = transaction.workItemId
            if (workItemId != null && workItemId !in workItemIds) {
                return missingRef(
                    "transactions[$index] references work item $workItemId which does not exist"
                )
            }
            if (transaction.direction < TRANSACTION_DIRECTION_INCOME ||
                transaction.direction > TRANSACTION_DIRECTION_OUTFLOW
            ) {
                return malformed("transactions[$index].direction ${transaction.direction} is not a valid direction")
            }
            if (transaction.amountMinor < 0) {
                return malformed("transactions[$index].amountMinor must not be negative")
            }
        }

        val goalIds = mutableSetOf<Long>()
        for ((index, goal) in payload.goals.withIndex()) {
            if (goal.id <= 0) return malformed("goals[$index].id must be a positive number")
            if (!goalIds.add(goal.id)) return malformed("duplicate goal id ${goal.id}")
            val accountId = goal.accountId
            if (accountId != null && accountId !in accountIds) {
                return missingRef("goals[$index] references account $accountId which does not exist")
            }
            if (goal.targetAmountMinor < 0) {
                return malformed("goals[$index].targetAmountMinor must not be negative")
            }
        }

        val budgetIds = mutableSetOf<Long>()
        val budgetSlots = mutableSetOf<Pair<String, Long?>>()
        for ((index, budget) in payload.budgets.withIndex()) {
            if (budget.id <= 0) return malformed("budgets[$index].id must be a positive number")
            if (!budgetIds.add(budget.id)) return malformed("duplicate budget id ${budget.id}")
            if (!MONTH_KEY_PATTERN.matches(budget.monthKey) || !isValidMonthKey(budget.monthKey)) {
                return malformed("budgets[$index].monthKey '${budget.monthKey}' must be a valid YYYY-MM month")
            }
            val categoryId = budget.categoryId
            if (categoryId != null && categoryId !in categoryIds) {
                return missingRef("budgets[$index] references category $categoryId which does not exist")
            }
            if (!budgetSlots.add(budget.monthKey to categoryId)) {
                return malformed("budgets[$index] duplicates the budget for month ${budget.monthKey}")
            }
            if (budget.amountMinor < 0) {
                return malformed("budgets[$index].amountMinor must not be negative")
            }
        }

        val opportunityIds = mutableSetOf<Long>()
        for ((index, opportunity) in payload.opportunities.withIndex()) {
            if (opportunity.id <= 0) return malformed("opportunities[$index].id must be a positive number")
            if (!opportunityIds.add(opportunity.id)) {
                return malformed("duplicate opportunity id ${opportunity.id}")
            }
            if (opportunity.type < 0 || opportunity.type > OPPORTUNITY_TYPE_OTHER) {
                return malformed(
                    "opportunities[$index].type ${opportunity.type} is not a valid opportunity type"
                )
            }
            if (opportunity.status < 0 || opportunity.status > OPPORTUNITY_STATUS_ARCHIVED) {
                return malformed(
                    "opportunities[$index].status ${opportunity.status} is not a valid status"
                )
            }
            val expected = opportunity.expectedAmountMinor
            if (expected != null && expected < 0) {
                return malformed("opportunities[$index].expectedAmountMinor must not be negative")
            }
        }

        return ValidationOutcome.Valid(countsOf(payload))
    }

    fun countsOf(payload: BackupPayload): BackupRecordCounts = BackupRecordCounts(
        accounts = payload.accounts.size,
        categories = payload.categories.size,
        transactions = payload.transactions.size,
        goals = payload.goals.size,
        budgets = payload.budgets.size,
        workItems = payload.workItems.size,
        telecomSims = payload.telecomSims.size,
        telecomPackages = payload.telecomPackages.size,
        telecomSubscriptions = payload.telecomSubscriptions.size,
        opportunities = payload.opportunities.size
    )

    private fun malformed(detail: String): ValidationOutcome.Invalid =
        ValidationOutcome.Invalid(BackupError(BackupErrorCode.MALFORMED_RECORDS, detail))

    private fun missingRef(detail: String): ValidationOutcome.Invalid =
        ValidationOutcome.Invalid(BackupError(BackupErrorCode.MISSING_REFERENCE, detail))

    private fun isValidMonthKey(monthKey: String): Boolean = try {
        YearMonth.parse(monthKey)
        true
    } catch (e: Exception) {
        false
    }
}
