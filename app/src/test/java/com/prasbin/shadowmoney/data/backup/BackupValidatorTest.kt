package com.prasbin.shadowmoney.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun validPayload(): BackupPayload = BackupTestData.richPayload()

    private fun invalid(payload: BackupPayload): ValidationOutcome {
        val outcome = BackupValidator.validate(payload)
        assertTrue("expected Invalid but was $outcome", outcome is ValidationOutcome.Invalid)
        return outcome
    }

    private fun code(payload: BackupPayload): BackupErrorCode =
        (invalid(payload) as ValidationOutcome.Invalid).error.code

    private fun detail(payload: BackupPayload): String =
        (invalid(payload) as ValidationOutcome.Invalid).error.message

    @Test
    fun fullPayload_isValidWithExactCounts() {
        val outcome = BackupValidator.validate(validPayload())
        assertTrue(outcome is ValidationOutcome.Valid)
        val counts = (outcome as ValidationOutcome.Valid).counts
        assertEquals(2, counts.accounts)
        assertEquals(2, counts.categories)
        assertEquals(2, counts.transactions)
        assertEquals(2, counts.goals)
        assertEquals(2, counts.budgets)
        assertEquals(1, counts.workItems)
        assertEquals(2, counts.telecomSims)
        assertEquals(1, counts.telecomPackages)
        assertEquals(1, counts.telecomSubscriptions)
        assertEquals(2, counts.opportunities)
        assertEquals(17, counts.total)
        assertTrue(!counts.isEmpty)
    }

    @Test
    fun emptyPayload_isValidExplicitly() {
        val outcome = BackupValidator.validate(BackupTestData.emptyPayload())
        assertTrue(outcome is ValidationOutcome.Valid)
        assertTrue((outcome as ValidationOutcome.Valid).counts.isEmpty)
    }

    @Test
    fun transactionWithMissingAccount_isMissingReference() {
        val payload = validPayload().copy(
            accounts = emptyList()
        )
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
        assertTrue(detail(payload).contains("account 1 which does not exist"))
    }

    @Test
    fun transactionWithMissingCategory_isMissingReference() {
        val payload = validPayload().copy(categories = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
        assertTrue(detail(payload).contains("category 1 which does not exist"))
    }

    @Test
    fun transactionWithMissingWorkItem_isMissingReference() {
        val payload = validPayload().copy(workItems = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
        assertTrue(detail(payload).contains("work item 3 which does not exist"))
    }

    @Test
    fun goalWithMissingAccount_isMissingReference() {
        val payload = validPayload().copy(accounts = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
    }

    @Test
    fun budgetWithMissingCategory_isMissingReference() {
        val payload = validPayload().copy(categories = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
    }

    @Test
    fun subscriptionWithMissingSim_isMissingReference() {
        val payload = validPayload().copy(telecomSims = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
        assertTrue(detail(payload).contains("SIM 41 which does not exist"))
    }

    @Test
    fun subscriptionWithMissingPackage_isMissingReference() {
        val payload = validPayload().copy(telecomPackages = emptyList())
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(payload))
        assertTrue(detail(payload).contains("telecom package 51 which does not exist"))
    }

    @Test
    fun duplicateAccountId_isMalformed() {
        val accounts = validPayload().accounts
        val payload = validPayload().copy(accounts = accounts + accounts[0])
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
        assertTrue(detail(payload).contains("duplicate account id 1"))
    }

    @Test
    fun nonPositiveIds_areMalformed() {
        val zero = validPayload().copy(
            transactions = validPayload().transactions.map { it.copy(id = 0L) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(zero))
        val negative = validPayload().copy(
            accounts = validPayload().accounts.map { it.copy(id = -5L) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(negative))
    }

    @Test
    fun invalidTransactionDirection_isMalformed() {
        val payload = validPayload().copy(
            transactions = validPayload().transactions.map { it.copy(direction = 2) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
        assertTrue(detail(payload).contains("direction 2"))
    }

    @Test
    fun negativeAmount_isMalformed() {
        val payload = validPayload().copy(
            transactions = validPayload().transactions.map { it.copy(amountMinor = -1L) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
        assertTrue(detail(payload).contains("must not be negative"))
    }

    @Test
    fun invalidAccountType_isMalformed() {
        val payload = validPayload().copy(
            accounts = validPayload().accounts.map { it.copy(type = 9) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidCategoryDirection_isMalformed() {
        val payload = validPayload().copy(
            categories = validPayload().categories.map { it.copy(direction = 3) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidWorkStatus_isMalformed() {
        val payload = validPayload().copy(
            workItems = validPayload().workItems.map { it.copy(status = 4) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidSimStatus_isMalformed() {
        val payload = validPayload().copy(
            telecomSims = validPayload().telecomSims.map { it.copy(status = 2) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidBillingPeriod_isMalformed() {
        val payload = validPayload().copy(
            telecomPackages = validPayload().telecomPackages.map { it.copy(period = 4) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidOpportunityTypeAndStatus_areMalformed() {
        val badType = validPayload().copy(
            opportunities = validPayload().opportunities.map { it.copy(type = 7) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(badType))
        val badStatus = validPayload().copy(
            opportunities = validPayload().opportunities.map { it.copy(status = 7) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(badStatus))
    }

    @Test
    fun negativeOpportunityAmount_isMalformed() {
        val payload = validPayload().copy(
            opportunities = validPayload().opportunities.map { it.copy(expectedAmountMinor = -1L) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
    }

    @Test
    fun invalidMonthKeys_areMalformed() {
        for (bad in listOf("2026-13", "2026-9", "abc", "26-09", "2026/09")) {
            val payload = validPayload().copy(
                budgets = validPayload().budgets.map { it.copy(monthKey = bad) }
            )
            assertEquals("monthKey $bad", BackupErrorCode.MALFORMED_RECORDS, code(payload))
        }
    }

    @Test
    fun duplicateBudgetSlot_isMalformed() {
        val payload = validPayload().copy(
            budgets = validPayload().budgets.map { it.copy(monthKey = "2026-10", categoryId = 1L) }
        )
        assertEquals(BackupErrorCode.MALFORMED_RECORDS, code(payload))
        assertTrue(detail(payload).contains("duplicates the budget for month 2026-10"))
    }

    @Test
    fun sameMonthDifferentCategory_isAllowed() {
        val outcome = BackupValidator.validate(validPayload())
        assertTrue(outcome is ValidationOutcome.Valid)
    }

    @Test
    fun validBackupPassesThenInvalidCopyFailsWithoutPartialRelaxation() {
        assertTrue(BackupValidator.validate(validPayload()) is ValidationOutcome.Valid)
        val tampered = validPayload().let { payload ->
            payload.copy(
                transactions = payload.transactions.mapIndexed { index, transaction ->
                    if (index == 0) transaction.copy(accountId = 4242L) else transaction
                }
            )
        }
        assertEquals(BackupErrorCode.MISSING_REFERENCE, code(tampered))
    }
}
