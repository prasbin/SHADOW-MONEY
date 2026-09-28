package com.prasbin.shadowmoney.assistant

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.prasbin.shadowmoney.data.AssistantRepository
import com.prasbin.shadowmoney.data.BudgetRepository
import com.prasbin.shadowmoney.data.DashboardRepository
import com.prasbin.shadowmoney.data.GoalRepository
import com.prasbin.shadowmoney.data.IntelligenceRepository
import com.prasbin.shadowmoney.data.Money
import com.prasbin.shadowmoney.data.OpportunityRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.TelecomRepository
import com.prasbin.shadowmoney.data.SecretTargetStore
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 11 privacy boundary: the Local Financial Assistant must never read,
 * derive from, log, or expose the Secret Target. It answers only from
 * financial records.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssistantPrivacyTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: AssistantRepository
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secretStore: SecretTargetStore

    private val now = 1_800_000_000_000L
    private val secretValue = 777_777_777L
    private val engine = AssistantEngine()

    private val answerableInputs = listOf(
        "what is my balance",
        "how much income did i make this month",
        "what did i spend this month",
        "how is my budget",
        "how are my goals",
        "my work",
        "my telecom cost",
        "what opportunities do i have",
        "recent transactions"
    )

    @Before
    fun createDb() {
        val tempDir = createTempDir("assistant_privacy").absoluteFile
        dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.Default)) {
            File(tempDir, "assistant_privacy.preferences_pb")
        }
        secretStore = SecretTargetStore(dataStore)

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

    private fun answer(input: String): String {
        val question = IntentClassifier.classify(input)
        return runBlocking {
            val data = if (question.intent.requiresData) repository.load(question) else null
            engine.answer(question, data)
        }.sections.joinToString("\n") { it.text }
    }

    // ---- STATIC BOUNDARIES -------------------------------------------------

    @Test
    fun assistantRepository_constructorHasNoSecretTargetStoreParameter() {
        val parameterTypeNames = AssistantRepository::class.java.declaredConstructors
            .flatMap { constructor -> constructor.parameterTypes.map { it.name } }
        assertTrue(parameterTypeNames.isNotEmpty())
        assertTrue(
            parameterTypeNames.none { it.contains("SecretTargetStore") }
        )
    }

    @Test
    fun assistantDataModel_hasNoSecretFields() {
        val fieldNames = AssistantData::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(fieldNames.none { it.contains("secret") })
        val fieldTypes = AssistantData::class.java.declaredFields.map { it.type.name }
        assertTrue(fieldTypes.none { it.contains("SecretTarget") })
    }

    private fun findSourceFile(relative: String): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(4) {
            val candidate = File(dir, "app/src/main/java/com/prasbin/shadowmoney/$relative")
            if (candidate.exists()) return candidate
            val alt = File(dir, "src/main/java/com/prasbin/shadowmoney/$relative")
            if (alt.exists()) return alt
            dir = dir.parentFile ?: return candidate
        }
        return File(relative)
    }

    @Test
    fun assistantSources_neverReferenceSecretTargetStorage() {
        val sources = listOf(
            "assistant/IntentClassifier.kt",
            "assistant/AssistantIntent.kt",
            "assistant/AssistantTime.kt",
            "assistant/AssistantData.kt",
            "assistant/AssistantEngine.kt",
            "data/AssistantRepository.kt",
            "presentation/screen/assistant/AssistantViewModel.kt",
            "presentation/screen/assistant/AssistantScreen.kt"
        )
        for (path in sources) {
            val file = findSourceFile(path)
            assertTrue("Missing source: $path", file.exists())
            val text = file.readText()
            assertFalse("$path references SecretTargetStore", text.contains("SecretTargetStore"))
            assertFalse("$path logs with println", text.contains("println"))
        }
    }

    // ---- REFUSAL BOUNDARY --------------------------------------------------

    @Test
    fun secretTargetQuestions_classifiedAsRefusal() {
        for (input in listOf(
            "what is the secret target",
            "show my private target",
            "secret goal details"
        )) {
            assertEquals(input, AssistantIntent.SECRET_TARGET_REFUSAL, IntentClassifier.classify(input).intent)
        }
    }

    @Test
    fun refusalResponse_isExactTextAndPlainGuidance() {
        for (input in listOf("what is the secret target", "show my private target")) {
            val question = IntentClassifier.classify(input)
            val response = engine.answer(question, null)
            assertEquals(1, response.sections.size)
            assertEquals(IntentClassifier.SECRET_TARGET_REFUSAL_TEXT, response.sections[0].text)
            assertNull(response.sections[0].kind)
        }
    }

    @Test
    fun refusalResponse_neverContainsSecretValue() = runBlocking {
        secretStore.setTarget(secretValue)
        val response = answer("what is the secret target")
        assertEquals(IntentClassifier.SECRET_TARGET_REFUSAL_TEXT, response)
        assertFalse(response.contains(Money.formatNpr(secretValue)))
        assertFalse(response.contains(secretValue.toString()))
    }

    // ---- VALUE NEVER APPEARS IN ANSWERS ------------------------------------

    @Test
    fun secretValue_neverAppearsInAnyFinancialAnswer() = runBlocking {
        secretStore.setTarget(secretValue)

        for (input in answerableInputs) {
            val text = answer(input)
            assertFalse("$input leaked formatted secret", text.contains(Money.formatNpr(secretValue)))
            assertFalse("$input leaked raw secret", text.contains(secretValue.toString()))
        }
    }

    @Test
    fun changingSecretTarget_doesNotAlterAnyAnswer() = runBlocking {
        val questions = answerableInputs.map { IntentClassifier.classify(it) }

        suspend fun answerAll(): List<AssistantResponse> = questions.map { question ->
            val data = if (question.intent.requiresData) repository.load(question) else null
            engine.answer(question, data)
        }

        val before = answerAll()
        secretStore.setTarget(secretValue)
        val withFirstSecret = answerAll()
        secretStore.setTarget(1L)
        val withSecondSecret = answerAll()

        assertEquals(before, withFirstSecret)
        assertEquals(before, withSecondSecret)
    }

    @Test
    fun secretTargetSetting_doesNotAffectDatabaseState() = runBlocking {
        fun counts(): List<Any> = runBlocking {
            listOf(
                database.accountDao().getAll().first().size,
                database.transactionDao().getAll().first().size,
                database.goalDao().observeAll().first().size,
                database.transactionDao().observeTransactionCount().first()
            )
        }

        val before = counts()
        secretStore.setTarget(secretValue)
        answer("what is my balance")
        secretStore.setTarget(999_999_999L)
        answer("how are my goals")
        val after = counts()

        assertEquals(before, after)
    }
}
