package com.prasbin.shadowmoney.data

import com.prasbin.shadowmoney.intelligence.AnalysisWindow
import com.prasbin.shadowmoney.intelligence.IntelligenceEngine
import com.prasbin.shadowmoney.intelligence.IntelligenceReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class IntelligenceRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val engine: IntelligenceEngine = IntelligenceEngine()
) {

    fun observeChanges(): Flow<Unit> = combine(
        transactionDao.observeTransactionCount(),
        categoryDao.getAll()
    ) { _, _ -> Unit }

    suspend fun loadReport(now: Long): IntelligenceReport {
        val fetchStart = AnalysisWindow.previousStart(now)
        val transactions = transactionDao.getTransactionsInWindow(fetchStart, now).first()
        val categories = categoryDao.getAll().first()
        return engine.analyze(transactions, categories, now)
    }
}
