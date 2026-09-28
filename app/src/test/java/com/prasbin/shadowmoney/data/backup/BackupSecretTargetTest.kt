package com.prasbin.shadowmoney.data.backup

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.prasbin.shadowmoney.data.SecretTargetStore
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Secret Target lives only in DataStore and must never appear in backup
 * JSON, in the checksum input, in restore output, or anywhere in the
 * backup code path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupSecretTargetTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var dataStore: androidx.datastore.core.DataStore<
        androidx.datastore.preferences.core.Preferences>
    private lateinit var secretTargetStore: SecretTargetStore
    private lateinit var repository: BackupRepository

    private val now = BackupTestData.NOW
    private val secretValue = 7_654_321_098_765L

    @Before
    fun setUp() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        val tempDir = createTempDir("backup_secret_target_test").absoluteFile
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Default)) {
            File(tempDir, "secret_target_test.preferences_pb")
        }
        secretTargetStore = SecretTargetStore(dataStore)
        repository = BackupRepository(database, FakeBackupFileIo(), clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    private suspend fun seedSmallLedger() {
        val accountId = database.accountDao().insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 100_000L)
        )
        val categoryId = database.categoryDao().insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        database.transactionDao().insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 55_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = now,
                note = "Groceries"
            )
        )
    }

    private fun backupJson(): String {
        val outcome = runBlocking { repository.createBackupJson() }
        assertTrue(outcome is BackupCreateOutcome.Success)
        return (outcome as BackupCreateOutcome.Success).json
    }

    @Test
    fun backupJson_neverContainsTheSecretTargetValue() = runBlocking {
        seedSmallLedger()
        secretTargetStore.setTarget(secretValue)

        val json = backupJson()
        assertFalse(json.contains(secretValue.toString()))
        assertFalse(json.contains("secret", ignoreCase = true))
        assertFalse(json.contains("secret_target", ignoreCase = true))
    }

    @Test
    fun checksumInput_isIndependentOfTheSecretTarget() = runBlocking {
        seedSmallLedger()
        val payloadBefore = readPayload(backupJson())
        val checksumBefore = BackupSerializer.checksumOf(payloadBefore)

        secretTargetStore.setTarget(secretValue)
        val payloadAfter = readPayload(backupJson())
        val checksumAfter = BackupSerializer.checksumOf(payloadAfter)

        assertEquals(checksumBefore, checksumAfter)
        assertEquals(payloadBefore, payloadAfter)
    }

    private fun readPayload(json: String): BackupPayload {
        val outcome = BackupSerializer.read(json, APP_SCHEMA_VERSION)
        assertTrue(outcome is EnvelopeReadOutcome.Ok)
        return (outcome as EnvelopeReadOutcome.Ok).envelope.payload
    }

    @Test
    fun restore_doesNotTouchTheSecretTarget() = runBlocking {
        seedSmallLedger()
        secretTargetStore.setTarget(secretValue)
        val json = backupJson()

        wipeAll()
        val result = repository.restoreFromText(json)
        assertTrue(result is RestoreWriteOutcome.Success)

        assertEquals(secretValue, secretTargetStore.getTarget())
    }

    @Test
    fun restoringAnEmptyBackup_doesNotTouchTheSecretTarget() = runBlocking {
        secretTargetStore.setTarget(secretValue)
        wipeAll()
        val result = repository.restoreFromText(BackupTestData.write(BackupTestData.emptyPayload()))
        assertTrue(result is RestoreWriteOutcome.Success)
        assertEquals(secretValue, secretTargetStore.getTarget())
    }

    @Test
    fun backupPayloadModel_hasNoFieldForTheSecretTarget() {
        val fields = BackupPayload::class.java.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") }
        assertTrue(fields.none { it.contains("secret", ignoreCase = true) })
        assertTrue(fields.none { it.contains("target", ignoreCase = true) })
        val transactionFields = TransactionBackup::class.java.declaredFields
            .map { it.name }
            .filter { !it.startsWith("$") }
        assertEquals(
            listOf(
                "accountId", "amountMinor", "categoryId", "createdTimestamp", "direction",
                "externalRef", "id", "note", "source", "transactionTimestamp", "workItemId"
            ).sorted(),
            transactionFields.sorted()
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
}
