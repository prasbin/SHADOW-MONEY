package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val SIM_STATUS_ACTIVE = 0
const val SIM_STATUS_ARCHIVED = 1

@Entity(tableName = "telecom_sims")
data class TelecomSim(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String = "",
    val carrier: String = "",
    val phoneNumber: String = "",
    val status: Int = SIM_STATUS_ACTIVE,
    val notes: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
