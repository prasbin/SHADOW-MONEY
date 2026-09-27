package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY
import com.prasbin.shadowmoney.data.model.Opportunity
import org.junit.Test
import org.junit.Assert.*

class OpportunityMathTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    @Test
    fun typeLabels_areBoundedAndDeterministic() {
        assertEquals("Freelance", OpportunityMath.typeLabel(OPPORTUNITY_TYPE_FREELANCE))
        assertEquals("Repository", OpportunityMath.typeLabel(OPPORTUNITY_TYPE_REPOSITORY))
        assertEquals("Unknown", OpportunityMath.typeLabel(99))
    }

    @Test
    fun statusLabels_areExplicit() {
        assertEquals("New", OpportunityMath.statusLabel(OPPORTUNITY_STATUS_NEW))
        assertEquals("Reviewing", OpportunityMath.statusLabel(OPPORTUNITY_STATUS_REVIEWING))
        assertEquals("In Progress", OpportunityMath.statusLabel(OPPORTUNITY_STATUS_IN_PROGRESS))
        assertEquals("Won", OpportunityMath.statusLabel(com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON))
        assertEquals("Lost", OpportunityMath.statusLabel(com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST))
        assertEquals("Archived", OpportunityMath.statusLabel(OPPORTUNITY_STATUS_ARCHIVED))
    }

    @Test
    fun summary_countsActiveExpectedAndReview() {
        val opportunities = listOf(
            Opportunity(title = "A", expectedAmountMinor = 10_000L, status = OPPORTUNITY_STATUS_NEW),
            Opportunity(title = "B", expectedAmountMinor = 20_000L, status = OPPORTUNITY_STATUS_REVIEWING),
            Opportunity(title = "C", expectedAmountMinor = 30_000L, status = OPPORTUNITY_STATUS_IN_PROGRESS),
            Opportunity(title = "D", expectedAmountMinor = 99_000L, status = OPPORTUNITY_STATUS_ARCHIVED)
        )
        val summary = OpportunitySummary.summarize(opportunities, now)
        assertEquals(3, summary.activeCount)
        assertEquals(60_000L, summary.totalExpectedAmountMinor)
        assertEquals(2, summary.needsReviewCount)
    }

    @Test
    fun summary_upcomingDeadlinesSortedAndCapped() {
        val opportunities = (1L..8L).map { id ->
            Opportunity(
                title = "O$id",
                status = OPPORTUNITY_STATUS_NEW,
                deadlineTimestamp = now + (9 - id) * day
            )
        }
        val summary = OpportunitySummary.summarize(opportunities, now)
        assertEquals(5, summary.upcomingDeadlines.size)
        assertEquals("O8", summary.upcomingDeadlines.first().title)
    }

    @Test
    fun summary_excludesOverdueAndArchivedFromUpcoming() {
        val opportunities = listOf(
            Opportunity(title = "Overdue", status = OPPORTUNITY_STATUS_NEW, deadlineTimestamp = now - day),
            Opportunity(title = "Archived", status = OPPORTUNITY_STATUS_ARCHIVED, deadlineTimestamp = now + day),
            Opportunity(title = "Upcoming", status = OPPORTUNITY_STATUS_NEW, deadlineTimestamp = now + day)
        )
        val summary = OpportunitySummary.summarize(opportunities, now)
        assertEquals(1, summary.upcomingDeadlines.size)
        assertEquals("Upcoming", summary.upcomingDeadlines.first().title)
    }

    @Test
    fun summary_emptyDatabase_zeroes() {
        val summary = OpportunitySummary.summarize(emptyList(), now)
        assertEquals(0, summary.activeCount)
        assertEquals(0L, summary.totalExpectedAmountMinor)
        assertEquals(0, summary.needsReviewCount)
        assertTrue(summary.upcomingDeadlines.isEmpty())
    }

    @Test
    fun urlValidation_basicStructure() {
        assertTrue(UrlParser.isValidUrl("https://example.com"))
        assertTrue(UrlParser.isValidUrl("http://example.com/path"))
        assertTrue(UrlParser.isValidUrl("https://github.com/owner/repo"))
        assertFalse(UrlParser.isValidUrl(""))
        assertFalse(UrlParser.isValidUrl("   "))
        assertFalse(UrlParser.isValidUrl("example.com"))
        assertFalse(UrlParser.isValidUrl("ftp://example.com"))
        assertFalse(UrlParser.isValidUrl("https://exa mple.com"))
    }

    @Test
    fun githubParsing_repositoryUrl() {
        val ref = UrlParser.parseGithubReference("https://github.com/owner/repository")
        assertNotNull(ref)
        assertEquals("owner", ref!!.owner)
        assertEquals("repository", ref.repository)
        assertNull(ref.reference)
    }

    @Test
    fun githubParsing_issueUrl() {
        val ref = UrlParser.parseGithubReference("https://github.com/owner/repository/issues/123")
        assertNotNull(ref)
        assertEquals("owner", ref!!.owner)
        assertEquals("repository", ref.repository)
        assertEquals("issues/123", ref.reference)
    }

    @Test
    fun githubParsing_nonGithubUrl_returnsNull() {
        assertNull(UrlParser.parseGithubReference("https://example.com/owner/repo"))
        assertNull(UrlParser.parseGithubReference("not a url"))
    }

    @Test
    fun githubParsing_wwwAndGitSuffix() {
        val ref = UrlParser.parseGithubReference("https://www.github.com/owner/repository.git")
        assertNotNull(ref)
        assertEquals("owner", ref!!.owner)
        assertEquals("repository", ref.repository)
    }
}
