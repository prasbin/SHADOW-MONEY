package com.prasbin.shadowmoney.presentation.screen.assistant

import androidx.room.Room
import com.prasbin.shadowmoney.assistant.ASSISTANT_HELP_TEXT
import com.prasbin.shadowmoney.assistant.ASSISTANT_READ_ERROR_TEXT
import com.prasbin.shadowmoney.assistant.IntentClassifier
import com.prasbin.shadowmoney.data.AssistantRepository
import com.prasbin.shadowmoney.data.BudgetRepository
import com.prasbin.shadowmoney.data.DashboardRepository
import com.prasbin.shadowmoney.data.GoalRepository
import com.prasbin.shadowmoney.data.IntelligenceRepository
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.OpportunityRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.TelecomRepository
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: AssistantRepository
    private lateinit var viewModel: AssistantViewModel

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        database = Room.inMemoryDatabaseBuilder(
            app.applicationContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = buildRepository(database)
        runBlocking {
            val accountId = database.accountDao().insert(
                Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 100_000L)
            )
            database.transactionDao().insert(
                Transaction(
                    accountId = accountId,
                    amountMinor = 10_000L,
                    direction = TRANSACTION_DIRECTION_INCOME,
                    transactionTimestamp = now - 3_600_000L,
                    note = "Payment"
                )
            )
            database.goalDao().insert(
                Goal(name = "Laptop", targetAmountMinor = 50_000L, accountId = accountId)
            )
        }
        viewModel = AssistantViewModel(repository)
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun buildRepository(db: ShadowMoneyDatabase): AssistantRepository {
        val txDao = db.transactionDao()
        val catDao = db.categoryDao()
        val accDao = db.accountDao()
        val gDao = db.goalDao()
        return AssistantRepository(
            dashboardRepository = DashboardRepository(accDao, catDao, txDao, gDao, db.openHelper),
            budgetRepository = BudgetRepository(db.budgetDao(), txDao, catDao, db.openHelper),
            goalRepository = GoalRepository(gDao, accDao, txDao),
            workRepository = WorkRepository(db.workItemDao(), txDao, db.openHelper, clock = { now }),
            telecomRepository = TelecomRepository(db.telecomDao(), clock = { now }),
            opportunityRepository = OpportunityRepository(db.opportunityDao(), clock = { now }),
            intelligenceRepository = IntelligenceRepository(txDao, catDao),
            transactionDao = txDao,
            workItemDao = db.workItemDao(),
            opportunityDao = db.opportunityDao(),
            telecomDao = db.telecomDao(),
            openHelper = db.openHelper,
            clock = { now }
        )
    }

    private suspend fun awaitExchange(
        vm: AssistantViewModel,
        expectedSize: Int
    ): AssistantChatMessage = withTimeout(10_000) {
        vm.state.first { !it.isBusy && it.messages.size == expectedSize }.messages.last()
    }

    private suspend fun replyText(vm: AssistantViewModel, expectedSize: Int): String =
        awaitExchange(vm, expectedSize).sections.joinToString("\n") { it.text }

    private fun submitAndWait(vm: AssistantViewModel, input: String, expectedSize: Int): String =
        runBlocking {
            vm.submit(input)
            replyText(vm, expectedSize)
        }

    @Test
    fun initialState_isEmptySession() {
        val state = viewModel.state.value
        assertTrue(state.messages.isEmpty())
        assertFalse(state.isBusy)
    }

    @Test
    fun submit_help_returnsHelpGuidance() {
        val text = submitAndWait(viewModel, "help", 2)
        assertEquals(ASSISTANT_HELP_TEXT, text)
    }

    @Test
    fun submit_recordsUserQuestionThenAssistantReply() {
        runBlocking {
            viewModel.submit("what is my balance")
            val state = withTimeout(10_000) {
                viewModel.state.first { !it.isBusy && it.messages.size == 2 }
            }
            assertEquals(2, state.messages.size)
            assertTrue(state.messages[0].isUser)
            assertEquals("what is my balance", state.messages[0].text)
            assertFalse(state.messages[1].isUser)
            assertTrue(state.messages[1].sections.isNotEmpty())
        }
    }

    @Test
    fun submit_balance_answersWithRecordedTotalAndSource() {
        val text = submitAndWait(viewModel, "what is my balance", 2)
        assertTrue(text.contains(Money.formatNpr(110_000L)))
        val reply = viewModel.state.value.messages[1]
        assertEquals("Local financial records.", reply.source)
    }

    @Test
    fun submit_blankInput_isIgnored() {
        viewModel.submit("")
        viewModel.submit("   ")
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun submit_secretTargetQuestion_refusesWithoutData() {
        val question = IntentClassifier.classify("what is the secret target")
        val text = submitAndWait(viewModel, "what is the secret target", 2)
        assertEquals(IntentClassifier.SECRET_TARGET_REFUSAL_TEXT, text)
        assertEquals(
            com.prasbin.shadowmoney.assistant.AssistantIntent.SECRET_TARGET_REFUSAL,
            question.intent
        )
    }

    @Test
    fun submit_afterDatabaseClosed_returnsReadErrorAndRecovers() {
        database.close()
        val text = submitAndWait(viewModel, "what is my balance", 2)
        assertEquals(ASSISTANT_READ_ERROR_TEXT, text)
        assertFalse(viewModel.state.value.isBusy)

        val help = submitAndWait(viewModel, "help", 4)
        assertEquals(ASSISTANT_HELP_TEXT, help)
    }

    @Test
    fun clearHistory_resetsSession() {
        submitAndWait(viewModel, "help", 2)
        viewModel.clearHistory()
        assertTrue(viewModel.state.value.messages.isEmpty())
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun history_isBoundedToMaxMessages() {
        runBlocking {
            val max = AssistantViewModel.MAX_MESSAGES
            val exchanges = max / 2 + 1
            for (i in 1..exchanges) {
                viewModel.submit("help")
                val expected = minOf(i * 2, max)
                withTimeout(10_000) {
                    viewModel.state.first { !it.isBusy && it.messages.size == expected }
                }
            }
            val state = viewModel.state.value
            assertEquals(max, state.messages.size)
            assertTrue(state.messages[0].isUser)
            assertFalse(state.messages.last().isUser)
        }
    }

    @Test
    fun repeatedIdenticalQuestions_produceIdenticalReplies() {
        val first = submitAndWait(viewModel, "how are my goals", 2)
        val second = submitAndWait(viewModel, "how are my goals", 4)
        assertEquals(first, second)
    }

    @Test
    fun askingQuestions_neverMutatesDatabase() = runBlocking {
        val accountsBefore = database.accountDao().getAll().first()
        val transactionsBefore = database.transactionDao().getAll().first()
        val goalsBefore = database.goalDao().observeAll().first()

        for ((index, input) in listOf(
            "what is my balance",
            "how much income did i make this month",
            "what did i spend this month",
            "how are my goals"
        ).withIndex()) {
            viewModel.submit(input)
            withTimeout(10_000) {
                viewModel.state.first { !it.isBusy && it.messages.size == (index + 1) * 2 }
            }
        }

        assertEquals(accountsBefore, database.accountDao().getAll().first())
        assertEquals(transactionsBefore, database.transactionDao().getAll().first())
        assertEquals(goalsBefore, database.goalDao().observeAll().first())
    }
}
