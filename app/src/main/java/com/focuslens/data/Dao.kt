package com.focuslens.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    fun between(from: Long, to: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun betweenOnce(from: Long, to: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE completedAt IS NULL AND scheduledAt < :now ORDER BY scheduledAt")
    fun overdue(now: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY scheduledAt DESC")
    fun all(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CheckInDao {
    @Insert suspend fun insert(checkIn: CheckInEntity): Long
    @Update suspend fun update(checkIn: CheckInEntity)

    @Query("SELECT * FROM check_ins WHERE taskId = :taskId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestFor(taskId: Long): CheckInEntity?

    /** Every missed check-in grouped by reason, used to tune which suggestion actually helps this user. */
    @Query("SELECT reason, COUNT(*) as count FROM check_ins WHERE completed = 0 AND reason IS NOT NULL GROUP BY reason")
    suspend fun missReasonCounts(): List<ReasonCount>

    @Query("SELECT reason, COUNT(*) as count FROM check_ins WHERE completed = 0 AND reason IS NOT NULL AND createdAt >= :since GROUP BY reason")
    suspend fun missReasonCountsSince(since: Long): List<ReasonCount>

    @Query("SELECT COUNT(*) FROM check_ins WHERE completed = 1 AND createdAt >= :since")
    suspend fun completedCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM check_ins WHERE completed = 0 AND createdAt >= :since")
    suspend fun missedCountSince(since: Long): Int
}

data class ReasonCount(val reason: MissReason, val count: Int)

@Dao
interface CycleLogDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(log: CycleLogEntity)

    @Query("SELECT * FROM cycle_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    fun between(from: String, to: String): Flow<List<CycleLogEntity>>

    @Query("SELECT * FROM cycle_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun betweenOnce(from: String, to: String): List<CycleLogEntity>

    @Query("SELECT * FROM cycle_logs WHERE mode = 'MENSTRUAL' AND periodStart = 1 ORDER BY date DESC LIMIT :limit")
    suspend fun recentPeriodStarts(limit: Int): List<CycleLogEntity>

    @Query("SELECT * FROM cycle_logs WHERE date = :date AND mode = :mode LIMIT 1")
    suspend fun forDateAndMode(date: String, mode: CycleMode): CycleLogEntity?
}

@Dao
interface MindfulnessDao {
    @Insert suspend fun insert(session: MindfulnessSessionEntity): Long

    @Query("SELECT * FROM mindfulness_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MindfulnessSessionEntity>>
}

@Dao
interface WearableDao {
    @Insert suspend fun insertAll(samples: List<WearableSampleEntity>)

    @Query("SELECT * FROM wearable_samples WHERE metric = :metric AND recordedAt BETWEEN :from AND :to ORDER BY recordedAt")
    fun metricBetween(metric: String, from: Long, to: Long): Flow<List<WearableSampleEntity>>
}

@Dao
interface JournalDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    suspend fun forDate(date: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries ORDER BY date DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY date DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<JournalEntryEntity>

    @Query("SELECT COUNT(*) FROM journal_entries")
    suspend fun totalCount(): Int
}

data class PkgTotal(val pkg: String, val millis: Long, val launches: Int)

@Dao
interface DailyUsageDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DailyUsageEntity>)

    @Query("SELECT * FROM daily_usage WHERE date = :date")
    suspend fun forDate(date: String): List<DailyUsageEntity>

    /** Every date that has at least one snapshotted row, for checking full coverage of a range. */
    @Query("SELECT DISTINCT date FROM daily_usage WHERE date BETWEEN :from AND :to")
    suspend fun datesBetween(from: String, to: String): List<String>

    @Query(
        "SELECT pkg, SUM(millis) as millis, SUM(launches) as launches FROM daily_usage " +
            "WHERE date BETWEEN :from AND :to GROUP BY pkg"
    )
    suspend fun totals(from: String, to: String): List<PkgTotal>
}

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity): Long
    @Query("DELETE FROM people WHERE id = :id") suspend fun delete(id: Long)

    @Query("SELECT * FROM people ORDER BY name")
    fun all(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY name")
    suspend fun allOnce(): List<PersonEntity>
}

@Dao
interface BirthdayWishDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(wish: BirthdayWishEntity)

    @Query("SELECT * FROM birthday_wishes WHERE year = :year")
    suspend fun forYear(year: Int): List<BirthdayWishEntity>
}

@Dao
interface ProfileDao {
    @Insert suspend fun insert(profile: ProfileEntity)
    @Update suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?
}
