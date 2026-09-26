package com.prasbin.shadowmoney

import androidx.room.Room
import com.prasbin.shadowmoney.data.*
import com.prasbin.shadowmoney.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

/**
 * Verifies the ACTUAL delete/archive semantics of the implemented v2 schema:
 *
 * - accounts: archive preferred; FK is RESTRICT so an account with history
 *   cannot be deleted while transactions reference it.
 * - categories: archive preferred; FK is SET NULL so deleting a category
 *   nulls transaction.categoryId instead of deleting the transaction.
 * - transactions: historical records are never cascade-deleted.
 * - balance remains derived (opening + income - outflow); accounts carry
 *   only openingBalanceMinor, never a second editable balance column.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeleteArchiveSemanticsTest {

    private lateinit var database: ShadowMoneyDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var goalDao: GoalDao

    @Before
    fun createDb() {
        val app = Robolectric.buildActivity(android.app.Activity::class.java).create().get()
        val appContext = app.applicationContext
        database = Room.inMemoryDatabaseBuilder(
            appContext,
            ShadowMoneyDatabase::class.java
        ).allowMainThreadQueries().build()
        accountDao = database.accountDao()
        categoryDao = database.categoryDao()
        transactionDao = database.transactionDao()
        goalDao = database.goalDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun accountArchive_existingTransactionsRemainIntact() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        )
        val txId = transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 1000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
        )

        accountDao.archive(accountId)

        val account = accountDao.getById(accountId)
        assertNotNull(account)
        assertFalse("archived account must be inactive", account!!.isActive)

        val tx = transactionDao.getById(txId)
        assertNotNull("archiving an account must not touch its transactions", tx)
        assertEquals(accountId, tx!!.accountId)
    }

    @Test
    fun accountDelete_withHistoricalTransactions_isBlockedAndHistorySurvives() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Bank", type = ACCOUNT_TYPE_BANK, openingBalanceMinor = 10000L)
        )
        val txId = transactionDao.insert(
            Transaction(accountId = accountId, amountMinor = 2000L, direction = TRANSACTION_DIRECTION_INCOME)
        )
        val account = accountDao.getById(accountId)!!

        var blocked = false
        try {
            accountDao.delete(account)
        } catch (e: Exception) {
            // RESTRICT enforcement: SQLiteConstraintException (or wrapper).
            blocked = true
        }
        assertTrue(
            "deleting an account with historical transactions must be blocked by RESTRICT, " +
                "not cascade-delete history",
            blocked
        )

        val surviving = transactionDao.getById(txId)
        assertNotNull("transaction history must survive a blocked account delete", surviving)
        assertEquals(accountId, surviving!!.accountId)
        assertNotNull(accountDao.getById(accountId))
    }

    @Test
    fun accountDelete_withoutTransactions_succeeds() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Empty", type = ACCOUNT_TYPE_CASH, openingBalanceMinor = 0L)
        )
        val account = accountDao.getById(accountId)!!
        // No referencing rows: RESTRICT permits the delete.
        accountDao.delete(account)
        assertNull(accountDao.getById(accountId))
    }

    @Test
    fun categoryArchive_existingTransactionRemainsValid() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        )
        val categoryId = categoryDao.insert(
            Category(name = "Food", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 750L,
                direction = TRANSACTION_DIRECTION_OUTFLOW
            )
        )

        categoryDao.archive(categoryId)

        val category = categoryDao.getById(categoryId)
        assertNotNull(category)
        assertFalse(category!!.isActive)

        val tx = transactionDao.getById(txId)
        assertNotNull("archiving a category must keep the transaction valid", tx)
        assertEquals(categoryId, tx!!.categoryId)
    }

    @Test
    fun categoryDelete_transactionCategoryBecomesNullInsteadOfDelete() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        )
        val categoryId = categoryDao.insert(
            Category(name = "Travel", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val txId = transactionDao.insert(
            Transaction(
                accountId = accountId,
                categoryId = categoryId,
                amountMinor = 1200L,
                direction = TRANSACTION_DIRECTION_OUTFLOW
            )
        )
        val category = categoryDao.getById(categoryId)!!

        // Implemented FK is SET NULL: deleting the category must preserve
        // the transaction and null its category reference.
        categoryDao.delete(category)

        assertNull(categoryDao.getById(categoryId))
        val tx = transactionDao.getById(txId)
        assertNotNull("category delete must not delete the transaction (SET NULL expected)", tx)
        assertNull("transaction.categoryId must become null after category delete", tx!!.categoryId)
    }

    @Test
    fun transactionDelete_doesNotAffectAccountOrCategory() = runBlocking {
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 5000L)
        )
        val categoryId = categoryDao.insert(
            Category(name = "Bills", direction = CATEGORY_DIRECTION_OUTFLOW)
        )
        val tx = Transaction(
            accountId = accountId,
            categoryId = categoryId,
            amountMinor = 900L,
            direction = TRANSACTION_DIRECTION_OUTFLOW
        )
        val txId = transactionDao.insert(tx)

        transactionDao.delete(transactionDao.getById(txId)!!)

        assertNull(transactionDao.getById(txId))
        assertNotNull("deleting a transaction must not delete its account", accountDao.getById(accountId))
        assertNotNull("deleting a transaction must not delete its category", categoryDao.getById(categoryId))
    }

    @Test
    fun balanceRemainsDerived_noSecondEditableBalanceSource() = runBlocking {
        // Accounts carry ONLY openingBalanceMinor; live balance is derived
        // as opening + income - outflow via TransactionRepository.
        val accountId = accountDao.insert(
            Account(name = "Wallet", type = ACCOUNT_TYPE_WALLET, openingBalanceMinor = 10000L)
        )
        transactionDao.insertAll(
            listOf(
                Transaction(accountId = accountId, amountMinor = 3000L, direction = TRANSACTION_DIRECTION_INCOME),
                Transaction(accountId = accountId, amountMinor = 2000L, direction = TRANSACTION_DIRECTION_OUTFLOW)
            )
        )
        val balance = TransactionRepository(transactionDao, accountDao).getBalanceMinor(accountId)
        assertEquals(11000L, balance)

        // Schema check: accounts table must not contain a second balance column.
        database.openHelper.readableDatabase.query(
            "PRAGMA table_info(`accounts`)",
            emptyArray()
        ).use { cursor ->
            val cols = mutableSetOf<String>()
            while (cursor.moveToNext()) cols.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertTrue(cols.contains("openingBalanceMinor"))
            assertFalse(cols.contains("balanceMinor"))
            assertFalse(cols.contains("currentBalanceMinor"))
            assertFalse(cols.contains("balance"))
        }
    }

    @Test
    fun foreignKeys_useSafeDeleteActions_noCascadeOnFinancialHistory() {
        fun onDeleteActions(table: String): Map<String, String> {
            database.openHelper.readableDatabase.query(
                "PRAGMA foreign_key_list(`$table`)",
                emptyArray()
            ).use { cursor ->
                val map = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    map[cursor.getString(cursor.getColumnIndexOrThrow("from"))] =
                        cursor.getString(cursor.getColumnIndexOrThrow("on_delete"))
                }
                return map
            }
        }

        val txActions = onDeleteActions("transactions")
        assertEquals("RESTRICT", txActions["accountId"])
        assertEquals("SET NULL", txActions["categoryId"])
        assertEquals("SET NULL", onDeleteActions("goals")["accountId"])

        // Explicit guard: no CASCADE anywhere on financial history tables.
        assertFalse(txActions.values.contains("CASCADE"))
        assertFalse(onDeleteActions("goals").values.contains("CASCADE"))

        // Useful indices exist.
        database.openHelper.readableDatabase.query(
            "PRAGMA index_list(`transactions`)",
            emptyArray()
        ).use { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.moveToNext()) names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertTrue(names.contains("index_transactions_accountId"))
            assertTrue(names.contains("index_transactions_categoryId"))
            assertTrue(names.contains("index_transactions_transactionTimestamp"))
        }
    }
}
