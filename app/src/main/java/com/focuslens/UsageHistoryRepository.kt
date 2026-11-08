package com.focuslens

import android.content.Context
import com.focuslens.data.AppDatabase
import com.focuslens.data.DailyUsageEntity
import com.focuslens.data.HourTotal
import com.focuslens.data.HourlyUsageEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * FocusLens's own exact daily usage history, kept in Room so it doesn't degrade once
 * Android's precise event log (about a week) ages into coarser monthly/yearly buckets.
 * This can only protect data going forward from whenever it first runs — it has no way
 * to recover days that are already gone from Android's own records.
 *
 * Deliberately never blends Room and Android data for the same range: a range is read
 * from Room only once every day in it has been snapshotted, otherwise it falls back to
 * UsageRepository's existing Android query untouched. That avoids double-counting a day
 * from both sources at once.
 */
class UsageHistoryRepository(context: Context, private val usage: UsageRepository) {

    private val dao = AppDatabase.get(context).dailyUsageDao()
    private val hourlyDao = AppDatabase.get(context).hourlyUsageDao()
    private val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    /** Snapshots any finished day in the last [daysBack] days that isn't already stored. Call once per app open. */
    suspend fun catchUp(daysBack: Int = 10) {
        val todayStart = startOfDay(System.currentTimeMillis())
        for (back in 1..daysBack) {
            val dayStart = todayStart - back * DAY_MS
            val dayEnd = dayStart + DAY_MS
            val date = fmt.format(Date(dayStart))
            if (dao.forDate(date).isNotEmpty()) continue
            val rows = usage.loadCustom(dayStart, dayEnd)
            if (rows.isNotEmpty()) {
                dao.upsertAll(rows.map { DailyUsageEntity(date, it.packageName, it.millis, it.launches) })
            }
        }
    }

    /** Same idea as [catchUp] but hour-of-day granularity — Android itself never keeps this
     *  for more than about a week, so capturing it before then is the only way an hourly view
     *  can ever cover days further back than that. */
    suspend fun catchUpHourly(daysBack: Int = 7) {
        val todayStart = startOfDay(System.currentTimeMillis())
        for (back in 1..daysBack) {
            val dayStart = todayStart - back * DAY_MS
            val dayEnd = dayStart + DAY_MS
            val date = fmt.format(Date(dayStart))
            if (hourlyDao.hasDate(date) != null) continue
            val breakdown = usage.hourlyBreakdown(dayStart, dayEnd)
            val rows = breakdown.flatMap { (hour, byPkg) -> byPkg.map { (pkg, ms) -> HourlyUsageEntity(date, hour, pkg, ms) } }
            if (rows.isNotEmpty()) hourlyDao.upsertAll(rows)
        }
    }

    /** Today's hour-by-hour totals (0-23), always computed live since today isn't a
     *  "finished" day yet and so is never snapshotted. */
    suspend fun hourlyTotalsForToday(): List<HourTotal> {
        val todayStart = startOfDay(System.currentTimeMillis())
        val breakdown = usage.hourlyBreakdown(todayStart, System.currentTimeMillis())
        return (0..23).map { h -> HourTotal(h, breakdown[h]?.values?.sum() ?: 0L) }
    }

    /** Hour-of-day totals summed across the last [days] finished days (from snapshotted Room
     *  data) plus today so far (computed live) — an approximate "when do I usually use my
     *  phone" read, not an exact one. */
    suspend fun hourlyTotalsRecent(days: Int): Map<Int, Long> {
        val todayStart = startOfDay(System.currentTimeMillis())
        val from = fmt.format(Date(todayStart - days * DAY_MS))
        val to = fmt.format(Date(todayStart - DAY_MS))
        val totals = HashMap<Int, Long>()
        hourlyDao.hourTotals(from, to).forEach { totals[it.hour] = (totals[it.hour] ?: 0L) + it.millis }
        hourlyTotalsForToday().forEach { totals[it.hour] = (totals[it.hour] ?: 0L) + it.millis }
        return totals
    }

    /**
     * [range]'s usage if every day in it has a snapshot (exact, from Room), otherwise null
     * so the caller falls back to [UsageRepository]'s own Android-backed query unchanged.
     */
    suspend fun tryExact(fromMillis: Long, toMillis: Long): List<AppUsage>? {
        val from = fmt.format(Date(fromMillis))
        val to = fmt.format(Date(toMillis - 1))
        val expectedDays = ((toMillis - fromMillis) / DAY_MS).toInt().coerceAtLeast(1)
        val coveredDays = dao.datesBetween(from, to).size
        if (coveredDays < expectedDays) return null

        return dao.totals(from, to)
            .filter { it.millis > 0 }
            .map { AppUsage(it.pkg, usage.labelFor(it.pkg), it.millis, it.launches) }
            .sortedByDescending { it.millis }
    }

    /** Days FocusLens has stored from fromMillis up to toMillis, and how many days that range spans. */
    suspend fun coverage(fromMillis: Long, toMillis: Long): Pair<Int, Int> {
        val from = fmt.format(Date(fromMillis))
        val to = fmt.format(Date(toMillis - 1))
        val expected = ((toMillis - fromMillis) / DAY_MS).toInt().coerceAtLeast(1)
        return dao.datesBetween(from, to).size to expected
    }

    private fun startOfDay(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
