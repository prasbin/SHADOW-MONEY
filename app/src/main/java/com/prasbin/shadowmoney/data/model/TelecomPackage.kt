package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val BILLING_PERIOD_WEEKLY = 0
const val BILLING_PERIOD_MONTHLY = 1
const val BILLING_PERIOD_QUARTERLY = 2
const val BILLING_PERIOD_YEARLY = 3

@Entity(
    tableName = "telecom_packages",
    indices = [Index("carrier")]
)
data class TelecomPackage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val carrier: String = "",
    val category: String = "",
    val priceMinor: Long = 0L,
    val period: Int = BILLING_PERIOD_MONTHLY,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
