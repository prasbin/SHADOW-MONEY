package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val OPPORTUNITY_TYPE_FREELANCE = 0
const val OPPORTUNITY_TYPE_CLIENT_WORK = 1
const val OPPORTUNITY_TYPE_PART_TIME = 2
const val OPPORTUNITY_TYPE_REMOTE_WORK = 3
const val OPPORTUNITY_TYPE_PROJECT = 4
const val OPPORTUNITY_TYPE_REPOSITORY = 5
const val OPPORTUNITY_TYPE_OTHER = 6

const val OPPORTUNITY_STATUS_NEW = 0
const val OPPORTUNITY_STATUS_REVIEWING = 1
const val OPPORTUNITY_STATUS_APPLIED = 2
const val OPPORTUNITY_STATUS_IN_PROGRESS = 3
const val OPPORTUNITY_STATUS_WON = 4
const val OPPORTUNITY_STATUS_LOST = 5
const val OPPORTUNITY_STATUS_ARCHIVED = 6

@Entity(
    tableName = "opportunities",
    indices = [
        Index("status"),
        Index("type"),
        Index("deadlineTimestamp")
    ]
)
data class Opportunity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val type: Int = OPPORTUNITY_TYPE_FREELANCE,
    val source: String = "",
    val sourceUrl: String = "",
    val expectedAmountMinor: Long? = null,
    val status: Int = OPPORTUNITY_STATUS_NEW,
    val deadlineTimestamp: Long = 0L,
    val client: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
