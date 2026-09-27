package com.prasbin.shadowmoney.presentation.screen.work

import androidx.room.Room
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.WorkRepository
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ARCHIVED
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
class WorkViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: WorkRepository

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = WorkRepository(
            workItemDao = database.workItemDao(),
            transactionDao = database.transactionDao(),
            openHelper = database.openHelper,
            clock = { now }
        )
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(): WorkViewModel = WorkViewModel(repository, clock = { now })

    @Test
    fun emptyState_whenNoWorkItems() = runBlocking {
        val viewModel = viewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Empty }
        }
        assertTrue(state is WorkUiState.Empty)
    }

    @Test
    fun createWorkItem_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }

        viewModel.create("Website", "Build", 100_000L, 0L, "Acme")

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.size == 1 }
        } as WorkUiState.Content
        assertEquals("Website", state.items.first().workItem.title)
        assertEquals(100_000L, state.items.first().workItem.expectedAmountMinor)
        assertEquals(0L, state.items.first().receivedMinor)
    }

    @Test
    fun archive_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }
        viewModel.create("Website", "", 1_000L, 0L, "")
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.isNotEmpty() }
        } as WorkUiState.Content
        val id = created.items.first().workItem.id

        viewModel.archive(id)

        val archived = withTimeout(10_000) {
            viewModel.uiState.first {
                it is WorkUiState.Content && it.items.first().workItem.status == WORK_STATUS_ARCHIVED
            }
        } as WorkUiState.Content
        assertEquals(WORK_STATUS_ARCHIVED, archived.items.first().workItem.status)
    }

    @Test
    fun statusFilter_filtersDisplayedItems() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }
        viewModel.create("Active one", "", 1_000L, 0L, "")
        viewModel.create("Archived one", "", 2_000L, 0L, "")
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.size == 2 }
        } as WorkUiState.Content
        val archivedId = created.items.first { it.workItem.title == "Archived one" }.workItem.id
        viewModel.archive(archivedId)
        val afterArchive = withTimeout(10_000) {
            viewModel.uiState.first {
                it is WorkUiState.Content && it.items.size == 2 &&
                    it.items.any { view -> view.workItem.id == archivedId && view.workItem.status == WORK_STATUS_ARCHIVED }
            }
        } as WorkUiState.Content
        assertTrue(afterArchive.items.any { it.workItem.status == WORK_STATUS_ARCHIVED })

        viewModel.setStatusFilter(WORK_STATUS_ARCHIVED)

        val filtered = withTimeout(10_000) {
            viewModel.uiState.first {
                it is WorkUiState.Content && it.items.size == 1 &&
                    it.items.first().workItem.title == "Archived one"
            }
        } as WorkUiState.Content
        assertEquals(1, filtered.items.size)
        assertEquals(WORK_STATUS_ARCHIVED, filtered.items.first().workItem.status)

        viewModel.setStatusFilter(null)
        val all = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.size == 2 }
        } as WorkUiState.Content
        assertEquals(2, all.items.size)
    }

    @Test
    fun search_filtersByTitle() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }
        viewModel.create("Website build", "", 1_000L, 0L, "")
        viewModel.create("Logo design", "", 2_000L, 0L, "")
        withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.size == 2 }
        }

        viewModel.setSearchQuery("website")

        val filtered = withTimeout(10_000) {
            viewModel.uiState.first {
                it is WorkUiState.Content && it.items.size == 1 &&
                    it.items.first().workItem.title == "Website build"
            }
        } as WorkUiState.Content
        assertEquals(1, filtered.items.size)
    }

    @Test
    fun transactionChange_refreshesReceivedAmount() = runBlocking {
        val accountId = database.accountDao().insert(
            com.prasbin.shadowmoney.data.model.Account(
                name = "Wallet",
                type = com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET,
                openingBalanceMinor = 0L
            )
        )
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }
        viewModel.create("Project", "", 100_000L, 0L, "")
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is WorkUiState.Content && it.items.isNotEmpty() }
        } as WorkUiState.Content
        val workItem = created.items.first().workItem

        val txId = database.transactionDao().insert(
            com.prasbin.shadowmoney.data.model.Transaction(
                accountId = accountId,
                amountMinor = 25_000L,
                direction = com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_INCOME,
                transactionTimestamp = now - 1_000L,
                note = "Payment"
            )
        )
        repository.linkTransaction(txId, workItem.id)

        val updated = withTimeout(10_000) {
            viewModel.uiState.first {
                it is WorkUiState.Content && it.items.first().receivedMinor == 25_000L
            }
        } as WorkUiState.Content
        assertEquals(25_000L, updated.items.first().receivedMinor)
        assertEquals(75_000L, updated.items.first().remainingExpectedMinor)
    }

    @Test
    fun validationError_surfacesErrorMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }

        viewModel.create("", "", 1_000L, 0L, "")

        val error = withTimeout(10_000) {
            viewModel.errorMessage.first { it != null }
        }
        assertNotNull(error)
        assertTrue(error!!.contains("Title"))
    }

    @Test
    fun invalidAmountError_surfacesErrorMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is WorkUiState.Empty } }

        viewModel.create("Title", "", -5L, 0L, "")

        val error = withTimeout(10_000) {
            viewModel.errorMessage.first { it != null }
        }
        assertNotNull(error)
        assertTrue(error!!.contains("negative"))
    }
}
