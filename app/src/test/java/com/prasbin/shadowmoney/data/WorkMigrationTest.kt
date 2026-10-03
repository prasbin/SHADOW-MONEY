package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WORK_STATUS_ACTIVE
import com.prasbin.shadowmoney.data.model.WorkItem
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
class WorkMigrationTest {

    private val dbName = "migration-v3-to-v4-work.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    private fun createPhase5V3Database(ctx: android.content.Context) {
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
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `monthKey` TEXT NOT NULL,
                `categoryId` INTEGER,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_monthKey` ON `budgets` (`categoryId`, `monthKey`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_monthKey` ON `budgets` (`monthKey`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionTimestamp` ON `transactions` (`transactionTimestamp`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_accountId` ON `goals` (`accountId`)")

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (1, 1, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'manual')")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (1, 1, 9999, 0, 1700000000000, 'Salary', 1700000000000, 'manual')")
        sqlite.execSQL("INSERT INTO goals (name, targetAmountMinor, accountId, deadlineTimestamp, isActive, isCompleted, createdTimestamp, updatedTimestamp) VALUES ('Save', 500000, 1, 1800000000000, 1, 0, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO budgets (amountMinor, monthKey, categoryId, createdTimestamp, updatedTimestamp) VALUES (50000, '2026-09', 1, 1700000000000, 1700000000000)")
        sqlite.version = 3
        sqlite.close()
    }

    @Test
    fun migration3To4_createsWorkItemsAndPreservesAllData() {
        val ctx = appContext()
        createPhase5V3Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertEquals(1, database.accountDao().getAll().first().size)
                assertEquals(1, database.categoryDao().getAll().first().size)
                val transactions = database.transactionDao().getAll().first()
                assertEquals(2, transactions.size)
                assertEquals(5_000L, transactions.first { it.note == "Lunch" }.amountMinor)
                assertEquals(9_999L, transactions.first { it.note == "Salary" }.amountMinor)
                assertEquals(1, database.goalDao().getAllActive().first().size)
                assertEquals(1, database.budgetDao().getByMonthOnce("2026-09").size)

                val workId = database.workItemDao().insert(
                    WorkItem(title = "Project", expectedAmountMinor = 100_000L, status = WORK_STATUS_ACTIVE)
                )
                assertTrue(workId > 0)

                val salary = transactions.first { it.note == "Salary" }
                database.transactionDao().setWorkItemId(salary.id, workId)

                val updated = database.transactionDao().getById(salary.id)!!
                assertEquals(workId, updated.workItemId)
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration3To4_workItemIdNullableAndFkSetNull() {
        val ctx = appContext()
        createPhase5V3Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val transactions = database.transactionDao().getAll().first()
                assertTrue(transactions.all { it.workItemId == null })

                val workId = database.workItemDao().insert(WorkItem(title = "W"))
                val lunch = transactions.first { it.note == "Lunch" }
                database.transactionDao().setWorkItemId(lunch.id, workId)

                database.workItemDao().delete(database.workItemDao().getById(workId)!!)

                val after = database.transactionDao().getById(lunch.id)!!
                assertNull(after.workItemId)
                assertEquals(5_000L, after.amountMinor)
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration3To4_schemaCorrect() {
        val ctx = appContext()
        createPhase5V3Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='work_items'",
                emptyArray()
            ).use { cursor ->
                assertEquals(1, cursor.count)
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='work_items'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_work_items_status"))
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='transactions'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_transactions_workItemId"))
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }
}
