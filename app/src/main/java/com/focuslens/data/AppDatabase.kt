package com.focuslens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun toTaskType(v: String) = TaskType.valueOf(v)
    @TypeConverter fun fromTaskType(v: TaskType) = v.name

    @TypeConverter fun toMissReason(v: String?) = v?.let { MissReason.valueOf(it) }
    @TypeConverter fun fromMissReason(v: MissReason?) = v?.name

    @TypeConverter fun toCycleMode(v: String) = CycleMode.valueOf(v)
    @TypeConverter fun fromCycleMode(v: CycleMode) = v.name

    @TypeConverter fun toMindfulnessType(v: String) = MindfulnessType.valueOf(v)
    @TypeConverter fun fromMindfulnessType(v: MindfulnessType) = v.name

    @TypeConverter fun toAgeBracket(v: String) = AgeBracket.valueOf(v)
    @TypeConverter fun fromAgeBracket(v: AgeBracket) = v.name
}

@Database(
    entities = [
        TaskEntity::class,
        CheckInEntity::class,
        CycleLogEntity::class,
        MindfulnessSessionEntity::class,
        WearableSampleEntity::class,
        ProfileEntity::class,
        JournalEntryEntity::class,
        DailyUsageEntity::class,
        PersonEntity::class,
        BirthdayWishEntity::class,
        HourlyUsageEntity::class,
        AlarmEntity::class,
        WeeklyCheckInEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun checkInDao(): CheckInDao
    abstract fun cycleLogDao(): CycleLogDao
    abstract fun mindfulnessDao(): MindfulnessDao
    abstract fun wearableDao(): WearableDao
    abstract fun profileDao(): ProfileDao
    abstract fun journalDao(): JournalDao
    abstract fun dailyUsageDao(): DailyUsageDao
    abstract fun personDao(): PersonDao
    abstract fun birthdayWishDao(): BirthdayWishDao
    abstract fun hourlyUsageDao(): HourlyUsageDao
    abstract fun alarmDao(): AlarmDao
    abstract fun weeklyCheckInDao(): WeeklyCheckInDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** Schema version this code expects; keep in step with @Database(version). */
        const val SCHEMA_VERSION = 10

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: run {
                keepCopyIfSchemaWillChange(context)
                buildDatabase(context)
            }
        }

        /**
         * Before a version change can delete anything, copy the existing database (and its
         * write-ahead files) into the app's private backups folder. This only copies; nothing
         * is restored automatically, and the copy is listed on the App updates page where the
         * person can keep it or delete it.
         */
        private fun keepCopyIfSchemaWillChange(context: Context) {
            try {
                val db = context.getDatabasePath("focuslens.db")
                if (!db.exists()) return
                val version = android.database.sqlite.SQLiteDatabase
                    .openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
                    .use { it.version }
                if (version == SCHEMA_VERSION) return
                val dir = java.io.File(context.filesDir, "backups").apply { mkdirs() }
                val copy = java.io.File(dir, "focuslens_schema_v$version.db")
                if (!copy.exists()) {
                    db.copyTo(copy)
                    for (suffix in listOf("-wal", "-shm")) {
                        val side = java.io.File(db.path + suffix)
                        if (side.exists()) side.copyTo(java.io.File(copy.path + suffix), overwrite = true)
                    }
                }
                context.getSharedPreferences("focus", Context.MODE_PRIVATE)
                    .edit().putInt("backup_kept_version", version).apply()
            } catch (e: Exception) {
                // Best effort: if the copy fails, the database still opens as before.
            }
        }

        private fun buildDatabase(context: Context): AppDatabase = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "focuslens.db"
            )
                // Pre-release app, no real user data to preserve yet — simplest path
                // through schema changes until there's something worth migrating.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
    }
}
