package com.prasbin.shadowmoney.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ShadowMoneyDao {
    @Insert
    suspend fun insert(entity: com.prasbin.shadowmoney.data.model.PlaceholderEntity)

    @Query("SELECT * FROM placeholder")
    suspend fun getAll(): List<com.prasbin.shadowmoney.data.model.PlaceholderEntity>
}
