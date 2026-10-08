package com.focuslens.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAY_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.US)

class JournalRepository(private val dao: JournalDao) {

    suspend fun save(prompt: String?, text: String, mood: String?) {
        val today = DAY_FORMAT.format(Date())
        dao.insert(
            JournalEntryEntity(
                date = today,
                prompt = prompt,
                text = text,
                mood = mood,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recent(limit: Int = 30) = dao.recentOnce(limit)

    /** Consecutive days ending today (or yesterday, if today's entry isn't written yet) with a journal entry. */
    suspend fun currentStreak(): Int {
        val entries = dao.recentOnce(60).map { it.date }.toSet()
        if (entries.isEmpty()) return 0

        val cal = Calendar.getInstance()
        var streak = 0
        // If today has no entry yet, a streak built through yesterday still counts as "current".
        if (DAY_FORMAT.format(cal.time) !in entries) cal.add(Calendar.DAY_OF_MONTH, -1)

        while (DAY_FORMAT.format(cal.time) in entries) {
            streak++
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return streak
    }

    /** First milestone this streak just crossed (3/7/14/30/60/100), or null if none — for the celebration moment. */
    fun milestoneFor(streak: Int): Int? = listOf(3, 7, 14, 30, 60, 100).firstOrNull { it == streak }
}
