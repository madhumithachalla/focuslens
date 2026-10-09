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
        BirthdayWishEntity::class
    ],
    version = 7,
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

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
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
}
