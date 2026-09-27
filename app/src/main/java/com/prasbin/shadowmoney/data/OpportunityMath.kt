package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW
import com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING
import com.prasbin.shadowmoney.data.model.Opportunity

const val OPPORTUNITY_UPCOMING_DEADLINE_CAP = 5

object OpportunityMath {

    fun typeLabel(type: Int): String = when (type) {
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_FREELANCE -> "Freelance"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_CLIENT_WORK -> "Client Work"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PART_TIME -> "Part Time"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REMOTE_WORK -> "Remote Work"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_PROJECT -> "Project"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_REPOSITORY -> "Repository"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_TYPE_OTHER -> "Other"
        else -> "Unknown"
    }

    fun statusLabel(status: Int): String = when (status) {
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_NEW -> "New"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_REVIEWING -> "Reviewing"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_APPLIED -> "Applied"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_IN_PROGRESS -> "In Progress"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_WON -> "Won"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_LOST -> "Lost"
        com.prasbin.shadowmoney.data.model.OPPORTUNITY_STATUS_ARCHIVED -> "Archived"
        else -> "Unknown"
    }
}

data class OpportunitySummaryData(
    val activeCount: Int,
    val totalExpectedAmountMinor: Long,
    val needsReviewCount: Int,
    val upcomingDeadlines: List<Opportunity>
)

object OpportunitySummary {

    fun summarize(opportunities: List<Opportunity>, now: Long): OpportunitySummaryData {
        val active = opportunities.filter { it.status != OPPORTUNITY_STATUS_ARCHIVED }
        val totalExpected = active.sumOf { it.expectedAmountMinor ?: 0L }
        val needsReview = active.count {
            it.status == OPPORTUNITY_STATUS_NEW || it.status == OPPORTUNITY_STATUS_REVIEWING
        }
        val upcoming = active
            .filter { it.deadlineTimestamp > 0L && WorkMath.deadlineStatus(it.deadlineTimestamp, now) != DeadlineStatus.OVERDUE }
            .sortedBy { it.deadlineTimestamp }
            .take(OPPORTUNITY_UPCOMING_DEADLINE_CAP)
        return OpportunitySummaryData(
            activeCount = active.size,
            totalExpectedAmountMinor = totalExpected,
            needsReviewCount = needsReview,
            upcomingDeadlines = upcoming
        )
    }
}

data class GithubReference(
    val owner: String,
    val repository: String,
    val reference: String?
)

object UrlParser {

    private val githubRegex = Regex(
        "^https?://(?:www\\.)?github\\.com/([^/\\s]+)/([^/\\s]+?)(?:\\.git)?(?:/(issues|pull|tree|blob|wiki)/([^/\\s]+(?:/[^/\\s]+)*))?/?$",
        RegexOption.IGNORE_CASE
    )

    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
        val withoutScheme = trimmed.removePrefix("https://").removePrefix("http://")
        if (withoutScheme.isBlank()) return false
        if (withoutScheme.contains(" ")) return false
        return withoutScheme.contains(".")
    }

    fun parseGithubReference(url: String): GithubReference? {
        val match = githubRegex.matchEntire(url.trim()) ?: return null
        val owner = match.groupValues[1]
        val repository = match.groupValues[2]
        val type = match.groupValues[3]
        val path = match.groupValues[4]
        val reference = if (type.isBlank() || path.isBlank()) null else "$type/$path"
        return GithubReference(owner, repository, reference)
    }
}
