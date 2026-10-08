package com.focuslens.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One day's accountability record: what was scheduled vs. what actually got a check-in. */
data class DayAccountability(
    val date: String, // yyyyMMdd
    val scheduled: Int,
    val completed: Int,
    val missed: Int
)

/**
 * Turns raw tasks + check-ins into the "keep a tab on accountability" view: a daily
 * schedule-vs-actual record and the current streak, used by Home's streak counter
 * and the Usage/accountability screens.
 */
class AccountabilityRepository(private val taskDao: TaskDao) {
    private fun dayKey(epochMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    /** Daily schedule-vs-actual for the last [days] days, oldest first. */
    suspend fun dailyAccountability(days: Int): List<DayAccountability> {
        val cal = Calendar.getInstance()
        val to = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1))
        val from = cal.timeInMillis

        val tasks = taskDao.betweenOnce(from, to)

        return tasks.groupBy { dayKey(it.scheduledAt) }.map { (date, dayTasks) ->
            val completed = dayTasks.count { it.completedAt != null }
            DayAccountability(
                date = date,
                scheduled = dayTasks.size,
                completed = completed,
                missed = dayTasks.size - completed
            )
        }.sortedBy { it.date }
    }

    /** Consecutive days (ending today) with at least one completed task and zero unresolved misses. */
    fun currentStreak(days: List<DayAccountability>): Int {
        var streak = 0
        for (day in days.sortedByDescending { it.date }) {
            if (day.scheduled > 0 && day.missed == 0 && day.completed > 0) streak++
            else if (day.scheduled > 0) break
            // a day with nothing scheduled doesn't break the streak, but doesn't extend it either
        }
        return streak
    }
}
