package com.focuslens

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar

data class AppUsage(
    val packageName: String,
    val label: String,
    val millis: Long,
    val launches: Int
)

data class ScreenTime(val screen: String, val millis: Long)

/** Launcher, system UI and similar surfaces. They are not apps the user chose to open. */
/**
 * UsageEvents types that end every open session: 16 = screen off, 17 = lock screen shown,
 * 26 = device shutdown.
 */
private val SCREEN_OFF_TYPES = setOf(16, 17, 26)

fun isSystemSurface(pkg: String): Boolean =
    pkg.contains("launcher") || pkg.contains("systemui") || pkg == "android" ||
        pkg.contains("wellbeing") || pkg.contains("inputmethod")

/** Turns Android's technical screen names into something readable. */
fun friendlyScreen(pkg: String, cls: String): String {
    val c = cls.lowercase()
    val simple = cls.substringAfterLast('.').ifEmpty { "Main screen" }
    return when {
        pkg == "com.whatsapp" && ("voip" in c || "calling" in c) -> "Calls"
        pkg == "com.whatsapp" && c.endsWith(".conversation") -> "Chats"
        pkg == "com.whatsapp" && "homeactivity" in c -> "Chat list"
        pkg == "com.whatsapp" && "mediacomposer" in c -> "Sending photos and videos"
        pkg == "com.whatsapp" && "statusplayback" in c -> "Status"
        pkg == "com.whatsapp" && "mediaview" in c -> "Viewing photos and videos"
        pkg == "com.whatsapp" && "gallerypicker" in c -> "Picking media"
        pkg == "com.whatsapp" && "authentication" in c -> "App lock"
        pkg == "com.instagram.android" && "mainactivity" in c ->
            "Main app (feed, Reels, messages, explore)"
        pkg == "com.instagram.android" && "modal" in c -> "Pop-up screens (comments, share, camera)"
        pkg == "com.instagram.android" && "browser" in c -> "In-app browser"
        "clips" in c -> "Reels link"
        "applovin" in c || "mbridge" in c || "unity3d" in c || "adactivity" in c || ".ads." in c -> "Ads"
        else -> simple
    }
}

enum class Range(val title: String, val approximate: Boolean = false) {
    TODAY("Today"),
    WEEK("Last 7 days"),
    MONTH("Last 30 days", approximate = true),
    YEAR("Last 12 months", approximate = true),
    ALL("All available", approximate = true),
    /** The actual window is whatever the user picked — see loadCustom(). */
    CUSTOM("Custom range", approximate = true)
}

class UsageRepository(private val context: Context) {

    private val usm =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = context.packageManager

    /** True once the user has granted Usage access to this app. */
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun load(range: Range): List<AppUsage> {
        val end = System.currentTimeMillis()
        return when (range) {
            Range.TODAY -> systemTotals(startOfToday(), end)
            Range.WEEK -> systemTotals(startOfToday() - 6L * DAY_MS, end)
            // Android only keeps precise minute-level events for about a week. Older
            // data exists only as monthly (about 6 months back) and yearly (about 2
            // years back) pre-summed buckets, so these views are built from those.
            Range.MONTH -> aggregate(UsageStatsManager.INTERVAL_MONTHLY, end - 30L * DAY_MS, end)
            Range.YEAR -> aggregate(UsageStatsManager.INTERVAL_YEARLY, end - 365L * DAY_MS, end)
            Range.ALL -> aggregate(UsageStatsManager.INTERVAL_YEARLY, 0L, end)
            Range.CUSTOM -> emptyList() // use loadCustom(start, end) directly instead
        }
    }

    /**
     * A user-picked date range. Spans of a week or less use the precise event log (same
     * path as TODAY/WEEK); anything longer falls back to the pre-summed monthly buckets,
     * same accuracy trade-off as MONTH/YEAR above.
     */
    fun loadCustom(startMillis: Long, endMillis: Long, withLaunches: Boolean = true): List<AppUsage> {
        val end = endMillis.coerceAtMost(System.currentTimeMillis())
        val spanDays = (end - startMillis) / DAY_MS
        return if (spanDays <= 7) systemTotals(startMillis, end, withLaunches)
        else aggregate(UsageStatsManager.INTERVAL_MONTHLY, startMillis, end)
    }

    private fun aggregate(interval: Int, start: Long, end: Long): List<AppUsage> {
        val totals = HashMap<String, Long>()
        for (s in usm.queryUsageStats(interval, start, end).orEmpty()) {
            totals[s.packageName] = (totals[s.packageName] ?: 0L) + s.totalTimeInForeground
        }
        return totals
            .filter { (pkg, ms) ->
                ms > 0 && pkg != context.packageName && !isSystemSurface(pkg) && isUserApp(pkg)
            }
            .map { (pkg, ms) -> AppUsage(pkg, labelFor(pkg), ms, 0) }
            .sortedByDescending { it.millis }
    }

    /**
     * Android's own per-app totals for [start, end). These are the figures Digital Wellbeing
     * shows, so FocusLens and Digital Wellbeing agree for the last week. Launch counts come from
     * the event log, since the totals call does not return them.
     */
    @Suppress("DEPRECATION")
    private fun systemTotals(start: Long, end: Long, withLaunches: Boolean = true): List<AppUsage> {
        // The event log is the expensive part; skip it when launch counts are not needed.
        val launches = if (withLaunches) usageBetween(start, end).associate { it.packageName to it.launches } else emptyMap()
        return usm.queryAndAggregateUsageStats(start, end).orEmpty()
            .filter { (pkg, s) ->
                s.totalTimeInForeground > 0 && pkg != context.packageName && !isSystemSurface(pkg) && isUserApp(pkg)
            }
            .map { (pkg, s) -> AppUsage(pkg, labelFor(pkg), s.totalTimeInForeground, launches[pkg] ?: 0) }
            .sortedByDescending { it.millis }
    }

    /**
     * Builds per-app foreground time from the raw event log. This is the same
     * source Digital Wellbeing reads, and it is more accurate than the bucketed
     * queryUsageStats totals because it is not split on bucket boundaries.
     */
    @Suppress("DEPRECATION")
    private fun usageBetween(start: Long, end: Long): List<AppUsage> {
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()

        val resumedAt = HashMap<String, Long>()
        val totals = HashMap<String, Long>()
        val launches = HashMap<String, Int>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    if (!resumedAt.containsKey(pkg)) {
                        resumedAt[pkg] = event.timeStamp
                        launches[pkg] = (launches[pkg] ?: 0) + 1
                    }
                }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = resumedAt.remove(pkg)
                    if (from != null) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - from)
                    }
                }
                in SCREEN_OFF_TYPES -> {
                    // Screen off, lock screen, or shutdown: nothing is in use any more, so every
                    // open session ends here. Without this, an app left "open" with the screen off
                    // (for example Photos) was counted until the end of the window.
                    for ((p, from) in resumedAt) {
                        totals[p] = (totals[p] ?: 0L) + (event.timeStamp - from).coerceAtLeast(0L)
                    }
                    resumedAt.clear()
                }
            }
        }
        // Apps still in the foreground at the end of the window (screen still on).
        for ((pkg, from) in resumedAt) {
            totals[pkg] = (totals[pkg] ?: 0L) + (end - from)
        }

        return totals
            .filter { (pkg, ms) ->
                ms > 0 && pkg != context.packageName && !isSystemSurface(pkg) && isUserApp(pkg)
            }
            .map { (pkg, ms) ->
                AppUsage(pkg, labelFor(pkg), ms, launches[pkg] ?: 0)
            }
            .sortedByDescending { it.millis }
    }

    /**
     * Time per screen inside each app (for WhatsApp this separates calls from chats, for
     * example). Android only records the screen (activity) name, so apps that keep
     * everything on one screen, like Instagram, show up as a single entry.
     */
    fun screenBreakdown(range: Range): Map<String, List<ScreenTime>> {
        val end = System.currentTimeMillis()
        val start = if (range == Range.TODAY) startOfToday() else startOfToday() - 6L * DAY_MS
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        val openAt = HashMap<String, Long>()
        val totals = HashMap<String, HashMap<String, Long>>()

        fun add(pkg: String, cls: String, from: Long, to: Long) {
            if (to <= from) return
            val m = totals.getOrPut(pkg) { HashMap() }
            val name = friendlyScreen(pkg, cls)
            m[name] = (m[name] ?: 0L) + (to - from)
        }

        fun flush(t: Long) {
            for ((k, s) in openAt) add(k.substringBefore('|'), k.substringAfter('|'), s, t)
            openAt.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            val cls = e.className ?: ""
            when (e.eventType) {
                1 -> openAt.putIfAbsent("$pkg|$cls", e.timeStamp)
                2, 23 -> openAt.remove("$pkg|$cls")?.let { add(pkg, cls, it, e.timeStamp) }
                in SCREEN_OFF_TYPES -> flush(e.timeStamp)
            }
        }
        flush(end)
        return totals.mapValues { (_, m) ->
            m.map { ScreenTime(it.key, it.value) }.sortedByDescending { it.millis }
        }
    }

    /**
     * Per-hour-of-day totals (device local time) for the single calendar day [dayStart,
     * dayEnd). A session that crosses an hour boundary is split across both hours by actual
     * overlap, so summing every hour's totals equals the same total [usageBetween] would give
     * for the same window. Only meaningful while Android's raw event log still covers this
     * day (about a week) — same constraint as [usageBetween], which is why
     * UsageHistoryRepository snapshots this before it ages out.
     */
    @Suppress("DEPRECATION")
    fun hourlyBreakdown(dayStart: Long, dayEnd: Long): Map<Int, Map<String, Long>> {
        val events = usm.queryEvents(dayStart, dayEnd)
        val event = UsageEvents.Event()
        val resumedAt = HashMap<String, Long>()
        val totals = HashMap<Int, HashMap<String, Long>>()
        val cal = Calendar.getInstance()

        fun addSplitByHour(pkg: String, from: Long, to: Long) {
            var cursor = from
            while (cursor < to) {
                cal.timeInMillis = cursor
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val hourEnd = cal.timeInMillis + 3_600_000L
                val sliceEnd = minOf(to, hourEnd)
                val bucket = totals.getOrPut(hour) { HashMap() }
                bucket[pkg] = (bucket[pkg] ?: 0L) + (sliceEnd - cursor)
                cursor = sliceEnd
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> resumedAt.putIfAbsent(pkg, event.timeStamp)
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val from = resumedAt.remove(pkg)
                    if (from != null && event.timeStamp > from) addSplitByHour(pkg, from, event.timeStamp)
                }
                in SCREEN_OFF_TYPES -> {
                    for ((p, from) in resumedAt) if (event.timeStamp > from) addSplitByHour(p, from, event.timeStamp)
                    resumedAt.clear()
                }
            }
        }
        for ((pkg, from) in resumedAt) if (dayEnd > from) addSplitByHour(pkg, from, dayEnd)

        return totals.mapValues { (_, m) ->
            m.filter { (pkg, ms) -> ms > 0 && pkg != context.packageName && !isSystemSurface(pkg) && isUserApp(pkg) }
        }.filterValues { it.isNotEmpty() }
    }

    private fun isUserApp(pkg: String): Boolean =
        pm.getLaunchIntentForPackage(pkg) != null

    fun labelFor(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance()
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

fun formatDuration(millis: Long): String {
    val totalMin = millis / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        totalMin > 0 -> "${m}m"
        else -> "<1m"
    }
}

/** Exact hour:minute:second duration, for readings where "about N min" is too coarse. */
fun formatDurationHMS(millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun toCsv(range: Range, rows: List<AppUsage>): String {
    val sb = StringBuilder("range,app,package,minutes,launches\n")
    for (r in rows) {
        val name = r.label.replace("\"", "\"\"")
        sb.append("${range.title},\"$name\",${r.packageName},${r.millis / 60_000},${r.launches}\n")
    }
    return sb.toString()
}
