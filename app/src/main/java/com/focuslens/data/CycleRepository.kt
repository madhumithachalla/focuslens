package com.focuslens.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAY_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.US)

/**
 * An estimate from the user's OWN logged period-start dates, nothing more. This is a
 * plain rolling average of past cycle lengths — not a clinically validated model like
 * Clue's, which draws on millions of aggregated, reviewed cycles. Never present this as
 * medical-grade; the UI must keep the "estimate from your own data" framing.
 */
data class CyclePrediction(
    val averageCycleLengthDays: Int,
    val predictedNextStart: String, // yyyyMMdd
    val currentCycleDay: Int,
    /** True once there are enough logged cycles (3+) to call this a real rolling average. */
    val isConfident: Boolean
)

class CycleRepository(private val dao: CycleLogDao) {

    private fun parse(date: String): Calendar =
        Calendar.getInstance().apply { time = DAY_FORMAT.parse(date) ?: Date() }

    private fun daysBetween(from: String, to: String): Int {
        val a = parse(from).timeInMillis
        val b = parse(to).timeInMillis
        return ((b - a) / 86_400_000L).toInt()
    }

    private fun addDays(date: String, days: Int): String {
        val cal = parse(date)
        cal.add(Calendar.DAY_OF_MONTH, days)
        return DAY_FORMAT.format(cal.time)
    }

    /**
     * Rolling average of the gaps between the last few logged period starts. Returns null
     * when there's no logged history at all — the UI should fall back to "log a period
     * start to see an estimate" rather than inventing a number.
     */
    suspend fun predictNextCycle(): CyclePrediction? {
        // Most recent first; need at least 2 starts to compute even one gap.
        val starts = dao.recentPeriodStarts(limit = 6).map { it.date }.sortedDescending()
        if (starts.size < 2) return null

        val gaps = starts.zipWithNext { newer, older -> daysBetween(older, newer) }
            .filter { it in 15..60 } // guards against a mis-tapped date wrecking the average
        if (gaps.isEmpty()) return null

        val avg = gaps.average().let { Math.round(it).toInt() }
        val lastStart = starts.first()
        val predicted = addDays(lastStart, avg)
        val today = DAY_FORMAT.format(Date())
        val currentDay = (daysBetween(lastStart, today) + 1).coerceAtLeast(1)

        return CyclePrediction(
            averageCycleLengthDays = avg,
            predictedNextStart = predicted,
            currentCycleDay = currentDay,
            isConfident = gaps.size >= 3
        )
    }

    suspend fun logToday(mode: CycleMode, periodStart: Boolean, energyLevel: Int?, moodTags: String?) {
        val today = DAY_FORMAT.format(Date())
        val existing = dao.forDate(today)
        dao.insert(
            CycleLogEntity(
                id = existing?.id ?: 0,
                mode = mode,
                date = today,
                energyLevel = energyLevel ?: existing?.energyLevel,
                moodTags = moodTags ?: existing?.moodTags,
                symptomTags = existing?.symptomTags,
                periodStart = periodStart || (existing?.periodStart ?: false)
            )
        )
    }
}
