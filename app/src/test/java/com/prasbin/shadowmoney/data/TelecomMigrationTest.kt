package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
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
class TelecomMigrationTest {

    private val dbName = "migration-v4-to-v5-telecom.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    private fun createPhase6V4Database(ctx: android.content.Context) {
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
                `workItemId` INTEGER,
                `amountMinor` INTEGER NOT NULL,
                `direction` INTEGER NOT NULL,
                `transactionTimestamp` INTEGER NOT NULL,
                `note` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`workItemId`) REFERENCES `work_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
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
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `work_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `expectedAmountMinor` INTEGER NOT NULL,
                `deadlineTimestamp` INTEGER NOT NULL,
                `client` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_monthKey` ON `budgets` (`categoryId`, `monthKey`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_monthKey` ON `budgets` (`monthKey`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_work_items_status` ON `work_items` (`status`)")
        sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_workItemId` ON `transactions` (`workItemId`)")

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (1, 1, NULL, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'manual')")
        sqlite.execSQL("INSERT INTO goals (name, targetAmountMinor, accountId, deadlineTimestamp, isActive, isCompleted, createdTimestamp, updatedTimestamp) VALUES ('Save', 500000, 1, 1800000000000, 1, 0, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO budgets (amountMinor, monthKey, categoryId, createdTimestamp, updatedTimestamp) VALUES (50000, '2026-09', 1, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO work_items (title, description, status, expectedAmountMinor, deadlineTimestamp, client, createdTimestamp, updatedTimestamp) VALUES ('Project', 'desc', 0, 100000, 1800000000000, 'Client', 1700000000000, 1700000000000)")
        sqlite.version = 4
        sqlite.close()
    }

    @Test
    fun migration4To5_createsTelecomTablesAndPreservesAllData() {
        val ctx = appContext()
        createPhase6V4Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                assertEquals(1, database.accountDao().getAll().first().size)
                assertEquals(1, database.categoryDao().getAll().first().size)
                val transactions = database.transactionDao().getAll().first()
                assertEquals(1, transactions.size)
                assertEquals(5_000L, transactions.first().amountMinor)
                assertEquals(1, database.goalDao().getAllActive().first().size)
                assertEquals(1, database.budgetDao().getByMonthOnce("2026-09").size)
                assertEquals(1, database.workItemDao().observeAll().first().size)

                val simId = database.telecomDao().insertSim(
                    TelecomSim(label = "Personal", carrier = "Nepal Telecom")
                )
                assertTrue(simId > 0)
                val packageId = database.telecomDao().insertPackage(
                    TelecomPackage(name = "Data 1GB", priceMinor = 1_500L)
                )
                assertTrue(packageId > 0)
                val subscriptionId = database.telecomDao().insertSubscription(
                    TelecomSubscription(simId = simId, packageId = packageId)
                )
                assertTrue(subscriptionId > 0)
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration4To5_telecomSchemaCorrect() {
        val ctx = appContext()
        createPhase6V4Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('telecom_sims','telecom_packages','telecom_subscriptions')",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertEquals(
                    setOf("telecom_sims", "telecom_packages", "telecom_subscriptions"),
                    names
                )
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='telecom_subscriptions'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_telecom_subscriptions_simId"))
                assertTrue(names.contains("index_telecom_subscriptions_packageId"))
                assertTrue(names.contains("index_telecom_subscriptions_renewalTimestamp"))
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='telecom_packages'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_telecom_packages_carrier"))
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration4To5_subscriptionForeignKeysEnforced() {
        val ctx = appContext()
        createPhase6V4Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val simId = database.telecomDao().insertSim(TelecomSim(label = "S"))
                val packageId = database.telecomDao().insertPackage(
                    TelecomPackage(name = "P", priceMinor = 1_000L)
                )
                database.telecomDao().insertSubscription(
                    TelecomSubscription(simId = simId, packageId = packageId)
                )

                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    runBlocking {
                        database.telecomDao().insertSubscription(
                            TelecomSubscription(simId = 99L, packageId = packageId)
                        )
                    }
                }
                assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
                    runBlocking {
                        database.telecomDao().insertSubscription(
                            TelecomSubscription(simId = simId, packageId = 99L)
                        )
                    }
                }
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }
}
