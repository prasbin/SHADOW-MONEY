package com.prasbin.shadowmoney.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecretTargetPrivacyTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var database: ShadowMoneyDatabase
    private lateinit var goalDao: GoalDao
    private lateinit var accountDao: AccountDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var goalRepository: GoalRepository
    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var workRepository: WorkRepository
    private lateinit var secretStore: SecretTargetStore

    private val secretValue = 7_777_777L

    @Before
    fun createDb() {
        val tempDir = createTempDir("secret_target_privacy").absoluteFile
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Default)) {
            File(tempDir, "secret_target_privacy.preferences_pb")
        }
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        goalDao = database.goalDao()
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
        goalRepository = GoalRepository(goalDao, accountDao, transactionDao)
        dashboardRepository = DashboardRepository(accountDao, categoryDao(), transactionDao, goalDao, database.openHelper)
        budgetRepository = BudgetRepository(database.budgetDao(), transactionDao, categoryDao(), database.openHelper)
        workRepository = WorkRepository(database.workItemDao(), transactionDao, database.openHelper)
        secretStore = SecretTargetStore(dataStore)
    }

    private fun categoryDao() = database.categoryDao()

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun secretTarget_notStoredInRoom() = runBlocking {
        secretStore.setTarget(secretValue)

        val goalRows = goalDao.observeAll().first()
        val transactionRows = transactionDao.getAll().first()
        val accountRows = accountDao.getAll().first()

        assertTrue(goalRows.none { it.targetAmountMinor == secretValue })
        assertTrue(transactionRows.none { it.amountMinor == secretValue })
        assertTrue(accountRows.none { it.openingBalanceMinor == secretValue })
    }

    @Test
    fun secretTarget_notExposedThroughGoalRepository() = runBlocking {
        val accountId = accountDao.insert(Account(name = "A", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 1_000L))
        goalRepository.create(com.prasbin.shadowmoney.data.model.Goal(name = "G", targetAmountMinor = 5_000L, accountId = accountId))

        secretStore.setTarget(secretValue)

        val views = goalRepository.loadGoalViews(goalDao.observeAll().first())
        assertTrue(views.none { it.currentBalanceMinor == secretValue || it.goal.targetAmountMinor == secretValue })
    }

    @Test
    fun secretTarget_doesNotAffectDashboardCalculations() = runBlocking {
        val accountId = accountDao.insert(Account(name = "A", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10_000L))
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 5_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                note = "Salary"
            )
        )

        val before = dashboardRepository.loadDashboardData(
            DashboardRepository(
                accountDao, categoryDao(), transactionDao, goalDao, database.openHelper
            ).observeSnapshot().first()
        )

        secretStore.setTarget(secretValue)

        val after = dashboardRepository.loadDashboardData(
            DashboardRepository(
                accountDao, categoryDao(), transactionDao, goalDao, database.openHelper
            ).observeSnapshot().first()
        )

        assertEquals(before.totalBalanceMinor, after.totalBalanceMinor)
        assertEquals(before.totalIncomeMinor, after.totalIncomeMinor)
        assertEquals(before.totalOutflowMinor, after.totalOutflowMinor)
    }

    @Test
    fun secretTarget_doesNotAffectBudgetCalculations() = runBlocking {
        val accountId = accountDao.insert(Account(name = "A", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))
        val categoryId = categoryDao().insert(
            com.prasbin.shadowmoney.data.model.Category(
                name = "Food",
                direction = com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 3_000L,
                direction = com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW,
                note = "Lunch"
            )
        )
        budgetRepository.createOverallBudget("2026-09", 50_000L)

        val before = budgetRepository.loadMonthData("2026-09")

        secretStore.setTarget(secretValue)

        val after = budgetRepository.loadMonthData("2026-09")
        assertEquals(before.overall?.spentMinor, after.overall?.spentMinor)
        assertEquals(before.overall?.remainingMinor, after.overall?.remainingMinor)
    }

    @Test
    fun secretTarget_doesNotAffectWorkCalculations() = runBlocking {
        val accountId = accountDao.insert(Account(name = "A", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L))
        val workId = workRepository.create("Project", "", 100_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 20_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                note = "Payment"
            )
        )
        workRepository.linkTransaction(txId, workId)

        val before = workRepository.loadViews(listOf(database.workItemDao().getById(workId)!!))

        secretStore.setTarget(secretValue)

        val after = workRepository.loadViews(listOf(database.workItemDao().getById(workId)!!))
        assertEquals(before.first().receivedMinor, after.first().receivedMinor)
    }

    private fun findSourceFile(name: String): java.io.File {
        var dir = java.io.File(System.getProperty("user.dir"))
        repeat(4) {
            val candidate = java.io.File(dir, "app/src/main/java/com/prasbin/shadowmoney/data/$name")
            if (candidate.exists()) return candidate
            val alt = java.io.File(dir, "src/main/java/com/prasbin/shadowmoney/data/$name")
            if (alt.exists()) return alt
            dir = dir.parentFile ?: return candidate
        }
        return java.io.File(name)
    }

    @Test
    fun secretTarget_notLogged_noLogCallsInStore() {
        val source = findSourceFile("SecretTargetStore.kt").readText()
        assertFalse(source.contains("Log."))
        assertFalse(source.contains("println"))
        assertFalse(source.contains("print("))
    }

    @Test
    fun secretTarget_notInRoomSchema() {
        val source = findSourceFile("ShadowMoneyDatabase.kt").readText()
        assertFalse(source.contains("secret_target"))
        assertFalse(source.contains("SecretTarget"))
    }
}
