package com.prasbin.shadowmoney.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "placeholder")
data class PlaceholderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = ""
)
