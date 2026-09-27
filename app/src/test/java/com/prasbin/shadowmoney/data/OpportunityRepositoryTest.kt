package com.prasbin.shadowmoney.data

import androidx.room.Room
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE
import com.prasbin.shadowmoney.data.model.Opportunity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OpportunityRepositoryTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var opportunityDao: OpportunityDao
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
        opportunityDao = database.opportunityDao()
        repository = OpportunityRepository(opportunityDao, clock = { now })
    }

    @After
    fun closeDb() {
        database.close()
    }

    private fun opportunity(
        title: String,
        type: Int = OPPORTUNITY_TYPE_FREELANCE,
        status: Int = OPPORTUNITY_STATUS_NEW,
        expectedAmountMinor: Long? = null,
        deadlineTimestamp: Long = 0L,
        sourceUrl: String = ""
    ) = Opportunity(
        title = title,
        type = type,
        status = status,
        expectedAmountMinor = expectedAmountMinor,
        deadlineTimestamp = deadlineTimestamp,
        sourceUrl = sourceUrl
    )

    @Test
    fun create_persistsOpportunity() = runBlocking {
        val id = repository.create("Website", "Build", OPPORTUNITY_TYPE_FREELANCE, "Upwork", "", 50_000L, 0L, "Acme")
        assertTrue(id > 0)
        val stored = opportunityDao.getById(id)
        assertNotNull(stored)
        assertEquals("Website", stored!!.title)
        assertEquals(50_000L, stored.expectedAmountMinor)
        assertEquals(OPPORTUNITY_STATUS_NEW, stored.status)
    }

    @Test
    fun create_blankTitle_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("  ", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "") }
        }
        Unit
    }

    @Test
    fun create_negativeExpectedAmount_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("Title", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "", -1L, 0L, "") }
        }
        Unit
    }

    @Test
    fun create_invalidUrl_rejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("Title", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "not-a-url", null, 0L, "") }
        }
        Unit
    }

    @Test
    fun create_emptyUrl_allowed() = runBlocking {
        val id = repository.create("Title", "desc", OPPORTUNITY_TYPE_FREELANCE, "GitHub", "", 1_000L, 0L, "")
        assertTrue(id > 0)
    }

    @Test
    fun update_persistsChanges() = runBlocking {
        val id = repository.create("Old", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        val existing = opportunityDao.getById(id)!!
        repository.update(existing, "New", "new desc", com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_CLIENT_WORK, "LinkedIn", "https://linkedin.com/job/1", 25_000L, OPPORTUNITY_STATUS_IN_PROGRESS, now + 86_400_000L, "Client")
        val updated = opportunityDao.getById(id)!!
        assertEquals("New", updated.title)
        assertEquals(25_000L, updated.expectedAmountMinor)
        assertEquals(OPPORTUNITY_STATUS_IN_PROGRESS, updated.status)
    }

    @Test
    fun archive_setsArchivedStatus() = runBlocking {
        val id = repository.create("Title", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        repository.archive(id)
        assertEquals(OPPORTUNITY_STATUS_ARCHIVED, opportunityDao.getById(id)!!.status)
    }

    @Test
    fun delete_removesOpportunity() = runBlocking {
        val id = repository.create("Title", "desc", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        repository.delete(opportunityDao.getById(id)!!)
        assertNull(opportunityDao.getById(id))
    }

    @Test
    fun statusFilter_works() = runBlocking {
        repository.create("Active", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        val archivedId = repository.create("Archived", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        repository.archive(archivedId)

        val active = repository.observeOpportunities(OPPORTUNITY_STATUS_NEW, null, null, null).first()
        assertEquals(1, active.size)
        val archived = repository.observeOpportunities(OPPORTUNITY_STATUS_ARCHIVED, null, null, null).first()
        assertEquals(1, archived.size)
        val all = repository.observeOpportunities(null, null, null, null).first()
        assertEquals(2, all.size)
    }

    @Test
    fun typeFilter_works() = runBlocking {
        repository.create("Freelance", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", null, 0L, "")
        repository.create("Repo", "d", com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY, "", "", null, 0L, "")

        val freelance = repository.observeOpportunities(null, OPPORTUNITY_TYPE_FREELANCE, null, null).first()
        assertEquals(1, freelance.size)
        assertEquals("Freelance", freelance.first().title)
    }

    @Test
    fun textSearch_matchesTitleClientSourceNotes() = runBlocking {
        repository.create("Website build", "d", OPPORTUNITY_TYPE_FREELANCE, "Upwork", "", null, 0L, "Acme")
        repository.create("Logo", "d", OPPORTUNITY_TYPE_FREELANCE, "Fiverr", "", null, 0L, "Beta")

        assertEquals(1, repository.observeOpportunities(null, null, null, "website").first().size)
        assertEquals(1, repository.observeOpportunities(null, null, null, "acme").first().size)
        assertEquals(1, repository.observeOpportunities(null, null, null, "upwork").first().size)
        assertEquals(2, repository.observeOpportunities(null, null, null, "").first().size)
        assertEquals(0, repository.observeOpportunities(null, null, null, "nonexistent").first().size)
    }

    @Test
    fun sourceFilter_works() = runBlocking {
        repository.create("A", "d", OPPORTUNITY_TYPE_FREELANCE, "Upwork", "", null, 0L, "")
        repository.create("B", "d", OPPORTUNITY_TYPE_FREELANCE, "Fiverr", "", null, 0L, "")

        val upwork = repository.observeOpportunities(null, null, "Upwork", null).first()
        assertEquals(1, upwork.size)
    }

    @Test
    fun loadViews_computesDeadlineAndGithubRef() = runBlocking {
        val id = repository.create(
            "Repo work", "d", com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY,
            "GitHub", "https://github.com/owner/repo/issues/42", 10_000L, now + 5L * 86_400_000L, ""
        )
        val views = repository.loadViews(listOf(opportunityDao.getById(id)!!))
        assertEquals(1, views.size)
        assertEquals(DeadlineStatus.UPCOMING, views.first().deadlineStatus)
        assertNotNull(views.first().githubReference)
        assertEquals("owner", views.first().githubReference!!.owner)
        assertEquals("repo", views.first().githubReference!!.repository)
        assertEquals("issues/42", views.first().githubReference!!.reference)
    }

    @Test
    fun loadSummary_computesDeterministicFacts() = runBlocking {
        repository.create("A", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", 10_000L, 0L, "")
        repository.create("B", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", 20_000L, 0L, "")
        repository.create("C", "d", OPPORTUNITY_TYPE_FREELANCE, "", "", 30_000L, now + 3L * 86_400_000L, "")

        val summary = repository.loadSummary()
        assertEquals(3, summary.activeCount)
        assertEquals(60_000L, summary.totalExpectedAmountMinor)
        assertEquals(3, summary.needsReviewCount)
        assertEquals(1, summary.upcomingDeadlines.size)
        assertEquals("C", summary.upcomingDeadlines.first().title)
    }

    @Test
    fun emptyDatabase_summaryZeroes() = runBlocking {
        val summary = repository.loadSummary()
        assertEquals(0, summary.activeCount)
        assertEquals(0L, summary.totalExpectedAmountMinor)
        assertEquals(0, summary.needsReviewCount)
        assertTrue(summary.upcomingDeadlines.isEmpty())
    }
}
