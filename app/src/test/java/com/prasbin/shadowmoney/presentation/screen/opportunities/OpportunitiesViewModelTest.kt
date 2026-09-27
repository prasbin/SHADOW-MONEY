package com.prasbin.shadowmoney.presentation.screen.opportunities

import androidx.room.Room
import com.prasbin.shadowmoney.data.OpportunityRepository
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE
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
class OpportunitiesViewModelTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var repository: OpportunityRepository

    private val now = 1_800_000_000_000L

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = OpportunityRepository(database.opportunityDao(), clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun viewModel(): OpportunitiesViewModel = OpportunitiesViewModel(repository, clock = { now })

    @Test
    fun emptyState_whenNoOpportunities() = runBlocking {
        val viewModel = viewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Empty }
        }
        assertTrue(state is OpportunitiesUiState.Empty)
    }

    @Test
    fun createOpportunity_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }

        viewModel.create("Website", "Build", OPPORTUNITY_TYPE_FREELANCE, "Upwork", "", 50_000L, 0L, "Acme")

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content && it.opportunities.size == 1 }
        } as OpportunitiesUiState.Content
        assertEquals("Website", state.opportunities.first().opportunity.title)
        assertEquals(50_000L, state.opportunities.first().opportunity.expectedAmountMinor)
        assertEquals(1, state.summary.activeCount)
        assertEquals(50_000L, state.summary.totalExpectedAmountMinor)
    }

    @Test
    fun statusFilter_filtersDisplayedItems() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }
        viewModel.create("Active", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        viewModel.create("Archived", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content && it.opportunities.size == 2 }
        }
        val archivedId = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content }
        }.let { (it as OpportunitiesUiState.Content).opportunities.first { view -> view.opportunity.title == "Archived" }.opportunity.id }
        viewModel.archive(archivedId)
        withTimeout(10_000) {
            viewModel.uiState.first {
                it is OpportunitiesUiState.Content && it.opportunities.size == 2 &&
                    it.opportunities.any { view -> view.opportunity.status == com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED }
            }
        }

        viewModel.setStatusFilter(OPPORTUNITY_STATUS_ARCHIVED)

        val filtered = withTimeout(10_000) {
            viewModel.uiState.first {
                it is OpportunitiesUiState.Content && it.opportunities.size == 1 &&
                    it.opportunities.first().opportunity.title == "Archived"
            }
        } as OpportunitiesUiState.Content
        assertEquals(1, filtered.opportunities.size)
    }

    @Test
    fun search_filtersByTitle() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }
        viewModel.create("Website build", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        viewModel.create("Logo design", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content && it.opportunities.size == 2 }
        }

        viewModel.setSearchQuery("website")

        val filtered = withTimeout(10_000) {
            viewModel.uiState.first {
                it is OpportunitiesUiState.Content && it.opportunities.size == 1 &&
                    it.opportunities.first().opportunity.title == "Website build"
            }
        } as OpportunitiesUiState.Content
        assertEquals(1, filtered.opportunities.size)
    }

    @Test
    fun archive_reflectedInContent() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }
        viewModel.create("Goal", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content && it.opportunities.isNotEmpty() }
        }
        val id = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content }
        }.let { (it as OpportunitiesUiState.Content).opportunities.first().opportunity.id }

        viewModel.archive(id)

        val archived = withTimeout(10_000) {
            viewModel.uiState.first {
                it is OpportunitiesUiState.Content &&
                    it.opportunities.first().opportunity.status == com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
            }
        } as OpportunitiesUiState.Content
        assertEquals(
            com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED,
            archived.opportunities.first().opportunity.status
        )
    }

    @Test
    fun delete_removesOpportunity() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }
        viewModel.create("Title", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        val created = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Content && it.opportunities.isNotEmpty() }
        } as OpportunitiesUiState.Content

        viewModel.delete(created.opportunities.first().opportunity)

        val after = withTimeout(10_000) {
            viewModel.uiState.first { it is OpportunitiesUiState.Empty }
        }
        assertTrue(after is OpportunitiesUiState.Empty)
    }

    @Test
    fun validationError_surfacesMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }

        viewModel.create("", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")

        val error = withTimeout(10_000) { viewModel.errorMessage.first { it != null } }
        assertTrue(error!!.contains("Title"))
    }

    @Test
    fun invalidUrlError_surfacesMessage() = runBlocking {
        val viewModel = viewModel()
        withTimeout(10_000) { viewModel.uiState.first { it is OpportunitiesUiState.Empty } }

        viewModel.create("Title", "d", OPPORTUNITY_TYPE_FREELANCE, "", "not-a-url", null, 0L, "")

        val error = withTimeout(10_000) { viewModel.errorMessage.first { it != null } }
        assertTrue(error!!.contains("URL"))
    }
}
