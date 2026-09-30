package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.assistant.AssistantEngine
import com.prasbin.shadowmoney.assistant.AssistantPeriod
import com.prasbin.shadowmoney.assistant.ASSISTANT_SOURCE_TEXT
import com.prasbin.shadowmoney.assistant.IntentClassifier
import com.prasbin.shadowmoney.assistant.requiresData
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: AssistantRepository
    private lateinit var budgetRepository: BudgetRepository
    private lateinit var workRepository: WorkRepository
    private lateinit var telecomRepository: TelecomRepository
    private lateinit var opportunityRepository: OpportunityRepository
    private lateinit var goalRepository: GoalRepository
    private lateinit var transactionDao: TransactionDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var accountDao: AccountDao
    private lateinit var goalDao: GoalDao

    private val now = 1_800_000_000_000L
    private val engine = AssistantEngine()

    private val currentKey = BudgetCalendar.currentMonthKey(now)
    private val lastKey = BudgetCalendar.shiftMonth(currentKey, -1)
    private val todayStart = com.prasbin.shadowmoney.assistant.AssistantTime
        .startOfDayKathmandu(now).toInstant().toEpochMilli()
    private val dayMs = 86_400_000L

    private var accountId: Long = 0L
    private var foodCategoryId: Long = 0L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()

        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        goalDao = database.goalDao()

        budgetRepository = BudgetRepository(
            budgetDao = database.budgetDao(),
            transactionDao = transactionDao,
            categoryDao = categoryDao,
            openHelper = database.openHelper
        )
        goalRepository = GoalRepository(goalDao, accountDao, transactionDao)
        workRepository = WorkRepository(
            workItemDao = database.workItemDao(),
            transactionDao = transactionDao,
            openHelper = database.openHelper,
            clock = { now }
        )
        telecomRepository = TelecomRepository(database.telecomDao(), clock = { now })
        opportunityRepository = OpportunityRepository(database.opportunityDao(), clock = { now })

        repository = AssistantRepository(
            dashboardRepository = DashboardRepository(
                accountDao = accountDao,
                categoryDao = categoryDao,
                transactionDao = transactionDao,
                goalDao = goalDao,
                budgetRepository = budgetRepository,
                openHelper = database.openHelper
            ),
            budgetRepository = budgetRepository,
            goalRepository = goalRepository,
            workRepository = workRepository,
            telecomRepository = telecomRepository,
            opportunityRepository = opportunityRepository,
            intelligenceRepository = IntelligenceRepository(
                transactionDao = transactionDao,
                categoryDao = categoryDao
            ),
            transactionDao = transactionDao,
            workItemDao = database.workItemDao(),
            opportunityDao = database.opportunityDao(),
            telecomDao = database.telecomDao(),
            openHelper = database.openHelper,
            clock = { now }
        )

        seed()
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun seed() = runBlocking {
        accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 100_000L)
        )
        foodCategoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = foodCategoryId,
                amountMinor = 2_000L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = todayStart + 3_600_000L,
                note = "Today lunch"
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = foodCategoryId,
                amountMinor = 500L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = todayStart + 7_200_000L,
                note = "Today snack"
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 10_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = todayStart + 10_800_000L,
                note = "Payment"
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = foodCategoryId,
                amountMinor = 700L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = todayStart - dayMs + 5 * 3_600_000L,
                note = "Yesterday"
            )
        )
        transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = foodCategoryId,
                amountMinor = 300L,
                direction = TRANSACTION_DIRECTION_OUTFLOW,
                transactionTimestamp = BudgetCalendar.monthStart(currentKey) - 3 * dayMs + 4 * 3_600_000L,
                note = "Last month"
            )
        )
    }

    private suspend fun load(input: String) =
        repository.load(IntentClassifier.classify(input))

    // ---- PERIOD AGGREGATES -------------------------------------------------

    @Test
    fun load_thisMonth_incomeOutflowCounts_matchSeededRecords() = runBlocking {
        val data = load("what did i spend this month")
        assertEquals(AssistantPeriod.THIS_MONTH, IntentClassifier.classify("what did i spend this month").period)
        assertEquals("this month", data.periodLabel)
        assertNotNull(data.periodRange)
        assertNotNull(data.periodTotals)

        val totals = data.periodTotals!!
        assertEquals(3_200L, totals.outflowMinor)
        assertEquals(10_000L, totals.incomeMinor)
        assertEquals(6_800L, totals.netMinor)
        assertEquals(4, totals.transactionCount)
        assertEquals(3, totals.outflowCount)
        assertEquals(1, totals.incomeCount)
    }

    @Test
    fun load_thisMonth_categoryBreakdown_isLargestFirst() = runBlocking {
        val data = load("what did i spend this month")
        val breakdown = data.periodTotals!!.categoryBreakdown
        assertEquals(1, breakdown.size)
        assertEquals("Food", breakdown[0].name)
        assertEquals(3_200L, breakdown[0].totalMinor)
    }

    @Test
    fun load_yesterday_windowContainsOnlyYesterdayRecords() = runBlocking {
        val data = load("what did i spend yesterday")
        assertEquals(AssistantPeriod.YESTERDAY, IntentClassifier.classify("what did i spend yesterday").period)
        assertEquals("yesterday", data.periodLabel)
        assertEquals(700L, data.periodTotals!!.outflowMinor)
        assertEquals(0L, data.periodTotals!!.incomeMinor)
        assertEquals(1, data.periodTotals!!.transactionCount)
        assertEquals(1, data.windowTransactions.size)
        assertEquals("Yesterday", data.windowTransactions[0].note)
    }

    @Test
    fun load_lastMonth_windowContainsOnlyLastMonthRecords() = runBlocking {
        val data = load("what did i spend last month")
        assertEquals(AssistantPeriod.LAST_MONTH, IntentClassifier.classify("what did i spend last month").period)
        assertEquals(300L, data.periodTotals!!.outflowMinor)
        assertEquals(1, data.periodTotals!!.transactionCount)
        assertEquals(1, data.windowTransactions.size)
        assertEquals("Last month", data.windowTransactions[0].note)
    }

    @Test
    fun load_incomeThisMonth_answersWithSeededAmount() = runBlocking {
        val question = IntentClassifier.classify("how much income did i make this month")
        val data = repository.load(question)
        val response = engine.answer(question, data)
        val text = response.sections.joinToString("\n") { it.text }
        assertTrue(text.contains(Money.formatNpr(10_000L)))
        assertTrue(text.contains(Money.formatNpr(6_800L)))
        assertEquals(ASSISTANT_SOURCE_TEXT, response.source)
    }

    @Test
    fun load_balance_matchesDashboardTotals() = runBlocking {
        val data = load("what is my balance")
        // Opening 100,000 + income 10,000 - outflows 3,500 = 106,500.
        assertEquals(106_500L, data.totalBalanceActiveMinor)
        assertEquals(1, data.accounts.size)
        assertEquals("Wallet", data.accounts[0].name)
        assertEquals(106_500L, data.accounts[0].balanceMinor)
        assertTrue(data.accounts[0].isActive)
    }

    @Test
    fun load_recentTransactions_newestFirstFromDashboard() = runBlocking {
        val data = load("recent transactions")
        assertEquals(5, data.recentTransactions.size)
        assertEquals("Payment", data.recentTransactions[0].note)
        assertEquals("Today snack", data.recentTransactions[1].note)
    }

    // ---- DOMAIN SECTIONS ---------------------------------------------------

    @Test
    fun load_budget_currentAndLastMonthInfo() = runBlocking {
        budgetRepository.createOverallBudget(currentKey, 50_000L)

        val current = load("how is my budget")
        assertTrue(current.budgetCurrent.hasAny)
        assertEquals(50_000L, current.budgetCurrent.overall!!.amountMinor)
        assertEquals(BudgetCalendar.monthLabel(currentKey), current.budgetCurrent.monthLabel)
        assertFalse(current.budgetLast.hasAny)

        val last = load("how is my budget last month")
        assertFalse(last.budgetLast.hasAny)
        assertEquals(BudgetCalendar.monthLabel(lastKey), last.budgetLast.monthLabel)
    }

    @Test
    fun load_budgetWithoutRecord_hasAnyFalseAndLabelExists() = runBlocking {
        val data = load("how is my budget")
        assertFalse(data.budgetCurrent.hasAny)
        assertNull(data.budgetCurrent.overall)
        assertEquals(BudgetCalendar.monthLabel(currentKey), data.budgetCurrent.monthLabel)
        assertEquals(BudgetCalendar.monthLabel(lastKey), data.budgetLast.monthLabel)
    }

    @Test
    fun load_goals_progressFromLinkedAccount() = runBlocking {
        goalRepository.create(
            Goal(name = "Laptop", targetAmountMinor = 10_000L, accountId = accountId)
        )
        val data = load("how are my goals")
        assertEquals(1, data.goals.size)
        val goal = data.goals[0]
        assertEquals("Laptop", goal.name)
        assertEquals(10_000L, goal.targetMinor)
        assertEquals(106_500L, goal.currentMinor)
        // Faithful to GoalRepository: raw percent, uncoerced.
        assertEquals(1065, goal.percentUsed)
        assertEquals("Wallet", goal.accountName)

        val question = IntentClassifier.classify("how are my goals")
        val text = engine.answer(question, data).sections.joinToString("\n") { it.text }
        assertTrue(text.contains("(100%)"))
        assertFalse(text.contains("1065%"))
    }

    @Test
    fun load_work_expectedVsReceived() = runBlocking {
        val workId = workRepository.create("Site", "Build", 50_000L, 0L, "")
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                amountMinor = 10_000L,
                direction = TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = todayStart + 14 * 3_600_000L,
                note = "Milestone"
            )
        )
        workRepository.linkTransaction(txId, workId)

        val data = load("my work")
        assertEquals(1, data.work.size)
        val item = data.work[0]
        assertEquals("Site", item.title)
        assertEquals("Active", item.statusLabel)
        assertTrue(item.isActive)
        assertEquals(50_000L, item.expectedMinor)
        assertEquals(10_000L, item.receivedMinor)
        assertEquals(40_000L, item.remainingExpectedMinor)
        assertEquals(116_500L, data.totalBalanceActiveMinor)
    }

    @Test
    fun load_telecom_summaryFromExistingRepository() = runBlocking {
        assertNull(load("my telecom cost").telecom)

        telecomRepository.createSim("Personal", "NTC", "98", "")
        val data = load("my telecom cost")
        assertNotNull(data.telecom)
        assertEquals(1, data.telecom!!.activeSimCount)
        assertEquals(0, data.telecom!!.activeSubscriptionCount)
        assertEquals(0L, data.telecom!!.expectedMonthlyCostMinor)
        assertTrue(data.telecom!!.upcomingRenewals.isEmpty())
    }

    @Test
    fun load_opportunities_summaryFromExistingRepository() = runBlocking {
        assertNull(load("what opportunities do i have").opportunities)

        opportunityRepository.create(
            "Website", "Build", OPPORTUNITY_TYPE_FREELANCE, "", "", 50_000L, 0L, ""
        )
        val data = load("what opportunities do i have")
        assertNotNull(data.opportunities)
        assertEquals(1, data.opportunities!!.activeCount)
        assertEquals(1, data.opportunities!!.needsReviewCount)
        assertEquals(50_000L, data.opportunities!!.totalExpectedAmountMinor)
        assertEquals(listOf("Website" to "New"), data.opportunities!!.tracked)
        val stored = database.opportunityDao().observeOpportunities(null, null, null, null).first()
        assertEquals(OPPORTUNITY_STATUS_NEW, stored[0].status)
    }

    // ---- EMPTY / BOUNDED ---------------------------------------------------

    @Test
    fun load_emptyDatabase_producesSafeDefaultsForEveryIntent() = runBlocking {
        database.close()
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val emptyDb = Room.inMemoryDatabaseBuilder(
            app.applicationContext, ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val emptyRepo = buildRepository(emptyDb)
            for (input in listOf(
                "what is my balance",
                "how much income did i make this month",
                "what did i spend this month",
                "how is my budget",
                "how are my goals",
                "my work",
                "my telecom cost",
                "what opportunities do i have",
                "recent transactions"
            )) {
                val question = IntentClassifier.classify(input)
                val data = emptyRepo.load(question)
                val response = engine.answer(question, data)
                assertTrue(input, response.sections.isNotEmpty())
                assertEquals(input, ASSISTANT_SOURCE_TEXT, response.source)
            }
            val balance = emptyRepo.load(IntentClassifier.classify("what is my balance"))
            assertEquals(0L, balance.totalBalanceActiveMinor)
            assertTrue(balance.goals.isEmpty())
            assertTrue(balance.work.isEmpty())
            assertNull(balance.telecom)
            assertNull(balance.opportunities)
            assertFalse(balance.budgetCurrent.hasAny)
        } finally {
            emptyDb.close()
        }
    }

    private fun buildRepository(db: ShadowMoneyDatabase): AssistantRepository {
        val txDao = db.transactionDao()
        val catDao = db.categoryDao()
        val accDao = db.accountDao()
        val gDao = db.goalDao()
        return AssistantRepository(
            dashboardRepository = DashboardRepository(accDao, catDao, txDao, gDao, BudgetRepository(db.budgetDao(), txDao, catDao, db.openHelper), db.openHelper),
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

    // ---- READ-ONLY GUARANTEE ----------------------------------------------

    private data class DbDump(
        val accounts: List<Account>,
        val categories: List<Category>,
        val transactions: List<Transaction>,
        val goals: List<Goal>,
        val work: List<com.prasbin.shadowmoney.data.model.WorkItem>,
        val opportunities: List<Opportunity>,
        val sims: List<com.prasbin.shadowmoney.data.model.TelecomSim>,
        val packages: List<com.prasbin.shadowmoney.data.model.TelecomPackage>,
        val subscriptions: List<com.prasbin.shadowmoney.data.model.TelecomSubscription>,
        val budgetsCurrent: List<com.prasbin.shadowmoney.data.model.Budget>,
        val budgetsLast: List<com.prasbin.shadowmoney.data.model.Budget>,
        val budgetCount: Int
    )

    private suspend fun dump(): DbDump = DbDump(
        accounts = accountDao.getAll().first(),
        categories = categoryDao.getAll().first(),
        transactions = transactionDao.getAll().first(),
        goals = goalDao.observeAll().first(),
        work = database.workItemDao().observeAll().first(),
        opportunities = database.opportunityDao().observeOpportunities(null, null, null, null).first(),
        sims = database.telecomDao().observeSims().first(),
        packages = database.telecomDao().observePackages().first(),
        subscriptions = database.telecomDao().observeSubscriptions().first(),
        budgetsCurrent = database.budgetDao().getByMonthOnce(currentKey),
        budgetsLast = database.budgetDao().getByMonthOnce(lastKey),
        budgetCount = database.budgetDao().observeBudgetCount().first()
    )

    @Test
    fun everyIntentAnswer_isReadOnly_databaseNeverChanges() = runBlocking {
        budgetRepository.createOverallBudget(currentKey, 50_000L)
        goalRepository.create(Goal(name = "Laptop", targetAmountMinor = 10_000L, accountId = accountId))
        workRepository.create("Site", "Build", 50_000L, 0L, "")
        telecomRepository.createSim("Personal", "NTC", "98", "")
        opportunityRepository.create("Website", "Build", OPPORTUNITY_TYPE_FREELANCE, "", "", 50_000L, 0L, "")

        val before = dump()

        val inputs = listOf(
            "what is my balance",
            "how much income did i make this month",
            "what did i spend this month",
            "what did i spend yesterday",
            "what did i spend last month",
            "how is my budget",
            "how is my budget last month",
            "how are my goals",
            "my work",
            "my telecom cost",
            "what opportunities do i have",
            "recent transactions",
            "how does import work",
            "help",
            "secret target",
            "income and spending",
            "tell me a joke",
            "what did i spend next month",
            "how is my budget today",
            "show my archived account balances"
        )

        for (input in inputs) {
            val question = IntentClassifier.classify(input)
            val data = if (question.intent.requiresData) repository.load(question) else null
            val response = engine.answer(question, data)
            assertTrue(input, response.sections.isNotEmpty())
            assertEquals(input, ASSISTANT_SOURCE_TEXT, response.source)
        }

        assertEquals(before, dump())
    }

    @Test
    fun load_answersAreDeterministic() = runBlocking {
        val question = IntentClassifier.classify("what did i spend this month")
        val first = repository.load(question)
        val second = repository.load(question)
        assertEquals(first, second)
        assertEquals(engine.answer(question, first), engine.answer(question, second))
    }
}
