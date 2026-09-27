package com.prasbin.shadowmoney.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.Opportunity
import com.prasbin.shadowmoney.data.model.TelecomPackage
import com.prasbin.shadowmoney.data.model.TelecomSim
import com.prasbin.shadowmoney.data.model.TelecomSubscription
import com.prasbin.shadowmoney.data.model.Transaction
import com.prasbin.shadowmoney.data.model.WorkItem
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [Account::class, Category::class, Transaction::class, Goal::class, Budget::class, WorkItem::class, TelecomSim::class, TelecomPackage::class, TelecomSubscription::class, Opportunity::class],
    version = 6,
    exportSchema = true
)
abstract class ShadowMoneyDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun budgetDao(): BudgetDao
    abstract fun workItemDao(): WorkItemDao
    abstract fun telecomDao(): TelecomDao
    abstract fun opportunityDao(): OpportunityDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowMoneyDatabase? = null

        fun getInstance(appContext: Context): ShadowMoneyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    appContext.applicationContext,
                    ShadowMoneyDatabase::class.java,
                    "shadow_money_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS placeholder")
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` INTEGER NOT NULL,
                `openingBalanceMinor` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `direction` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `isSystem` INTEGER NOT NULL,
                `createdTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        database.execSQL("""
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
        database.execSQL("""
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
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_accountId` ON `transactions` (`accountId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_categoryId` ON `transactions` (`categoryId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionTimestamp` ON `transactions` (`transactionTimestamp`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_accountId` ON `goals` (`accountId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
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
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_categoryId_monthKey` ON `budgets` (`categoryId`, `monthKey`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_monthKey` ON `budgets` (`monthKey`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
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
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_work_items_status` ON `work_items` (`status`)")
        database.execSQL("ALTER TABLE `transactions` ADD COLUMN `workItemId` INTEGER DEFAULT NULL REFERENCES `work_items`(`id`) ON DELETE SET NULL")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_workItemId` ON `transactions` (`workItemId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
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
        database.execSQL("""
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
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_telecom_packages_carrier` ON `telecom_packages` (`carrier`)")
        database.execSQL("""
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
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_simId` ON `telecom_subscriptions` (`simId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_packageId` ON `telecom_subscriptions` (`packageId`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_telecom_subscriptions_renewalTimestamp` ON `telecom_subscriptions` (`renewalTimestamp`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS `opportunities` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `type` INTEGER NOT NULL,
                `source` TEXT NOT NULL,
                `sourceUrl` TEXT NOT NULL,
                `expectedAmountMinor` INTEGER,
                `status` INTEGER NOT NULL,
                `deadlineTimestamp` INTEGER NOT NULL,
                `client` TEXT NOT NULL,
                `createdTimestamp` INTEGER NOT NULL,
                `updatedTimestamp` INTEGER NOT NULL
            )
        """.trimIndent())
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_opportunities_status` ON `opportunities` (`status`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_opportunities_type` ON `opportunities` (`type`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_opportunities_deadlineTimestamp` ON `opportunities` (`deadlineTimestamp`)")
    }
}
