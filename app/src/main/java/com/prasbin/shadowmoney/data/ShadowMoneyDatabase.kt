package com.prasbin.shadowmoney.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.prasbin.shadowmoney.data.model.Account
import com.prasbin.shadowmoney.data.model.Budget
import com.prasbin.shadowmoney.data.model.Category
import com.prasbin.shadowmoney.data.model.Goal
import com.prasbin.shadowmoney.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [Account::class, Category::class, Transaction::class, Goal::class, Budget::class],
    version = 3,
    exportSchema = true
)
abstract class ShadowMoneyDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun budgetDao(): BudgetDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
