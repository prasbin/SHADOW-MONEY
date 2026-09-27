package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val WORK_STATUS_ACTIVE = 0
const val WORK_STATUS_PAUSED = 1
const val WORK_STATUS_COMPLETED = 2
const val WORK_STATUS_ARCHIVED = 3

@Entity(tableName = "work_items")
data class WorkItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val status: Int = WORK_STATUS_ACTIVE,
    val expectedAmountMinor: Long = 0L,
    val deadlineTimestamp: Long = 0L,
    val client: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
