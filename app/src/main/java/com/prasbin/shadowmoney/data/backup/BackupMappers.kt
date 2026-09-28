package com.prasbin.shadowmoney.data.backup

import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WorkItem

fun Account.toBackup(): AccountBackup = AccountBackup(
    id = id,
    name = name,
    type = type,
    openingBalanceMinor = openingBalanceMinor,
    isActive = isActive,
    createdTimestamp = createdTimestamp
)

fun AccountBackup.toEntity(): Account = Account(
    id = id,
    name = name,
    type = type,
    openingBalanceMinor = openingBalanceMinor,
    isActive = isActive,
    createdTimestamp = createdTimestamp
)

fun Category.toBackup(): CategoryBackup = CategoryBackup(
    id = id,
    name = name,
    direction = direction,
    isActive = isActive,
    isSystem = isSystem,
    createdTimestamp = createdTimestamp
)

fun CategoryBackup.toEntity(): Category = Category(
    id = id,
    name = name,
    direction = direction,
    isActive = isActive,
    isSystem = isSystem,
    createdTimestamp = createdTimestamp
)

fun Transaction.toBackup(): TransactionBackup = TransactionBackup(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    workItemId = workItemId,
    amountMinor = amountMinor,
    direction = direction,
    transactionTimestamp = transactionTimestamp,
    note = note,
    createdTimestamp = createdTimestamp,
    source = source,
    externalRef = externalRef
)

fun TransactionBackup.toEntity(): Transaction = Transaction(
    id = id,
    accountId = accountId,
    categoryId = categoryId,
    workItemId = workItemId,
    amountMinor = amountMinor,
    direction = direction,
    transactionTimestamp = transactionTimestamp,
    note = note,
    createdTimestamp = createdTimestamp,
    source = source,
    externalRef = externalRef
)

fun Goal.toBackup(): GoalBackup = GoalBackup(
    id = id,
    name = name,
    targetAmountMinor = targetAmountMinor,
    accountId = accountId,
    deadlineTimestamp = deadlineTimestamp,
    isActive = isActive,
    isCompleted = isCompleted,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun GoalBackup.toEntity(): Goal = Goal(
    id = id,
    name = name,
    targetAmountMinor = targetAmountMinor,
    accountId = accountId,
    deadlineTimestamp = deadlineTimestamp,
    isActive = isActive,
    isCompleted = isCompleted,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun Budget.toBackup(): BudgetBackup = BudgetBackup(
    id = id,
    amountMinor = amountMinor,
    monthKey = monthKey,
    categoryId = categoryId,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun BudgetBackup.toEntity(): Budget = Budget(
    id = id,
    amountMinor = amountMinor,
    monthKey = monthKey,
    categoryId = categoryId,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun WorkItem.toBackup(): WorkItemBackup = WorkItemBackup(
    id = id,
    title = title,
    description = description,
    status = status,
    expectedAmountMinor = expectedAmountMinor,
    deadlineTimestamp = deadlineTimestamp,
    client = client,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun WorkItemBackup.toEntity(): WorkItem = WorkItem(
    id = id,
    title = title,
    description = description,
    status = status,
    expectedAmountMinor = expectedAmountMinor,
    deadlineTimestamp = deadlineTimestamp,
    client = client,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomSim.toBackup(): TelecomSimBackup = TelecomSimBackup(
    id = id,
    label = label,
    carrier = carrier,
    phoneNumber = phoneNumber,
    status = status,
    notes = notes,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomSimBackup.toEntity(): TelecomSim = TelecomSim(
    id = id,
    label = label,
    carrier = carrier,
    phoneNumber = phoneNumber,
    status = status,
    notes = notes,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomPackage.toBackup(): TelecomPackageBackup = TelecomPackageBackup(
    id = id,
    name = name,
    carrier = carrier,
    category = category,
    priceMinor = priceMinor,
    period = period,
    notes = notes,
    isActive = isActive,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomPackageBackup.toEntity(): TelecomPackage = TelecomPackage(
    id = id,
    name = name,
    carrier = carrier,
    category = category,
    priceMinor = priceMinor,
    period = period,
    notes = notes,
    isActive = isActive,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomSubscription.toBackup(): TelecomSubscriptionBackup = TelecomSubscriptionBackup(
    id = id,
    simId = simId,
    packageId = packageId,
    startTimestamp = startTimestamp,
    renewalTimestamp = renewalTimestamp,
    monthlyCostMinor = monthlyCostMinor,
    isActive = isActive,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun TelecomSubscriptionBackup.toEntity(): TelecomSubscription = TelecomSubscription(
    id = id,
    simId = simId,
    packageId = packageId,
    startTimestamp = startTimestamp,
    renewalTimestamp = renewalTimestamp,
    monthlyCostMinor = monthlyCostMinor,
    isActive = isActive,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun Opportunity.toBackup(): OpportunityBackup = OpportunityBackup(
    id = id,
    title = title,
    description = description,
    type = type,
    source = source,
    sourceUrl = sourceUrl,
    expectedAmountMinor = expectedAmountMinor,
    status = status,
    deadlineTimestamp = deadlineTimestamp,
    client = client,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)

fun OpportunityBackup.toEntity(): Opportunity = Opportunity(
    id = id,
    title = title,
    description = description,
    type = type,
    source = source,
    sourceUrl = sourceUrl,
    expectedAmountMinor = expectedAmountMinor,
    status = status,
    deadlineTimestamp = deadlineTimestamp,
    client = client,
    createdTimestamp = createdTimestamp,
    updatedTimestamp = updatedTimestamp
)
