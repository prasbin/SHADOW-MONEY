package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.CATEGORY_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BudgetMigrationTest {

    private val dbName = "migration-v2-to-v3-budgets.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    private fun createPhase2V2Database(ctx: android.content.Context) {
        ctx.deleteDatabase(dbName)
        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` INTEGER NOT NULL,
                `openingBalanceMinor` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `direction` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `isSystem` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `transactions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `categoryId` INTEGER,
                `amountMinor` INTEGER NOT NULL,
                `direction` INTEGER NOT NULL,
                `transactionTimestamp` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetAmountMinor` INTEGER NOT NULL,
                `accountId` INTEGER,
                `deadlineTimestamp` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `isCompleted` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionTimestamp` ON `transactions` (`transactionTimestamp`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_accountId` ON `goals` (`accountId`)")

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Bank', 1, 200000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (1, 1, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'manual')")
        sqlite.version = 2
        sqlite.close()
    }

    @Test
    fun migration2To3_createsBudgetsTableAndPreservesData() {
        val ctx = appContext()
        createPhase2V2Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertEquals(2, database.accountDao().getAll().first().size)
                assertEquals(1, database.categoryDao().getAll().first().size)
                assertEquals(1, database.transactionDao().getAll().first().size)

                val categoryId = database.categoryDao().insert(
                    Category(name = "Travel", direction = CATEGORY_DIRECTION_OUTFLOW)
                )
                val budgetId = database.budgetDao().insert(
                    com.prasbin.shadowmoney.data.model.Budget(
                        amountMinor = 30_000L,
                        monthKey = "2026-09",
                        categoryId = categoryId
                    )
                )
                assertTrue(budgetId > 0)

                val overallId = database.budgetDao().insert(
                    com.prasbin.shadowmoney.data.model.Budget(
                        amountMinor = 100_000L,
                        monthKey = "2026-09",
                        categoryId = null
                    )
                )
                assertTrue(overallId > 0)

                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    runBlocking {
                        database.budgetDao().insert(
                            com.prasbin.shadowmoney.data.model.Budget(
                                amountMinor = 40_000L,
                                monthKey = "2026-09",
                                categoryId = categoryId
                            )
                        )
                    }
                }

                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    runBlocking {
                        database.budgetDao().insert(
                            com.prasbin.shadowmoney.data.model.Budget(
                                amountMinor = 40_000L,
                                monthKey = "2026-10",
                                categoryId = 999L
                            )
                        )
                    }
                }

                val budgets = database.budgetDao().getByMonthOnce("2026-09")
                assertEquals(2, budgets.size)
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration2To3_budgetsTableSchemaCorrect() {
        val ctx = appContext()
        createPhase2V2Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='budgets'",
                emptyArray()
            ).use { cursor ->
                assertEquals(1, cursor.count)
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='budgets'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_budgets_categoryId_monthKey"))
                assertTrue(names.contains("index_budgets_monthKey"))
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }
}
