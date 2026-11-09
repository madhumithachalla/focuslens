package com.focuslens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Usage history lives in its own database file, separate from AppDatabase on purpose.
 * AppDatabase deletes all its tables on any schema change (see its builder), which wiped
 * history on every update that touched another table. This database has no destructive
 * fallback: a future schema change must come with an explicit migration, so the history
 * can't be silently thrown away.
 */
@Database(
    entities = [DailyUsageEntity::class, HourlyUsageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun hourlyUsageDao(): HourlyUsageDao

    companion object {
        @Volatile private var instance: UsageDatabase? = null

        fun get(context: Context): UsageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                UsageDatabase::class.java,
                "focuslens_usage.db"
            ).build().also { instance = it }
        }
    }
}
