package com.prasbin.shadowmoney.dashboard

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.prasbin.shadowmoney.data.IntelligenceRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.intelligence.MILLIS_PER_DAY
import com.prasbin.shadowmoney.presentation.screen.dashboard.IntelligenceUiState
import com.prasbin.shadowmoney.presentation.screen.dashboard.IntelligenceViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntelligenceRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var transactionDao: com.prasbin.shadowmoney.data.TransactionDao
    private lateinit var categoryDao: com.prasbin.shadowmoney.data.CategoryDao

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        transactionDao = database.transactionDao()
        categoryDao = database.categoryDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun tx(
        id: Long,
        accountId: Long,
        categoryId: Long?,
        amount: Long,
        timestamp: Long,
        note: String = "Spending"
    ) = Transaction(
        id = id,
        accountId = accountId,
        categoryId = categoryId,
        amountMinor = amount,
        direction = TRANSACTION_DIRECTION_OUTFLOW,
        transactionTimestamp = timestamp,
        note = note
    )

    private fun seedAccount(): Long = runBlocking {
        database.accountDao().insert(
            com.prasbin.shadowmoney.data.model.Account(
                name = "Test Account",
                type = com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET,
                openingBalanceMinor = 0L
            )
        )
    }

    private fun seedCategory(): Long = runBlocking {
        database.categoryDao().insert(
            com.prasbin.shadowmoney.data.model.Category(
                name = "Test Category",
                direction = com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
            )
        )
    }

    @Test
    fun windowQueryBounds_onlyInRangeTransactionsLoaded() = runBlocking {
        val accountId = seedAccount()
        val categoryId = seedCategory()
        transactionDao.insertAll(
            listOf(
                tx(1, accountId, categoryId, 999_999L, now - 500 * MILLIS_PER_DAY),
                tx(2, accountId, categoryId, 1_000L, now - 100 * MILLIS_PER_DAY),
                tx(3, accountId, categoryId, 2_000L, now)
            )
        )
        val repository = IntelligenceRepository(transactionDao, categoryDao)
        val report = repository.loadReport(now)
        assertEquals(3_000L, report.period.outflowMinor)
        assertEquals(2, report.period.transactionCount)
    }

    @Test
    fun engineWired_recurringPatternProducesAnalysis() = runBlocking {
        val accountId = seedAccount()
        val categoryId = seedCategory()
        transactionDao.insertAll(
            listOf(
                tx(1, accountId, categoryId, 15_000L, now - 90 * MILLIS_PER_DAY, note = "Monthly rent"),
                tx(2, accountId, categoryId, 15_000L, now - 60 * MILLIS_PER_DAY, note = "Monthly rent"),
                tx(3, accountId, categoryId, 15_000L, now - 30 * MILLIS_PER_DAY, note = "Monthly rent")
            )
        )
        val repository = IntelligenceRepository(transactionDao, categoryDao)
        val report = repository.loadReport(now)
        val recurring = report.insights.filter { it.kind.name == "ANALYSIS" }
        assertTrue(recurring.isNotEmpty())
        assertTrue(recurring.any { it.title.contains("monthly rent", ignoreCase = true) })
    }

    @Test
    fun emptyDatabase_reportLoadedWithInsufficientFlag() = runBlocking {
        val repository = IntelligenceRepository(transactionDao, categoryDao)
        val report = repository.loadReport(now)
        assertFalse(report.sufficientData)
        assertEquals(0, report.period.transactionCount)
    }

    @Test
    fun reactiveUpdate_insertChangesReport() = runBlocking {
        val accountId = seedAccount()
        val categoryId = seedCategory()
        transactionDao.insert(tx(1, accountId, categoryId, 10_000L, now - 10 * MILLIS_PER_DAY))
        val repository = IntelligenceRepository(transactionDao, categoryDao)
        val viewModel = IntelligenceViewModel(repository, clock = { now })

        val first = withTimeout(10_000) {
            viewModel.uiState.first { it is IntelligenceUiState.Content }
        } as IntelligenceUiState.Content
        assertEquals(1, first.report.period.transactionCount)

        transactionDao.insert(tx(2, accountId, categoryId, 5_000L, now - 5 * MILLIS_PER_DAY))

        val second = withTimeout(10_000) {
            viewModel.uiState.first {
                it is IntelligenceUiState.Content && it.report.period.transactionCount == 2
            }
        } as IntelligenceUiState.Content
        assertEquals(2, second.report.period.transactionCount)
        assertEquals(15_000L, second.report.period.outflowMinor)
    }
}
