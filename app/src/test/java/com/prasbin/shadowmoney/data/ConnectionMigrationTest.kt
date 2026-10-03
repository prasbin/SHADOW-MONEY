package com.prasbin.shadowmoney.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.prasbin.shadowmoney.data.connections.ConnectionCapability
import com.prasbin.shadowmoney.data.connections.ConnectionRepository
import com.prasbin.shadowmoney.data.connections.ConnectionStatus
import com.prasbin.shadowmoney.data.connections.FinancialConnection
import com.prasbin.shadowmoney.data.connections.Provenance
import com.prasbin.shadowmoney.data.connections.Provider
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
 * Genuine populated v7 → v8 migration: every pre-existing table with real rows
 * survives, the two new connection tables appear, and connection/baseline rows
 * round-trip with the unique per-provider constraint enforced.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConnectionMigrationTest {

    private val dbName = "migration-v7-to-v8-connections.db"

    private fun appContext() =
        Robolectric.buildActivity(android.app.Activity::class.java).create().get().applicationContext

    /** Creates a genuine, fully populated v7 database (post-externalRef schema). */
    private fun createV7Database(ctx: android.content.Context) {
        ctx.deleteDatabase(dbName)
        val file = ctx.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`name` TEXT NOT NULL,`type` INTEGER NOT NULL," +
                "`openingBalanceMinor` INTEGER NOT NULL,`isActive` INTEGER NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`name` TEXT NOT NULL,`direction` INTEGER NOT NULL," +
                "`isActive` INTEGER NOT NULL,`isSystem` INTEGER NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `transactions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`accountId` INTEGER NOT NULL,`categoryId` INTEGER,`workItemId` INTEGER," +
                "`amountMinor` INTEGER NOT NULL,`direction` INTEGER NOT NULL," +
                "`transactionTimestamp` INTEGER NOT NULL,`note` TEXT NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL,`source` TEXT NOT NULL," +
                "`externalRef` TEXT DEFAULT NULL," +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT," +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL," +
                "FOREIGN KEY(`workItemId`) REFERENCES `work_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`name` TEXT NOT NULL,`targetAmountMinor` INTEGER NOT NULL," +
                "`accountId` INTEGER,`deadlineTimestamp` INTEGER NOT NULL," +
                "`isActive` INTEGER NOT NULL,`isCompleted` INTEGER NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL,`updatedTimestamp` INTEGER NOT NULL," +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`amountMinor` INTEGER NOT NULL,`monthKey` TEXT NOT NULL," +
                "`categoryId` INTEGER,`createdTimestamp` INTEGER NOT NULL," +
                "`updatedTimestamp` INTEGER NOT NULL," +
                "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`title` TEXT NOT NULL,`description` TEXT NOT NULL,`status` INTEGER NOT NULL," +
                "`expectedAmountMinor` INTEGER NOT NULL,`deadlineTimestamp` INTEGER NOT NULL," +
                "`client` TEXT NOT NULL,`createdTimestamp` INTEGER NOT NULL," +
                "`updatedTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `telecom_sims` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`label` TEXT NOT NULL,`carrier` TEXT NOT NULL,`phoneNumber` TEXT NOT NULL," +
                "`status` INTEGER NOT NULL,`notes` TEXT NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL,`updatedTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `telecom_packages` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`name` TEXT NOT NULL,`carrier` TEXT NOT NULL,`category` TEXT NOT NULL," +
                "`priceMinor` INTEGER NOT NULL,`period` INTEGER NOT NULL,`notes` TEXT NOT NULL," +
                "`isActive` INTEGER NOT NULL,`createdTimestamp` INTEGER NOT NULL," +
                "`updatedTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `telecom_subscriptions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`simId` INTEGER NOT NULL,`packageId` INTEGER NOT NULL," +
                "`startTimestamp` INTEGER NOT NULL,`renewalTimestamp` INTEGER NOT NULL," +
                "`monthlyCostMinor` INTEGER NOT NULL,`isActive` INTEGER NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL,`updatedTimestamp` INTEGER NOT NULL," +
                "FOREIGN KEY(`simId`) REFERENCES `telecom_sims`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT," +
                "FOREIGN KEY(`packageId`) REFERENCES `telecom_packages`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `opportunities` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "`title` TEXT NOT NULL,`description` TEXT NOT NULL,`type` INTEGER NOT NULL," +
                "`source` TEXT NOT NULL,`sourceUrl` TEXT NOT NULL," +
                "`expectedAmountMinor` INTEGER,`status` INTEGER NOT NULL," +
                "`deadlineTimestamp` INTEGER NOT NULL,`client` TEXT NOT NULL," +
                "`createdTimestamp` INTEGER NOT NULL,`updatedTimestamp` INTEGER NOT NULL)"
        )
        sqlite.execSQL("CREATE INDEX `index_transactions_accountId` ON `transactions` (`accountId`)")
        sqlite.execSQL("CREATE INDEX `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        sqlite.execSQL("CREATE INDEX `index_transactions_transactionTimestamp` ON `transactions` (`transactionTimestamp`)")
        sqlite.execSQL("CREATE INDEX `index_transactions_workItemId` ON `transactions` (`workItemId`)")
        sqlite.execSQL("CREATE INDEX `index_goals_accountId` ON `goals` (`accountId`)")
        sqlite.execSQL("CREATE UNIQUE INDEX `index_budgets_categoryId_monthKey` ON `budgets` (`categoryId`, `monthKey`)")
        sqlite.execSQL("CREATE INDEX `index_budgets_monthKey` ON `budgets` (`monthKey`)")
        sqlite.execSQL("CREATE INDEX `index_work_items_status` ON `work_items` (`status`)")
        sqlite.execSQL("CREATE INDEX `index_telecom_packages_carrier` ON `telecom_packages` (`carrier`)")
        sqlite.execSQL("CREATE INDEX `index_telecom_subscriptions_simId` ON `telecom_subscriptions` (`simId`)")
        sqlite.execSQL("CREATE INDEX `index_telecom_subscriptions_packageId` ON `telecom_subscriptions` (`packageId`)")
        sqlite.execSQL("CREATE INDEX `index_telecom_subscriptions_renewalTimestamp` ON `telecom_subscriptions` (`renewalTimestamp`)")
        sqlite.execSQL("CREATE INDEX `index_opportunities_status` ON `opportunities` (`status`)")
        sqlite.execSQL("CREATE INDEX `index_opportunities_type` ON `opportunities` (`type`)")
        sqlite.execSQL("CREATE INDEX `index_opportunities_deadlineTimestamp` ON `opportunities` (`deadlineTimestamp`)")

        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Wallet', 0, 100000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO accounts (name, type, openingBalanceMinor, isActive, createdTimestamp) VALUES ('Bank', 1, 900000, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Food', 1, 1, 1, 1700000000000)")
        sqlite.execSQL("INSERT INTO categories (name, direction, isActive, isSystem, createdTimestamp) VALUES ('Salary', 0, 1, 0, 1700000000000)")
        sqlite.execSQL("INSERT INTO work_items (title, description, status, expectedAmountMinor, deadlineTimestamp, client, createdTimestamp, updatedTimestamp) VALUES ('Project', 'desc', 0, 100000, 1800000000000, 'Client', 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source, externalRef) VALUES (1, 1, NULL, 5000, 1, 1700000000000, 'Lunch', 1700000000000, 'manual', NULL)")
        sqlite.execSQL("INSERT INTO transactions (accountId, categoryId, workItemId, amountMinor, direction, transactionTimestamp, note, createdTimestamp, source, externalRef) VALUES (2, 2, 1, 250000, 0, 1700000001000, 'Paycheck', 1700000001000, 'IMPORT_FILE', 'TX-77')")
        sqlite.execSQL("INSERT INTO goals (name, targetAmountMinor, accountId, deadlineTimestamp, isActive, isCompleted, createdTimestamp, updatedTimestamp) VALUES ('Save', 500000, 1, 1800000000000, 1, 0, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO budgets (amountMinor, monthKey, categoryId, createdTimestamp, updatedTimestamp) VALUES (50000, '2026-09', 1, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO telecom_sims (label, carrier, phoneNumber, status, notes, createdTimestamp, updatedTimestamp) VALUES ('Personal', 'Nepal Telecom', '', 0, '', 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO telecom_packages (name, carrier, category, priceMinor, period, notes, isActive, createdTimestamp, updatedTimestamp) VALUES ('Data 1GB', 'Nepal Telecom', 'data', 1500, 1, '', 1, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO telecom_subscriptions (simId, packageId, startTimestamp, renewalTimestamp, monthlyCostMinor, isActive, createdTimestamp, updatedTimestamp) VALUES (1, 1, 1700000000000, 1702592000000, 1500, 1, 1700000000000, 1700000000000)")
        sqlite.execSQL("INSERT INTO opportunities (title, description, type, source, sourceUrl, expectedAmountMinor, status, deadlineTimestamp, client, createdTimestamp, updatedTimestamp) VALUES ('Freelance gig', 'Build site', 0, 'Upwork', '', 50000, 0, 1800000000000, 'Acme', 1700000000000, 1700000000000)")
        sqlite.version = 7
        sqlite.close()
    }

    private fun openMigrated(ctx: android.content.Context): ShadowMoneyDatabase =
        Room.databaseBuilder(ctx, ShadowMoneyDatabase::class.java, dbName)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
            )
            .allowMainThreadQueries()
            .build()

    @After
    fun cleanup() {
        appContext().deleteDatabase(dbName)
    }

    @Test
    fun migration7To8_preservesAllExistingData() {
        val ctx = appContext()
        createV7Database(ctx)
        val database = openMigrated(ctx)
        try {
            runBlocking {
                assertEquals(2, database.accountDao().getAll().first().size)
                assertEquals(2, database.categoryDao().getAll().first().size)

                val transactions = database.transactionDao().getAll().first()
                assertEquals(2, transactions.size)
                assertEquals(5_000L, transactions.first { it.id == 1L }.amountMinor)
                assertEquals("manual", transactions.first { it.id == 1L }.source)
                assertNull(transactions.first { it.id == 1L }.externalRef)
                assertEquals("TX-77", transactions.first { it.id == 2L }.externalRef)

                assertEquals(1, database.goalDao().getAllActive().first().size)
                assertEquals(1, database.budgetDao().getByMonthOnce("2026-09").size)
                assertEquals(1, database.workItemDao().observeAll().first().size)
                assertEquals(1, database.telecomDao().observeSims().first().size)
                assertEquals(1, database.telecomDao().observePackages().first().size)
                assertEquals(1, database.telecomDao().observeSubscriptions().first().size)
                val opportunities = database.opportunityDao()
                    .observeOpportunities(null, null, null, null).first()
                assertEquals(1, opportunities.size)
                assertEquals("Freelance gig", opportunities.first().title)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration7To8_createsConnectionTablesEmptyAndQueryable() {
        val ctx = appContext()
        createV7Database(ctx)
        val database = openMigrated(ctx)
        try {
            database.openHelper.readableDatabase
                .query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('financial_connections', 'balance_baselines')",
                    emptyArray()
                )
                .use { cursor ->
                    val tables = mutableSetOf<String>()
                    while (cursor.moveToNext()) tables.add(cursor.getString(0))
                    assertTrue("financial_connections must exist", tables.contains("financial_connections"))
                    assertTrue("balance_baselines must exist", tables.contains("balance_baselines"))
                }

            runBlocking {
                assertTrue(database.connectionDao().getConnectionsOnce().isEmpty())
                assertTrue(database.connectionDao().getBaselinesOnce().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migration7To8_connectionAndBaselineRoundTripWithUniqueProvider() {
        val ctx = appContext()
        createV7Database(ctx)
        val database = openMigrated(ctx)
        try {
            val repository = ConnectionRepository(database.connectionDao())
            runBlocking {
                val first = repository.upsertConnection(
                    FinancialConnection(
                        provider = Provider.SANIMA,
                        status = ConnectionStatus.UNAVAILABLE,
                        capabilities = emptySet(),
                        availabilityNote = "NOT AVAILABLE THROUGH OFFICIAL PUBLIC CONSUMER API",
                        updatedAtMs = 1_000L
                    )
                )
                val second = repository.upsertConnection(
                    FinancialConnection(
                        id = 0L,
                        provider = Provider.SANIMA,
                        status = ConnectionStatus.CONNECTED,
                        capabilities = setOf(ConnectionCapability.BALANCE_READ),
                        availabilityNote = "updated",
                        lastVerifiedAtMs = 2_000L,
                        verifiedBalanceMinor = 75_000L,
                        updatedAtMs = 2_000L
                    )
                )

                val rows = repository.getConnectionsOnce()
                assertEquals(1, rows.size)
                assertEquals(first.id, rows.first().id)
                assertEquals(second.id, rows.first().id)
                assertEquals(ConnectionStatus.CONNECTED, rows.first().status)
                assertEquals(75_000L, rows.first().verifiedBalanceMinor)
                assertEquals(
                    setOf(ConnectionCapability.BALANCE_READ),
                    rows.first().capabilities
                )

                repository.setBaselines(
                    listOf(
                        com.prasbin.shadowmoney.data.connections.BalanceBaseline(
                            provider = Provider.SANIMA,
                            baselineMinor = 75_000L,
                            provenance = Provenance.CONNECTED_VERIFIED,
                            setAtMs = 2_000L
                        )
                    )
                )
                val baselines = repository.getBaselinesOnce()
                assertEquals(1, baselines.size)
                assertEquals(75_000L, baselines.first().baselineMinor)
                assertEquals(Provenance.CONNECTED_VERIFIED, baselines.first().provenance)

                val observed = repository.observeConnections().first()
                assertEquals(1, observed.size)
            }
        } finally {
            database.close()
        }
    }
}
