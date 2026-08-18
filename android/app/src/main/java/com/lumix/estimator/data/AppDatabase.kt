package com.lumix.estimator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * "Save location. Reload project. Confirm location and polygon remain" (2026-08-18): [SiteEntity]
 * added, version 1 -> 2. No explicit [androidx.room.migration.Migration] is written — this is a
 * pre-release app with no installed base to preserve (the same "placeholder data, not real
 * client-facing state yet" status every other part of this project is still in) — `.fallbackToDestructiveMigration()`
 * below means an installer upgrading from the v1 schema loses their existing saved QUOTES too,
 * not just gains site storage. Worth knowing before shipping this build over an existing install
 * with real saved quotes on it.
 */
@Database(entities = [QuoteEntity::class, SiteEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun siteDao(): SiteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumix-quotes.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
