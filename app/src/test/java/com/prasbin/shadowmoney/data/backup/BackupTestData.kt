package com.prasbin.shadowmoney.data.backup

/**
 * Shared fixtures for Phase 12 backup tests: one payload covering every
 * entity group with edge values (nulls, archived states, external refs,
 * unicode text, exact large Long amounts).
 */
object BackupTestData {

    const val NOW = 1_800_000_000_000L

    fun richPayload(): BackupPayload = BackupPayload(
        accounts = listOf(
            AccountBackup(
                id = 1L,
                name = "Wallet",
                type = 0,
                openingBalanceMinor = 100_000L,
                isActive = true,
                createdTimestamp = NOW
            ),
            AccountBackup(
                id = 7L,
                name = "Bank \"Primary\" €",
                type = 3,
                openingBalanceMinor = -1L,
                isActive = false,
                createdTimestamp = NOW - 1_000L
            )
        ),
        categories = listOf(
            CategoryBackup(
                id = 1L,
                name = "Food",
                direction = 1,
                isActive = true,
                isSystem = true,
                createdTimestamp = NOW
            ),
            CategoryBackup(
                id = 2L,
                name = "Salary काठमाडौं",
                direction = 0,
                isActive = false,
                isSystem = false,
                createdTimestamp = NOW
            )
        ),
        transactions = listOf(
            TransactionBackup(
                id = 11L,
                accountId = 1L,
                categoryId = 1L,
                workItemId = 3L,
                amountMinor = 12_345L,
                direction = 1,
                transactionTimestamp = NOW - 5_000L,
                note = "Café ☕ line\nbreak\ttab",
                createdTimestamp = NOW,
                source = "IMPORT_FILE",
                externalRef = "TX-42"
            ),
            TransactionBackup(
                id = 12L,
                accountId = 7L,
                categoryId = null,
                workItemId = null,
                amountMinor = 9_007_199_254_740_993L,
                direction = 0,
                transactionTimestamp = NOW,
                note = "",
                createdTimestamp = NOW,
                source = "manual",
                externalRef = null
            )
        ),
        goals = listOf(
            GoalBackup(
                id = 21L,
                name = "Laptop",
                targetAmountMinor = 250_000L,
                accountId = 1L,
                deadlineTimestamp = NOW + 86_400_000L,
                isActive = false,
                isCompleted = true,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            ),
            GoalBackup(
                id = 22L,
                name = "No account goal",
                targetAmountMinor = 0L,
                accountId = null,
                deadlineTimestamp = 0L,
                isActive = true,
                isCompleted = false,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        budgets = listOf(
            BudgetBackup(
                id = 31L,
                amountMinor = 30_000L,
                monthKey = "2026-09",
                categoryId = 1L,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            ),
            BudgetBackup(
                id = 32L,
                amountMinor = 10_000L,
                monthKey = "2026-09",
                categoryId = null,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        workItems = listOf(
            WorkItemBackup(
                id = 3L,
                title = "Logo design",
                description = "Client work with \"quotes\" and \\slashes\\",
                status = 3,
                expectedAmountMinor = 500_000L,
                deadlineTimestamp = NOW,
                client = "ACME",
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        telecomSims = listOf(
            TelecomSimBackup(
                id = 41L,
                label = "Backup SIM",
                carrier = "Ncell",
                phoneNumber = "+977-9800000000",
                status = 1,
                notes = "archived",
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            ),
            TelecomSimBackup(
                id = 42L,
                label = "Active SIM",
                carrier = "Namaste",
                phoneNumber = "+977-9811111111",
                status = 0,
                notes = "",
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        telecomPackages = listOf(
            TelecomPackageBackup(
                id = 51L,
                name = "Quarterly 4G",
                carrier = "Ncell",
                category = "Data",
                priceMinor = 999_000L,
                period = 2,
                notes = "",
                isActive = false,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        telecomSubscriptions = listOf(
            TelecomSubscriptionBackup(
                id = 61L,
                simId = 41L,
                packageId = 51L,
                startTimestamp = NOW - 10_000L,
                renewalTimestamp = NOW + 10_000L,
                monthlyCostMinor = 12_345L,
                isActive = false,
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        ),
        opportunities = listOf(
            OpportunityBackup(
                id = 71L,
                title = "Portal rework",
                description = "Project with unicode: naïve",
                type = 4,
                source = "Upwork",
                sourceUrl = "https://example.invalid/job/1",
                expectedAmountMinor = 1_500_000L,
                status = 5,
                deadlineTimestamp = NOW + 60_000L,
                client = "ACME",
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            ),
            OpportunityBackup(
                id = 72L,
                title = "Open application",
                description = "",
                type = 6,
                source = "",
                sourceUrl = "",
                expectedAmountMinor = null,
                status = 0,
                deadlineTimestamp = 0L,
                client = "",
                createdTimestamp = NOW,
                updatedTimestamp = NOW
            )
        )
    )

    fun emptyPayload(): BackupPayload = BackupPayload()

    fun write(payload: BackupPayload, createdAt: Long = NOW, schemaVersion: Int = APP_SCHEMA_VERSION): String =
        BackupSerializer.write(BackupBuilder.build(payload, createdAt, schemaVersion))
}
