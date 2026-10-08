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
}

data class ReasonCount(val reason: MissReason, val count: Int)

@Dao
interface CycleLogDao {
    @Insert suspend fun insert(log: CycleLogEntity): Long

    @Query("SELECT * FROM cycle_logs WHERE date BETWEEN :from AND :to ORDER BY date")
    fun between(from: String, to: String): Flow<List<CycleLogEntity>>
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
interface ProfileDao {
    @Insert suspend fun insert(profile: ProfileEntity)
    @Update suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = 0")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 0")
    suspend fun get(): ProfileEntity?
}
