package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.data.model.*
import kotlinx.coroutines.flow.Flow

class AccountRepository(private val accountDao: AccountDao) {
    fun getAllActive(): Flow<List<Account>> = accountDao.getAllActive()
    fun getAll(): Flow<List<Account>> = accountDao.getAll()
    suspend fun getById(id: Long): Account? = accountDao.getById(id)
    suspend fun insert(account: Account): Long = accountDao.insert(account)
    suspend fun update(account: Account) = accountDao.update(account)
    suspend fun delete(account: Account) = accountDao.delete(account)
    suspend fun getTotalBalanceMinor(): Long = accountDao.getTotalBalanceMinor()
    suspend fun getAccountBalanceMinor(accountId: Long): Long = accountDao.getAccountBalanceMinor(accountId)
}

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun getAllActive(): Flow<List<Category>> = categoryDao.getAllActive()
    fun getAll(): Flow<List<Category>> = categoryDao.getAll()
    fun getByDirection(direction: Int): Flow<List<Category>> = categoryDao.getByDirection(direction)
    suspend fun getById(id: Long): Category? = categoryDao.getById(id)
    suspend fun insert(category: Category): Long = categoryDao.insert(category)
    suspend fun insertAll(categories: List<Category>) = categoryDao.insertAll(categories)
    suspend fun update(category: Category) = categoryDao.update(category)
    suspend fun delete(category: Category) = categoryDao.delete(category)
    suspend fun archive(id: Long) = categoryDao.archive(id)
    suspend fun getSystemCategoryCount(): Int = categoryDao.getSystemCategoryCount()
}

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {
    fun getAll(): Flow<List<Transaction>> = transactionDao.getAll()
    fun getByAccountId(accountId: Long): Flow<List<Transaction>> = transactionDao.getByAccountId(accountId)
    fun getByCategoryId(categoryId: Long): Flow<List<Transaction>> = transactionDao.getByCategoryId(categoryId)
    suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)
    suspend fun insert(transaction: Transaction): Long = transactionDao.insert(transaction)
    suspend fun insertAll(transactions: List<Transaction>) = transactionDao.insertAll(transactions)
    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)
    suspend fun getTotalIncomeMinor(accountId: Long): Long = transactionDao.getTotalIncomeMinor(accountId)
    suspend fun getTotalOutflowMinor(accountId: Long): Long = transactionDao.getTotalOutflowMinor(accountId)
    suspend fun getCount(): Int = transactionDao.getCount()

    suspend fun getBalanceMinor(accountId: Long): Long {
        val income = transactionDao.getTotalIncomeMinor(accountId)
        val outflow = transactionDao.getTotalOutflowMinor(accountId)
        val opening = accountDao.getAccountBalanceMinor(accountId)
        return Money.balanceMinor(opening, income, outflow)
    }
}

class GoalRepository(private val goalDao: GoalDao) {
    fun getAllActive(): Flow<List<Goal>> = goalDao.getAllActive()
    fun getActiveNotCompleted(): Flow<List<Goal>> = goalDao.getActiveNotCompleted()
    suspend fun getById(id: Long): Goal? = goalDao.getById(id)
    suspend fun insert(goal: Goal): Long = goalDao.insert(goal)
    suspend fun update(goal: Goal) = goalDao.update(goal)
    suspend fun delete(goal: Goal) = goalDao.delete(goal)
    suspend fun markComplete(id: Long) = goalDao.markComplete(id)
    suspend fun archive(id: Long) = goalDao.archive(id)
}
