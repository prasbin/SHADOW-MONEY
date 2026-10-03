package com.prasbin.shadowmoney.data.backup

import androidx.room.Dao
import androidx.room.Query
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WorkItem

/**
 * Read/count/delete queries for full backup and full-replacement restore.
 * Adds no tables, columns or indices: the database schema stays at version 7.
 */
@Dao
interface BackupDao {

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccounts(): List<Account>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategories(): List<Category>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<Transaction>

    @Query("SELECT * FROM goals")
    suspend fun getAllGoals(): List<Goal>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgets(): List<Budget>

    @Query("SELECT * FROM work_items")
    suspend fun getAllWorkItems(): List<WorkItem>

    @Query("SELECT * FROM telecom_sims")
    suspend fun getAllSims(): List<TelecomSim>

    @Query("SELECT * FROM telecom_packages")
    suspend fun getAllPackages(): List<TelecomPackage>

    @Query("SELECT * FROM telecom_subscriptions")
    suspend fun getAllSubscriptions(): List<TelecomSubscription>

    @Query("SELECT * FROM opportunities")
    suspend fun getAllOpportunities(): List<Opportunity>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun countAccounts(): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun countCategories(): Int

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun countTransactions(): Int

    @Query("SELECT COUNT(*) FROM goals")
    suspend fun countGoals(): Int

    @Query("SELECT COUNT(*) FROM budgets")
    suspend fun countBudgets(): Int

    @Query("SELECT COUNT(*) FROM work_items")
    suspend fun countWorkItems(): Int

    @Query("SELECT COUNT(*) FROM telecom_sims")
    suspend fun countSims(): Int

    @Query("SELECT COUNT(*) FROM telecom_packages")
    suspend fun countPackages(): Int

    @Query("SELECT COUNT(*) FROM telecom_subscriptions")
    suspend fun countSubscriptions(): Int

    @Query("SELECT COUNT(*) FROM opportunities")
    suspend fun countOpportunities(): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM imported_statements")
    suspend fun deleteAllImportedStatements()

    @Query("DELETE FROM goals")
    suspend fun deleteAllGoals()

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()

    @Query("DELETE FROM telecom_subscriptions")
    suspend fun deleteAllSubscriptions()

    @Query("DELETE FROM opportunities")
    suspend fun deleteAllOpportunities()

    @Query("DELETE FROM work_items")
    suspend fun deleteAllWorkItems()

    @Query("DELETE FROM telecom_packages")
    suspend fun deleteAllPackages()

    @Query("DELETE FROM telecom_sims")
    suspend fun deleteAllSims()

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}
