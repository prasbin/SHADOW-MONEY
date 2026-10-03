package com.prasbin.shadowmoney.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.prasbin.shadowmoney.data.model.ImportedStatement
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access for imported statement metadata. Statements are written
 * once at import time and only read afterwards (reconciliation evidence,
 * statement age display). There is no update or delete path in this phase.
 */
@Dao
interface ImportedStatementDao {

    @Insert
    suspend fun insert(statement: ImportedStatement): Long

    @Query("SELECT * FROM imported_statements WHERE id = :id")
    suspend fun getById(id: Long): ImportedStatement?

    @Query("SELECT * FROM imported_statements ORDER BY importedAtMs DESC")
    fun observeAll(): Flow<List<ImportedStatement>>

    @Query("SELECT * FROM imported_statements ORDER BY importedAtMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ImportedStatement>

    @Query("SELECT * FROM imported_statements WHERE fileSha256 = :sha256")
    suspend fun findBySha256(sha256: String): List<ImportedStatement>
}
