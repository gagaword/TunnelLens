package com.gagaworld.modernsocks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProxyProfileEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ModernSocksDatabase : RoomDatabase() {
    abstract fun proxyProfileDao(): ProxyProfileDao

    companion object {
        fun create(context: Context): ModernSocksDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ModernSocksDatabase::class.java,
                "modernsocks.db",
            ).addMigrations(MIGRATION_1_2).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE proxy_profiles ADD COLUMN appRoutingMode TEXT NOT NULL " +
                        "DEFAULT 'ALL_APPS'",
                )
                db.execSQL(
                    "ALTER TABLE proxy_profiles ADD COLUMN appRoutingPackages TEXT NOT NULL " +
                        "DEFAULT '[]'",
                )
            }
        }
    }
}
