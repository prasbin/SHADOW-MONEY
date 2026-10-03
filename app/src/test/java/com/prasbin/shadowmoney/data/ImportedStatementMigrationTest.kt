package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.model.ImportedStatement
import com.prasbin.shadowmoney.data.model.STATEMENT_FORMAT_CSV
import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_SANIMA
import com.prasbin.shadowmoney.data.model.STATEMENT_SOURCE_UNKNOWN
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real populated v9 → v10 migration test for imported statement metadata.
 * The v9 database is hand-built exactly as app/schemas/.../9.json declares it,
 * including `externalRef TEXT DEFAULT NULL`, then opened through the full
 * migration chain so MIGRATION_9_10 runs for real.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImportedStatementMigrationTest {

    private val dbName = "migration-v9-to-v10-imported-statements.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    private fun createPopulatedV9Database(ctx: android.content.Context) {
        ctx.deleteDatabase(dbName)
        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        val ddl = listOf(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` INTEGER NOT NULL, `openingBalanceMinor` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `direction` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `isSystem` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, `workItemId` INTEGER, `amountMinor` INTEGER NOT NULL, `direction` INTEGER NOT NULL, `transactionTimestamp` INTEGER NOT NULL, `note` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, `externalRef` TEXT DEFAULT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , FOREIGN KEY(`workItemId`) REFERENCES `work_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `targetAmountMinor` INTEGER NOT NULL, `accountId` INTEGER, `deadlineTimestamp` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, `monthKey` TEXT NOT NULL, `categoryId` INTEGER, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE TABLE IF NOT EXISTS `work_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `status` INTEGER NOT NULL, `expectedAmountMinor` INTEGER NOT NULL, `deadlineTimestamp` INTEGER NOT NULL, `client` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `telecom_sims` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `label` TEXT NOT NULL, `carrier` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `status` INTEGER NOT NULL, `notes` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `telecom_packages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `carrier` TEXT NOT NULL, `category` TEXT NOT NULL, `priceMinor` INTEGER NOT NULL, `period` INTEGER NOT NULL, `notes` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `telecom_subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `simId` INTEGER NOT NULL, `packageId` INTEGER NOT NULL, `startTimestamp` INTEGER NOT NULL, `renewalTimestamp` INTEGER NOT NULL, `monthlyCostMinor` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL, FOREIGN KEY(`simId`) REFERENCES `telecom_sims`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`packageId`) REFERENCES `telecom_packages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
            "CREATE TABLE IF NOT EXISTS `opportunities` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `type` INTEGER NOT NULL, `source` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `expectedAmountMinor` INTEGER, `status` INTEGER NOT NULL, `deadlineTimestamp` INTEGER NOT NULL, `client` TEXT NOT NULL, `createdTimestamp` INTEGER NOT NULL, `updatedTimestamp` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `financial_connections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `provider` TEXT NOT NULL, `status` TEXT NOT NULL, `capabilities` TEXT NOT NULL, `availabilityNote` TEXT NOT NULL, `lastVerifiedAtMs` INTEGER, `verifiedBalanceMinor` INTEGER, `updatedAtMs` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `balance_baselines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `provider` TEXT NOT NULL, `baselineMinor` INTEGER NOT NULL, `provenance` TEXT NOT NULL, `setAtMs` INTEGER NOT NULL, `sourceVerifiedAtMs` INTEGER, `sourceSet` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_transactionTimestamp` ON `transactions` (`transactionTimestamp`)",
            "CREATE INDEX IF NOT EXISTS `index_transactions_workItemId` ON `transactions` (`workItemId`)",
            "CREATE INDEX IF NOT EXISTS `index_goals_accountId` ON `goals` (`accountId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_monthKey` ON `budgets` (`categoryId`, `monthKey`)",
            "CREATE INDEX IF NOT EXISTS `index_budgets_monthKey` ON `budgets` (`monthKey`)",
            "CREATE INDEX IF NOT EXISTS `index_work_items_status` ON `work_items` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_telecom_packages_carrier` ON `telecom_packages` (`carrier`)",
            "CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_simId` ON `telecom_subscriptions` (`simId`)",
            "CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_packageId` ON `telecom_subscriptions` (`packageId`)",
            "CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_renewalTimestamp` ON `telecom_subscriptions` (`renewalTimestamp`)",
            "CREATE INDEX IF NOT EXISTS `index_opportunities_status` ON `opportunities` (`status`)",
            "CREATE INDEX IF NOT EXISTS `index_opportunities_type` ON `opportunities` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_opportunities_deadlineTimestamp` ON `opportunities` (`deadlineTimestamp`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_connections_provider` ON `financial_connections` (`provider`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_balance_baselines_provider` ON `balance_baselines` (`provider`)"
        )
        ddl.forEach { sqlite.execSQL(it) }

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Bank', 1, 900000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source, externalRef) VALUES (1, 1, NULL, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'IMPORT_FILE', 'TX-1')")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source) VALUES (2, NULL, NULL, 250000, 0, 1700000001000, 'Paycheck', 1700000001000, 'manual')")
        sqlite.execSQL("INSERT INTO financial_connections (provider, status, capabilities, availabilityNote, lastVerifiedAtMs, verifiedBalanceMinor, updatedAtMs) VALUES ('SANIMA', 'UNAVAILABLE', '[]', 'no public API', NULL, NULL, 1700000000000)")
        sqlite.execSQL("INSERT INTO balance_baselines (provider, baselineMinor, provenance, setAtMs, sourceVerifiedAtMs, sourceSet) VALUES ('SANIMA', 100000, 'MANUAL_ENTRY', 1700000000000, NULL, 'MANUAL')")
        sqlite.version = 9
        sqlite.close()
    }

    private fun openMigrated(ctx: android.content.Context): ShadowMoneyDatabase =
        Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10
            )
            .allowMainThreadQueries()
            .build()

    @After
    fun cleanup() {
        appContext().deleteDatabase(dbName)
    }

    @Test
    fun migration9To10_preservesAllExistingData() {
        val ctx = appContext()
        createPopulatedV9Database(ctx)
        val database = openMigrated(ctx)
        try {
            runBlocking {
                assertEquals(2, database.accountDao().getAll().first().size)
                assertEquals(1, database.categoryDao().getAll().first().size)

                val transactions = database.transactionDao().getAll().first()
                assertEquals(2, transactions.size)
                val lunch = transactions.first { it.id == 1L }
                assertEquals(5_000L, lunch.amountMinor)
                assertEquals("IMPORT_FILE", lunch.source)
                assertEquals("TX-1", lunch.externalRef)
                assertNull(lunch.statementId)

                assertEquals(1, database.connectionDao().observeConnections().first().size)
                assertEquals(
                    100_000L,
                    database.connectionDao().getBaselinesOnce().first().baselineMinor
                )
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration9To10_addsStatementIdColumnAndImportedStatementsTable() {
        val ctx = appContext()
        createPopulatedV9Database(ctx)
        val database = openMigrated(ctx)
        try {
            database.openHelper.readableDatabase
                .query("PRAGMA table_info(`transactions`)", emptyArray())
                .use { cursor ->
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) columns.add(cursor.getString(1))
                    assertTrue("statementId column must exist", columns.contains("statementId"))
                }

            database.openHelper.readableDatabase
                .query("PRAGMA table_info(`imported_statements`)", emptyArray())
                .use { cursor ->
                    val columns = mutableSetOf<String>()
                    while (cursor.moveToNext()) columns.add(cursor.getString(1))
                    assertTrue(columns.contains("provider"))
                    assertTrue(columns.contains("endBalanceMinor"))
                    assertTrue(columns.contains("fileSha256"))
                    assertTrue(columns.contains("notes"))
                }

            runBlocking {
                val statementId = database.importedStatementDao().insert(
                    ImportedStatement(
                        provider = STATEMENT_SOURCE_SANIMA,
                        documentName = "statement.csv",
                        format = STATEMENT_FORMAT_CSV,
                        importedAtMs = 1_790_000_000_000L,
                        transactionCount = 1,
                        rowCount = 1,
                        notes = "synthetic fixture"
                    )
                )
                assertTrue(statementId > 0)

                val stored = database.importedStatementDao().getById(statementId)!!
                assertEquals(STATEMENT_SOURCE_SANIMA, stored.provider)
                assertEquals("statement.csv", stored.documentName)
                assertNull(stored.endBalanceMinor)

                val linked = database.transactionDao().insert(
                    Transaction(
                        accountId = 1L,
                        categoryId = 1L,
                        amountMinor = 1_200L,
                        direction = 1,
                        transactionTimestamp = 1_790_000_000_000L,
                        note = "Statement coffee",
                        createdTimestamp = 1_790_000_000_000L,
                        source = "IMPORT_FILE",
                        statementId = statementId
                    )
                )
                assertEquals(statementId, database.transactionDao().getById(linked)!!.statementId)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration9To10_freshStatementDefaultsToUnknownSource() {
        val ctx = appContext()
        createPopulatedV9Database(ctx)
        val database = openMigrated(ctx)
        try {
            runBlocking {
                val id = database.importedStatementDao().insert(
                    ImportedStatement(
                        documentName = "statement.pdf",
                        format = "PDF",
                        importedAtMs = 1_790_000_000_000L
                    )
                )
                val stored = database.importedStatementDao().getById(id)!!
                assertEquals(STATEMENT_SOURCE_UNKNOWN, stored.provider)
            }
        } finally {
            database.close()
        }
    }
}
