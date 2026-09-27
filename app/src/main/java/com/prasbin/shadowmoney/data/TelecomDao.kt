package com.prasbin.shadowmoney.data

import androidx.room.*
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import kotlinx.coroutines.flow.Flow

@Dao
interface TelecomDao {

    @Query("SELECT * FROM telecom_sims ORDER BY createdTimestamp DESC")
    fun observeSims(): Flow<List<TelecomSim>>

    @Query("SELECT * FROM telecom_sims WHERE id = :id")
    suspend fun getSimById(id: Long): TelecomSim?

    @Insert
    suspend fun insertSim(sim: TelecomSim): Long

    @Update
    suspend fun updateSim(sim: TelecomSim)

    @Query("UPDATE telecom_sims SET status = :status, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updateSimStatus(id: Long, status: Int, updatedTimestamp: Long)

    @Query("SELECT * FROM telecom_packages ORDER BY createdTimestamp DESC")
    fun observePackages(): Flow<List<TelecomPackage>>

    @Query("SELECT * FROM telecom_packages WHERE id = :id")
    suspend fun getPackageById(id: Long): TelecomPackage?

    @Insert
    suspend fun insertPackage(packageEntity: TelecomPackage): Long

    @Update
    suspend fun updatePackage(packageEntity: TelecomPackage)

    @Query("UPDATE telecom_packages SET isActive = :isActive, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updatePackageActive(id: Long, isActive: Boolean, updatedTimestamp: Long)

    @Query("SELECT * FROM telecom_subscriptions ORDER BY renewalTimestamp ASC")
    fun observeSubscriptions(): Flow<List<TelecomSubscription>>

    @Query("SELECT * FROM telecom_subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: Long): TelecomSubscription?

    @Insert
    suspend fun insertSubscription(subscription: TelecomSubscription): Long

    @Update
    suspend fun updateSubscription(subscription: TelecomSubscription)

    @Query("UPDATE telecom_subscriptions SET isActive = :isActive, updatedTimestamp = :updatedTimestamp WHERE id = :id")
    suspend fun updateSubscriptionActive(id: Long, isActive: Boolean, updatedTimestamp: Long)

    @Delete
    suspend fun deleteSubscription(subscription: TelecomSubscription)

    @Query("SELECT COUNT(*) FROM telecom_subscriptions")
    fun observeSubscriptionCount(): Flow<Int>
}
