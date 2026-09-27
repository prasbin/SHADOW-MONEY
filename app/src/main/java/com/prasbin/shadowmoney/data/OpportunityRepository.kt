package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.Opportunity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class OpportunityView(
    val opportunity: Opportunity,
    val deadlineStatus: DeadlineStatus,
    val githubReference: GithubReference?
)

class OpportunityRepository(
    private val opportunityDao: OpportunityDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun observeOpportunities(
        statusFilter: Int?,
        typeFilter: Int?,
        sourceFilter: String?,
        search: String?
    ): Flow<List<Opportunity>> =
        opportunityDao.observeOpportunities(statusFilter, typeFilter, sourceFilter, search)

    fun observeOpportunityDetail(id: Long): Flow<Opportunity?> =
        opportunityDao.observeById(id)

    fun observeChanges(): Flow<Unit> = combine(
        opportunityDao.observeCount()
    ) { Unit }

    suspend fun loadViews(opportunities: List<Opportunity>): List<OpportunityView> {
        val now = clock()
        return opportunities.map { opportunity ->
            OpportunityView(
                opportunity = opportunity,
                deadlineStatus = WorkMath.deadlineStatus(opportunity.deadlineTimestamp, now),
                githubReference = UrlParser.parseGithubReference(opportunity.sourceUrl)
            )
        }
    }

    suspend fun loadSummary(): OpportunitySummaryData {
        val opportunities = opportunityDao.observeOpportunities(null, null, null, null).first()
        return OpportunitySummary.summarize(opportunities, clock())
    }

    suspend fun create(
        title: String,
        description: String,
        type: Int,
        source: String,
        sourceUrl: String,
        expectedAmountMinor: Long?,
        deadlineTimestamp: Long,
        client: String
    ): Long {
        validateTitle(title)
        validateUrl(sourceUrl)
        validateAmount(expectedAmountMinor)
        val now = clock()
        return opportunityDao.insert(
            Opportunity(
                title = title,
                description = description,
                type = type,
                source = source,
                sourceUrl = sourceUrl,
                expectedAmountMinor = expectedAmountMinor,
                deadlineTimestamp = deadlineTimestamp,
                client = client,
                createdTimestamp = now,
                updatedTimestamp = now
            )
        )
    }

    suspend fun update(
        opportunity: Opportunity,
        title: String,
        description: String,
        type: Int,
        source: String,
        sourceUrl: String,
        expectedAmountMinor: Long?,
        status: Int,
        deadlineTimestamp: Long,
        client: String
    ) {
        validateTitle(title)
        validateUrl(sourceUrl)
        validateAmount(expectedAmountMinor)
        opportunityDao.update(
            opportunity.copy(
                title = title,
                description = description,
                type = type,
                source = source,
                sourceUrl = sourceUrl,
                expectedAmountMinor = expectedAmountMinor,
                status = status,
                deadlineTimestamp = deadlineTimestamp,
                client = client,
                updatedTimestamp = clock()
            )
        )
    }

    suspend fun archive(id: Long) {
        opportunityDao.updateStatus(id, OPPORTUNITY_STATUS_ARCHIVED, clock())
    }

    suspend fun delete(opportunity: Opportunity) {
        opportunityDao.delete(opportunity)
    }

    private fun validateTitle(title: String) {
        if (title.isBlank()) {
            throw IllegalArgumentException("Title is required")
        }
    }

    private fun validateUrl(url: String) {
        if (url.isBlank()) return
        if (!UrlParser.isValidUrl(url)) {
            throw IllegalArgumentException("Invalid URL format")
        }
    }

    private fun validateAmount(amountMinor: Long?) {
        if (amountMinor != null && amountMinor < 0L) {
            throw IllegalArgumentException("Expected amount cannot be negative")
        }
    }
}
