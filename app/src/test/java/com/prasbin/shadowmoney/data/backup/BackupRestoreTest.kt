package com.prasbin.shadowmoney.data.backup

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_BANK
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.BILLING_PERIOD_QUARTERLY
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_OTHER
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PROJECT
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.SIM_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.WorkItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupRestoreTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var fileIo: FakeBackupFileIo
    private lateinit var repository: BackupRepository

    private val now = BackupTestData.NOW

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        fileIo = FakeBackupFileIo()
        repository = BackupRepository(database, fileIo, clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    private suspend fun seedDatabase() {
        val accountDao = database.accountDao()
        val categoryDao = database.categoryDao()
        val transactionDao = database.transactionDao()
        val goalDao = database.goalDao()
        val budgetDao = database.budgetDao()
        val workItemDao = database.workItemDao()
        val telecomDao = database.telecomDao()
        val opportunityDao = database.opportunityDao()

        val wallet = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 100_000L)
        )
        val bank = accountDao.insert(
            Account(
                name = "Bank",
                type = ACCOUNT_TYPE_BANK,
                openingBalanceMinor = -5_000L,
                isActive = false,
                createdTimestamp = now - 1_000L
            )
        )
        val food = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW, isSystem = true)
        )
        val salary = categoryDao.insert(
            Category(name = "Salary", direction = CATEGORY_DIRECTION_INCOME)
        )
        val work = workItemDao.insert(
            WorkItem(
                title = "Logo",
                description = "Client \"ACME\" work",
                status = WORK_STATUS_ARCHIVED,
                expectedAmountMinor = 500_000L,
                client = "ACME",
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
        val oldSim = telecomDao.insertSim(
            TelecomSim(
                label = "Old SIM",
                carrier = "Ncell",
                phoneNumber = "+977-9800000000",
                status = SIM_STATUS_ARCHIVED,
                notes = "archived"
            )
        )
        val newSim = telecomDao.insertSim(
            TelecomSim(label = "New SIM", carrier = "Namaste", phoneNumber = "+977-9811111111")
        )
        val pkg = telecomDao.insertPackage(
            TelecomPackage(
                name = "Quarterly 4G",
                carrier = "Ncell",
                category = "Data",
                priceMinor = 999_000L,
                period = BILLING_PERIOD_QUARTERLY,
                isActive = false
            )
        )
        telecomDao.insertSubscription(
            TelecomSubscription(
                simId = oldSim,
                packageId = pkg,
                startTimestamp = now - 10_000L,
                renewalTimestamp = now + 10_000L,
                monthlyCostMinor = 12_345L,
                isActive = false
            )
        )
        opportunityDao.insert(
            Opportunity(
                title = "Portal rework",
                type = OPPORTUNITY_TYPE_PROJECT,
                source = "Upwork",
                sourceUrl = "https://example.invalid/job/1",
                expectedAmountMinor = 1_500_000L,
                status = OPPORTUNITY_STATUS_LOST,
                client = "ACME",
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
        opportunityDao.insert(
            Opportunity(
                title = "Open application",
                type = OPPORTUNITY_TYPE_OTHER,
                expectedAmountMinor = null
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = wallet,
                categoryId = food,
                workItemId = work,
                amountMinor = 12_345L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = now - 5_000L,
                note = "Café ☕ lunch",
                createdTimestamp = now,
                source = "IMPORT_FILE",
                externalRef = "TX-42"
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = bank,
                categoryId = null,
                workItemId = null,
                amountMinor = 9_007_199_254_740_993L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = now,
                note = "",
                createdTimestamp = now,
                source = "manual",
                externalRef = null
            )
        )
        goalDao.insert(
            Goal(
                name = "Laptop",
                targetAmountMinor = 250_000L,
                accountId = wallet,
                isActive = false,
                isCompleted = true,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
        goalDao.insert(
            Goal(
                name = "Emergency fund",
                targetAmountMinor = 0L,
                accountId = null,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
        budgetDao.insert(
            Budget(amountMinor = 30_000L, monthKey = "2026-09", categoryId = food)
        )
        budgetDao.insert(
            Budget(amountMinor = 10_000L, monthKey = "2026-09", categoryId = null)
        )
    }

    private suspend fun snapshot(): Map<String, List<Any>> {
        val dao = database.backupDao()
        return mapOf(
            "accounts" to dao.getAllAccounts().sortedBy { it.id },
            "categories" to dao.getAllCategories().sortedBy { it.id },
            "transactions" to dao.getAllTransactions().sortedBy { it.id },
            "goals" to dao.getAllGoals().sortedBy { it.id },
            "budgets" to dao.getAllBudgets().sortedBy { it.id },
            "workItems" to dao.getAllWorkItems().sortedBy { it.id },
            "sims" to dao.getAllSims().sortedBy { it.id },
            "packages" to dao.getAllPackages().sortedBy { it.id },
            "subscriptions" to dao.getAllSubscriptions().sortedBy { it.id },
            "opportunities" to dao.getAllOpportunities().sortedBy { it.id }
        )
    }

    private suspend fun wipeAll() {
        val dao = database.backupDao()
        dao.deleteAllTransactions()
        dao.deleteAllGoals()
        dao.deleteAllBudgets()
        dao.deleteAllSubscriptions()
        dao.deleteAllOpportunities()
        dao.deleteAllWorkItems()
        dao.deleteAllPackages()
        dao.deleteAllSims()
        dao.deleteAllCategories()
        dao.deleteAllAccounts()
    }

    private suspend fun createBackupText(): String {
        val outcome = repository.createBackupJson()
        assertTrue("expected Success but was $outcome", outcome is BackupCreateOutcome.Success)
        return (outcome as BackupCreateOutcome.Success).json
    }

    private suspend fun tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        database.openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type='table'", emptyArray())
            .use { cursor ->
                while (cursor.moveToNext()) names.add(cursor.getString(0))
            }
        return names
    }

    @Test
    fun createBackupJson_reportsExactCountsChecksumAndIsDeterministic() = runBlocking {
        seedDatabase()
        val first = repository.createBackupJson()
        assertTrue(first is BackupCreateOutcome.Success)
        first as BackupCreateOutcome.Success
        assertEquals(2, first.counts.accounts)
        assertEquals(2, first.counts.categories)
        assertEquals(2, first.counts.transactions)
        assertEquals(2, first.counts.goals)
        assertEquals(2, first.counts.budgets)
        assertEquals(1, first.counts.workItems)
        assertEquals(2, first.counts.telecomSims)
        assertEquals(1, first.counts.telecomPackages)
        assertEquals(1, first.counts.telecomSubscriptions)
        assertEquals(2, first.counts.opportunities)
        assertEquals(17, first.counts.total)
        assertEquals(64, first.checksum.length)
        assertEquals(now, first.createdAtEpochMillis)
        assertEquals(first.json, createBackupText())
    }

    @Test
    fun exportAndImportThroughFileIo_replacesDivergedDatabaseExactly() = runBlocking {
        seedDatabase()
        val original = snapshot()
        val json = createBackupText()
        assertEquals(
            FileWriteResult.Success,
            repository.writeBackupToUri("doc://backup-1", json)
        )

        wipeAll()
        val intruder = database.accountDao().insert(
            Account(name = "Intruder account", type = ACCOUNT_TYPE_WALLET)
        )
        database.transactionDao().insert(
            Transaction(accountId = intruder, amountMinor = 1L, note = "noise")
        )

        val preview = repository.prepareRestoreFromUri("doc://backup-1")
        assertTrue("expected Ready but was $preview", preview is RestorePreviewOutcome.Ready)
        val result = repository.restore((preview as RestorePreviewOutcome.Ready).candidate)
        assertTrue("expected Success but was $result", result is RestoreWriteOutcome.Success)

        assertEquals(original, snapshot())
        assertTrue(
            database.backupDao().getAllAccounts().none { it.name == "Intruder account" }
        )
    }

    @Test
    fun restore_isFullReplacement_notAMerge() = runBlocking {
        seedDatabase()
        val json = createBackupText()
        val wallet = database.backupDao().getAllAccounts().first { it.name == "Wallet" }
        database.accountDao().insert(
            Account(name = "Extra account", type = ACCOUNT_TYPE_WALLET, createdTimestamp = now)
        )
        database.accountDao().insert(
            Account(name = "Wallet renamed", type = ACCOUNT_TYPE_WALLET, createdTimestamp = now)
        )
        val before = repository.loadCounts()
        assertEquals(4, before.accounts)

        val result = repository.restoreFromText(json)
        assertTrue(result is RestoreWriteOutcome.Success)

        val accounts = database.backupDao().getAllAccounts()
        assertEquals(2, accounts.size)
        assertEquals(wallet.id, accounts.first { it.name == "Wallet" }.id)
        assertTrue(accounts.none { it.name == "Extra account" })
        assertTrue(accounts.none { it.name == "Wallet renamed" })
    }

    @Test
    fun restore_preservesIdsRelationshipsAndExactFieldValues() = runBlocking {
        seedDatabase()
        val original = snapshot()
        val json = createBackupText()
        wipeAll()

        val result = repository.restoreFromText(json)
        assertTrue(result is RestoreWriteOutcome.Success)
        assertEquals(original, snapshot())

        val tx = database.backupDao().getAllTransactions().first { it.externalRef == "TX-42" }
        assertEquals("Café ☕ lunch", tx.note)
        assertEquals("IMPORT_FILE", tx.source)
        assertEquals(12_345L, tx.amountMinor)
        val big = database.backupDao().getAllTransactions().first { it.externalRef == null }
        assertEquals(9_007_199_254_740_993L, big.amountMinor)
        assertEquals(
            1,
            database.backupDao().getAllSubscriptions().count {
                it.simId != 0L && it.packageId != 0L && !it.isActive
            }
        )
        assertEquals(
            WORK_STATUS_ARCHIVED,
            database.backupDao().getAllWorkItems().single().status
        )
        assertEquals(
            SIM_STATUS_ARCHIVED,
            database.backupDao().getAllSims().first { it.label == "Old SIM" }.status
        )
        assertEquals(
            "2026-09",
            database.backupDao().getAllBudgets().first { it.categoryId != null }.monthKey
        )
    }

    @Test
    fun restore_emptyBackup_explicitlyEmptiesDatabase() = runBlocking {
        seedDatabase()
        val emptyJson = BackupTestData.write(BackupTestData.emptyPayload())

        val preview = repository.prepareRestoreText(emptyJson)
        assertTrue(preview is RestorePreviewOutcome.Ready)
        assertEquals(0, (preview as RestorePreviewOutcome.Ready).candidate.counts.total)

        val result = repository.restore(preview.candidate)
        assertTrue(result is RestoreWriteOutcome.Success)
        assertEquals(0, repository.loadCounts().total)
        for ((_, rows) in snapshot()) {
            assertTrue("table must be empty", rows.isEmpty())
        }
    }

    @Test
    fun preview_makesNoDatabaseWrites() = runBlocking {
        seedDatabase()
        val before = snapshot()
        val json = createBackupText()

        val preview = repository.prepareRestoreText(json)
        assertTrue(preview is RestorePreviewOutcome.Ready)
        assertEquals(before, snapshot())

        wipeAll()
        val rejected = repository.prepareRestoreText(json)
        assertTrue(rejected is RestorePreviewOutcome.Ready)
        assertEquals(0, repository.loadCounts().total)
    }

    @Test
    fun tamperedBackup_isRejectedAndNothingIsRestored() = runBlocking {
        seedDatabase()
        val before = snapshot()
        val json = createBackupText()
            .replace("\"amountMinor\":12345", "\"amountMinor\":99999")

        val preview = repository.prepareRestoreText(json)
        assertTrue(preview is RestorePreviewOutcome.Rejected)
        assertEquals(
            BackupErrorCode.CHECKSUM_MISMATCH,
            (preview as RestorePreviewOutcome.Rejected).error.code
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun danglingReferenceWithValidChecksum_isRejectedAndNothingIsRestored() = runBlocking {
        seedDatabase()
        val before = snapshot()
        val payload = readPayload(createBackupText()).copy(
            transactions = readPayload(createBackupText()).transactions.map {
                it.copy(accountId = 4242L)
            }
        )
        val json = BackupSerializer.write(BackupBuilder.build(payload, now))

        val preview = repository.prepareRestoreText(json)
        assertTrue(preview is RestorePreviewOutcome.Rejected)
        assertEquals(
            BackupErrorCode.MISSING_REFERENCE,
            (preview as RestorePreviewOutcome.Rejected).error.code
        )
        assertEquals(before, snapshot())
    }

    private fun readPayload(json: String): BackupPayload {
        val outcome = BackupSerializer.read(json, APP_SCHEMA_VERSION)
        assertTrue(outcome is EnvelopeReadOutcome.Ok)
        return (outcome as EnvelopeReadOutcome.Ok).envelope.payload
    }

    @Test
    fun incompatibleSchema_isRejectedAndNothingIsRestored() = runBlocking {
        seedDatabase()
        val before = snapshot()
        val json = BackupTestData.write(readPayload(createBackupText()), schemaVersion = 6)

        val preview = repository.prepareRestoreText(json)
        assertEquals(
            BackupErrorCode.INCOMPATIBLE_SCHEMA,
            (preview as RestorePreviewOutcome.Rejected).error.code
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun blankAndOversizedDocuments_areRejected() = runBlocking {
        seedDatabase()
        val before = snapshot()

        val blank = repository.prepareRestoreText("")
        assertEquals(
            BackupErrorCode.NO_DATA,
            (blank as RestorePreviewOutcome.Rejected).error.code
        )

        val oversized = repository.prepareRestoreText("x".repeat(MAX_BACKUP_BYTES + 1))
        assertEquals(
            BackupErrorCode.FILE_TOO_LARGE,
            (oversized as RestorePreviewOutcome.Rejected).error.code
        )
        assertEquals(before, snapshot())
    }

    @Test
    fun failedRestoreTransaction_rollsBackToExactPriorState() = runBlocking {
        seedDatabase()
        val before = snapshot()
        val payload = readPayload(createBackupText())
        val poisoned = payload.copy(
            transactions = payload.transactions.mapIndexed { index, transaction ->
                if (index == 0) transaction.copy(accountId = 99_999L) else transaction
            }
        )

        try {
            runBlocking { RestoreEngine(database).restore(poisoned) }
            fail("expected the poisoned restore to fail")
        } catch (expected: Exception) {
            // FK violation inside the transaction
        }
        assertEquals(before, snapshot())
    }

    @Test
    fun restoreTwice_isIdempotent() = runBlocking {
        seedDatabase()
        val json = createBackupText()
        val afterFirst = snapshot()

        val result = repository.restoreFromText(json)
        assertTrue(result is RestoreWriteOutcome.Success)
        assertEquals(afterFirst, snapshot())
        assertEquals(afterFirst, snapshot())
    }

    @Test
    fun restore_addsNoTablesAndNoParallelLedger() = runBlocking {
        seedDatabase()
        val tablesBefore = tableNames()
        repository.restoreFromText(createBackupText())
        assertEquals(tablesBefore, tableNames())
    }

    @Test
    fun restoringEmptyBackupFromEmptyDatabase_isExplicitSuccess() = runBlocking {
        val json = BackupTestData.write(BackupTestData.emptyPayload())
        val result = repository.restoreFromText(json)
        assertTrue(result is RestoreWriteOutcome.Success)
        assertEquals(0, (result as RestoreWriteOutcome.Success).counts.total)
        assertEquals(0, repository.loadCounts().total)
    }

    @Test
    fun failedFileRead_isReportedAsStorageError() = runBlocking {
        fileIo.readResultOverride = FileReadResult.Failed("Could not open the selected file")
        val preview = repository.prepareRestoreFromUri("doc://missing")
        assertEquals(
            BackupErrorCode.STORAGE_ERROR,
            (preview as RestorePreviewOutcome.Rejected).error.code
        )

        fileIo.readResultOverride = FileReadResult.TooLarge(MAX_BACKUP_BYTES)
        val tooLarge = repository.prepareRestoreFromUri("doc://big")
        assertEquals(
            BackupErrorCode.FILE_TOO_LARGE,
            (tooLarge as RestorePreviewOutcome.Rejected).error.code
        )
    }

    @Test
    fun failedFileWrite_isReportedByCreateResultNotFalseSuccess() = runBlocking {
        seedDatabase()
        fileIo.writeResult = FileWriteResult.Failed("Could not write the backup file")
        assertEquals(
            FileWriteResult.Failed("Could not write the backup file"),
            repository.writeBackupToUri("doc://x", "{}")
        )
        assertTrue(fileIo.writtenUris.isEmpty())
    }
}
