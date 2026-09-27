package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telecom_subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = TelecomSim::class,
            parentColumns = ["id"],
            childColumns = ["simId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TelecomPackage::class,
            parentColumns = ["id"],
            childColumns = ["packageId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("simId"),
        Index("packageId"),
        Index("renewalTimestamp")
    ]
)
data class TelecomSubscription(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val simId: Long = 0L,
    val packageId: Long = 0L,
    val startTimestamp: Long = 0L,
    val renewalTimestamp: Long = 0L,
    val monthlyCostMinor: Long = 0L,
    val isActive: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)
