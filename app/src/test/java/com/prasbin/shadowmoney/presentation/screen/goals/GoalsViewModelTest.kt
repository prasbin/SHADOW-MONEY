package com.prasbin.shadowmoney.presentation.screen.goals

import androidx.room.Room
import com.prasbin.shadowmoney.data.GoalRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
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
class GoalsViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: GoalRepository
    private lateinit var accountDao: com.prasbin.shadowmoney.data.AccountDao
    private lateinit var transactionDao: com.prasbin.shadowmoney.data.TransactionDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GoalRepository(
            goalDao = database.goalDao(),
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao()
        )
        accountDao = database.accountDao()
        transactionDao = database.transactionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(): GoalsViewModel = GoalsViewModel(repository)

    @Test
    fun emptyState_whenNoGoals() = runBlocking {
        val viewModel = viewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Empty }
        }
        assertTrue(state is GoalsUiState.Empty)
    }

    @Test
    fun createGoal_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }

        viewModel.create("Emergency fund", 100_000L, null, 0L)

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Content && it.goals.size == 1 }
        } as GoalsUiState.Content
        assertEquals("Emergency fund", state.goals.first().goal.name)
        assertEquals(100_000L, state.goals.first().goal.targetAmountMinor)
        assertEquals(0L, state.goals.first().currentBalanceMinor)
        assertNull(state.goals.first().accountName)
    }

    @Test
    fun createGoal_withLinkedAccount_progressDerived() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Savings", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 60_000L)
        )
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }

        viewModel.create("Goal", 100_000L, accountId, 0L)

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Content && it.goals.isNotEmpty() }
        } as GoalsUiState.Content
        assertEquals(60_000L, state.goals.first().currentBalanceMinor)
        assertEquals(40_000L, state.goals.first().remainingMinor)
        assertEquals(60, state.goals.first().percentUsed)
        assertEquals("Savings", state.goals.first().accountName)
    }

    @Test
    fun transactionChange_refreshesGoalProgress() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Savings", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 0L)
        )
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }
        viewModel.create("Goal", 100_000L, accountId, 0L)
        withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Content && it.goals.isNotEmpty() }
        }

        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 25_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                note = "Salary"
            )
        )

        val state = withTimeout(10_000) {
            viewModel.uiState.first {
                it is GoalsUiState.Content && it.goals.first().currentBalanceMinor == 25_000L
            }
        } as GoalsUiState.Content
        assertEquals(25_000L, state.goals.first().currentBalanceMinor)
        assertEquals(25, state.goals.first().percentUsed)
    }

    @Test
    fun archive_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }
        viewModel.create("Goal", 1_000L, null, 0L)
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Content && it.goals.isNotEmpty() }
        } as GoalsUiState.Content
        val goal = created.goals.first().goal

        viewModel.archive(goal.id)

        val archived = withTimeout(10_000) {
            viewModel.uiState.first {
                it is GoalsUiState.Content && it.goals.first().goal.isActive.not()
            }
        } as GoalsUiState.Content
        assertFalse(archived.goals.first().goal.isActive)
    }

    @Test
    fun delete_removesGoal() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }
        viewModel.create("Goal", 1_000L, null, 0L)
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Content && it.goals.isNotEmpty() }
        } as GoalsUiState.Content

        viewModel.delete(created.goals.first().goal)

        val after = withTimeout(10_000) {
            viewModel.uiState.first { it is GoalsUiState.Empty }
        }
        assertTrue(after is GoalsUiState.Empty)
    }

    @Test
    fun validationErrors_surfaceMessages() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is GoalsUiState.Empty } }

        viewModel.create("", 1_000L, null, 0L)
        val nameError = withTimeout(10_000) { viewModel.errorMessage.first { it != null } }
        assertTrue(nameError!!.contains("Name"))

        viewModel.create("Valid", 0L, null, 0L)
        val amountError = withTimeout(10_000) { viewModel.errorMessage.first { it != null && it.contains("Target") } }
        assertNotNull(amountError)
    }
}
