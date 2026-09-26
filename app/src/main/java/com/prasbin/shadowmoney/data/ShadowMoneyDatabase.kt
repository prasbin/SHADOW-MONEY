package com.prasbin.shadowmoney.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [com.prasbin.shadowmoney.data.model.PlaceholderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ShadowMoneyDatabase : RoomDatabase() {

    abstract fun shadowMoneyDao(): ShadowMoneyDao

    companion object {
        @Volatile
        private var INSTANCE: ShadowMoneyDatabase? = null

        fun getInstance(appContext: android.content.Context): ShadowMoneyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    appContext.applicationContext,
                    ShadowMoneyDatabase::class.java,
                    "shadow_money_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
