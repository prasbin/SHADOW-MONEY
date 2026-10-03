package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ACCOUNT_TYPE_WALLET
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.TRANSACTION_DIRECTION_OUTFLOW
import com.prasbin.shadowmoney.data.model.Transaction
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
class OpportunityMigrationTest {

    private val dbName = "migration-v5-to-v6-opportunity.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    private fun createPhase8V5Database(ctx: android.content.Context) {
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
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `telecom_sims` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `label` TEXT NOT NULL,
                `carrier` TEXT NOT NULL,
                `phoneNumber` TEXT NOT NULL,
                `status` INTEGER NOT NULL,
                `notes` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `telecom_packages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `carrier` TEXT NOT NULL,
                `category` TEXT NOT NULL,
                `priceMinor` INTEGER NOT NULL,
                `period` INTEGER NOT NULL,
                `notes` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        sqlite.execSQL("""
            CREATE TABLE IF NOT EXISTS `telecom_subscriptions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `simId` INTEGER NOT NULL,
                `packageId` INTEGER NOT NULL,
                `startTimestamp` INTEGER NOT NULL,
                `renewalTimestamp` INTEGER NOT NULL,
                `monthlyCostMinor` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL,
                FOREIGN KEY(`simId`) REFERENCES `telecom_sims`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`packageId`) REFERENCES `telecom_packages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (1, 1, NULL, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'manual')")
        sqlite.execSQL("INSERT INTO goals (name, targetAmountMinor, accountId, deadlineTimestamp, isActive, isCompleted, createdTimestamp, updatedTimestamp) VALUES ('Save', 500000, 1, 1800000000000, 1, 0, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO budgets (amountMinor, monthKey, categoryId, createdTimestamp, updatedTimestamp) VALUES (50000, '2026-09', 1, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO work_items (title, description, status, expectedAmountMinor, deadlineTimestamp, client, createdTimestamp, updatedTimestamp) VALUES ('Project', 'desc', 0, 100000, 1800000000000, 'Client', 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO telecom_sims (label, carrier, phoneNumber, status, notes, createdTimestamp, updatedTimestamp) VALUES ('Personal', 'Nepal Telecom', '', 0, '', 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO telecom_packages (name, carrier, category, priceMinor, period, notes, isActive, createdTimestamp, updatedTimestamp) VALUES ('Data 1GB', 'Nepal Telecom', 'data', 1500, 1, '', 1, 1700000000000, 1700000000000)")
        sqlite.version = 5
        sqlite.close()
    }

    @Test
    fun migration5To6_createsOpportunitiesAndPreservesAllData() {
        val ctx = appContext()
        createPhase8V5Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
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
                assertEquals(1, database.telecomDao().observeSims().first().size)
                assertEquals(1, database.telecomDao().observePackages().first().size)

                val id = database.opportunityDao().insert(
                    Opportunity(title = "Freelance gig", expectedAmountMinor = 50_000L)
                )
                assertTrue(id > 0)
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }

    @Test
    fun migration5To6_opportunitySchemaCorrect() {
        val ctx = appContext()
        createPhase8V5Database(ctx)

        val database = Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        try {
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='opportunities'",
                emptyArray()
            ).use { cursor ->
                assertEquals(1, cursor.count)
            }
            database.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='opportunities'",
                emptyArray()
            ).use { cursor ->
                val names = mutableSetOf<String>()
                while (cursor.moveToNext()) names.add(cursor.getString(0))
                assertTrue(names.contains("index_opportunities_status"))
                assertTrue(names.contains("index_opportunities_type"))
                assertTrue(names.contains("index_opportunities_deadlineTimestamp"))
            }
        } finally {
            database.close()
            ctx.deleteDatabase(dbName)
        }
    }
}
