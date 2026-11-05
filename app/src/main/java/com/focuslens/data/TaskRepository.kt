package com.focuslens.data

import java.util.Calendar

data class WeeklyReflection(
    val completed: Int,
    val missed: Int,
    val topMissReason: MissReason?,
    val suggestion: String?
)

private val MISS_SUGGESTIONS = mapOf(
    MissReason.TIRED to "Tired came up most this week — try scheduling your hardest task earlier in the day, before you're worn down.",
    MissReason.DISTRACTED to "Distraction was the top reason this week — consider raising Focus limits on the apps pulling you away.",
    MissReason.TOO_BIG to "Tasks feeling too big came up most — try splitting your next few into smaller first steps.",
    MissReason.FORGOT to "Forgetting was the top reason this week — turn on task reminders in Settings if they're not already on.",
    MissReason.OTHER to "Something else got in the way most this week — worth a look at what's actually competing for your time."
)

class TaskRepository(private val taskDao: TaskDao, private val checkInDao: CheckInDao) {

    suspend fun addTask(
        title: String,
        type: TaskType,
        scheduledAt: Long,
        recallNote: String?,
        calendarEventId: Long? = null
    ): Long =
        taskDao.insert(
            TaskEntity(
                title = title,
                type = type,
                scheduledAt = scheduledAt,
                recallNote = recallNote,
                calendarEventId = calendarEventId
            )
        )

    suspend fun markComplete(task: TaskEntity) {
        taskDao.update(task.copy(completedAt = System.currentTimeMillis()))
        checkInDao.insert(
            CheckInEntity(
                taskId = task.id,
                completed = true,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markIncomplete(task: TaskEntity, reason: MissReason?, note: String?) {
        taskDao.update(task.copy(completedAt = null))
        checkInDao.insert(
            CheckInEntity(
                taskId = task.id,
                completed = false,
                reason = reason,
                note = note,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTask(id: Long) = taskDao.delete(id)

    suspend fun weeklyReflection(): WeeklyReflection {
        val sevenDaysAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -7) }.timeInMillis
        val completed = checkInDao.completedCountSince(sevenDaysAgo)
        val missed = checkInDao.missedCountSince(sevenDaysAgo)
        val reasons = checkInDao.missReasonCountsSince(sevenDaysAgo)
        val top = reasons.maxByOrNull { it.count }?.reason
        return WeeklyReflection(
            completed = completed,
            missed = missed,
            topMissReason = top,
            suggestion = top?.let { MISS_SUGGESTIONS[it] }
        )
    }
}
