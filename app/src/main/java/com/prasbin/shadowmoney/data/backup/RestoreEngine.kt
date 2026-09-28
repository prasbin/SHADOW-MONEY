package com.prasbin.shadowmoney.data.backup

import androidx.room.withTransaction
import com.prasbin.shadowmoney.data.ShadowMoneyDatabase

/**
 * Applies a validated backup payload as a full replacement inside a single
 * Room transaction. Existing rows for every backup scope table are deleted
 * first (children before parents so foreign keys never block), then payload
 * rows are inserted with their original primary keys (parents before
 * children). Any failure rolls the whole transaction back, leaving the
 * database exactly as it was. The excluded protected target value lives
 * outside the database and is never read or written here.
 */
class RestoreEngine(private val database: ShadowMoneyDatabase) {

    suspend fun restore(payload: BackupPayload) {
        database.withTransaction {
            val dao = database.backupDao()
            dao.deleteAllTransactions()
            dao.deleteAllGoals()
            dao.deleteAllBudgets()
            dao.deleteAllSubscriptions()
            dao.deleteAllOpportunities()
            dao.deleteAllWorkItems()
            dao.deleteAllPackages()
            dao.deleteAllSims()
            dao.deleteAllCategories()
            dao.deleteAllAccounts()

            val accountDao = database.accountDao()
            for (account in payload.accounts) accountDao.insert(account.toEntity())
            val categoryDao = database.categoryDao()
            for (category in payload.categories) categoryDao.insert(category.toEntity())
            val workItemDao = database.workItemDao()
            for (workItem in payload.workItems) workItemDao.insert(workItem.toEntity())
            val telecomDao = database.telecomDao()
            for (sim in payload.telecomSims) telecomDao.insertSim(sim.toEntity())
            for (telecomPackage in payload.telecomPackages) telecomDao.insertPackage(telecomPackage.toEntity())
            val opportunityDao = database.opportunityDao()
            for (opportunity in payload.opportunities) opportunityDao.insert(opportunity.toEntity())
            val transactionDao = database.transactionDao()
            for (transaction in payload.transactions) transactionDao.insert(transaction.toEntity())
            val goalDao = database.goalDao()
            for (goal in payload.goals) goalDao.insert(goal.toEntity())
            val budgetDao = database.budgetDao()
            for (budget in payload.budgets) budgetDao.insert(budget.toEntity())
            for (subscription in payload.telecomSubscriptions) {
                telecomDao.insertSubscription(subscription.toEntity())
            }
        }
    }
}
