package com.focuslens

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(val events: Int, val sessions: Int, val days: Int, val apps: Int)

/**
 * Collects everything Android lets a normal app read about phone usage and writes it
 * as a ZIP of CSV files. Needs only the Usage access permission.
 */
class DataExporter(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = context.packageManager
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val labels = HashMap<String, String>()

    private class Ev(val t: Long, val type: Int, val pkg: String, val cls: String, val bucket: Int)
    private class AppSession(val pkg: String, val start: Long, val end: Long)
    private class ActSession(val pkg: String, val cls: String, val start: Long, val end: Long)
    private class DayAgg {
        var screenOnMs = 0L
        var screenOns = 0
        var unlocks = 0
        var keyguardShown = 0
        var appMs = 0L
        var userAppMs = 0L
        var sessions = 0
        var first = Long.MAX_VALUE
        var last = 0L
        val apps = HashSet<String>()
    }

    // ---------- small helpers ----------

    private fun ts(ms: Long) = if (ms <= 0L) "" else iso.format(Date(ms))
    private fun day(ms: Long) = dayFmt.format(Date(ms))

    private fun label(pkg: String): String = labels.getOrPut(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    private fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    private fun BufferedWriter.row(vararg cols: Any?) {
        write(cols.joinToString(",") { esc(it) })
        write("\n")
    }

    private fun ZipOutputStream.file(name: String, block: BufferedWriter.() -> Unit) {
        putNextEntry(ZipEntry(name))
        val w = BufferedWriter(OutputStreamWriter(this, Charsets.UTF_8))
        w.block()
        w.flush()
        closeEntry()
    }

    private fun mins(ms: Long) = String.format(Locale.US, "%.2f", ms / 60000.0)

    /** Splits [start, end) at every hour boundary. */
    private fun forEachHour(start: Long, end: Long, f: (day: String, hour: Int, ms: Long) -> Unit) {
        val cal = Calendar.getInstance()
        var cur = start
        while (cur < end) {
            cal.timeInMillis = cur
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val next = minOf(cal.timeInMillis, end)
            if (next <= cur) break
            f(day(cur), hour, next - cur)
            cur = next
        }
    }

    /** Splits [start, end) at every midnight. */
    private fun forEachDay(start: Long, end: Long, f: (day: String, ms: Long) -> Unit) {
        val cal = Calendar.getInstance()
        var cur = start
        while (cur < end) {
            cal.timeInMillis = cur
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            val next = minOf(cal.timeInMillis, end)
            if (next <= cur) break
            f(day(cur), next - cur)
            cur = next
        }
    }

    /** Launcher, system UI and similar surfaces. They are not "apps you use". */
    private val systemSurfaces = setOf(
        "com.google.android.apps.nexuslauncher", "com.android.launcher3", "com.android.systemui",
        "android", "com.google.android.apps.wellbeing", "com.android.settings.intelligence"
    )
    private fun isSystemSurface(pkg: String) = pkg in systemSurfaces

    private val eventNames = mapOf(
        1 to "ACTIVITY_RESUMED", 2 to "ACTIVITY_PAUSED", 3 to "END_OF_DAY",
        4 to "CONTINUE_PREVIOUS_DAY", 5 to "CONFIGURATION_CHANGE", 6 to "SYSTEM_INTERACTION",
        7 to "USER_INTERACTION", 8 to "SHORTCUT_INVOCATION", 9 to "CHOOSER_ACTION",
        10 to "NOTIFICATION_SEEN", 11 to "STANDBY_BUCKET_CHANGED",
        12 to "NOTIFICATION_INTERRUPTION", 13 to "SLICE_PINNED_PRIV", 14 to "SLICE_PINNED",
        15 to "SCREEN_INTERACTIVE", 16 to "SCREEN_NON_INTERACTIVE", 17 to "KEYGUARD_SHOWN",
        18 to "KEYGUARD_HIDDEN", 19 to "FOREGROUND_SERVICE_START",
        20 to "FOREGROUND_SERVICE_STOP", 21 to "CONTINUING_FOREGROUND_SERVICE",
        22 to "ROLLOVER_FOREGROUND_SERVICE", 23 to "ACTIVITY_STOPPED",
        24 to "ACTIVITY_DESTROYED", 25 to "FLUSH_TO_DISK", 26 to "DEVICE_SHUTDOWN",
        27 to "DEVICE_STARTUP", 28 to "USER_UNLOCKED", 29 to "USER_STOPPED",
        30 to "LOCUS_ID_SET", 31 to "APP_COMPONENT_USED"
    )

    // ---------- main entry ----------

    fun writeZip(out: OutputStream, lookbackDays: Int = 365): ExportResult {
        val now = System.currentTimeMillis()
        val from = now - lookbackDays * 86_400_000L

        // 1. Read the raw event log once.
        val events = ArrayList<Ev>()
        val usageEvents = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(e)
            val bucket = if (Build.VERSION.SDK_INT >= 28 && e.eventType == 11) e.appStandbyBucket else 0
            events.add(Ev(e.timeStamp, e.eventType, e.packageName ?: "", e.className ?: "", bucket))
        }

        // 2. Turn events into sessions (app level and activity level) and screen intervals.
        val open = HashMap<String, HashMap<String, Long>>()
        val appStart = HashMap<String, Long>()
        val rawSessions = ArrayList<AppSession>()
        val actSessions = ArrayList<ActSession>()
        var lastEvT = 0L
        val screenIntervals = ArrayList<LongArray>()
        var screenOnAt = -1L

        fun closePkg(pkg: String, t: Long) {
            open.remove(pkg)?.forEach { (cls, s) -> if (t > s) actSessions.add(ActSession(pkg, cls, s, t)) }
            appStart.remove(pkg)?.let { if (t > it) rawSessions.add(AppSession(pkg, it, t)) }
        }

        for (ev in events) {
            when (ev.type) {
                1 -> {
                    val m = open.getOrPut(ev.pkg) { HashMap() }
                    if (m.isEmpty()) appStart[ev.pkg] = ev.t
                    m.putIfAbsent(ev.cls, ev.t)
                }
                2, 23 -> {
                    val m = open[ev.pkg]
                    if (m != null) {
                        val s = m.remove(ev.cls)
                        if (s != null && ev.t > s) actSessions.add(ActSession(ev.pkg, ev.cls, s, ev.t))
                        if (m.isEmpty()) {
                            open.remove(ev.pkg)
                            appStart.remove(ev.pkg)?.let { if (ev.t > it) rawSessions.add(AppSession(ev.pkg, it, ev.t)) }
                        }
                    }
                }
                15 -> {
                    // Android sometimes logs "screen on" twice with no "screen off" between.
                    // Close the earlier interval at the last event seen instead of letting
                    // it run on and swallow the gap.
                    if (screenOnAt >= 0) {
                        val endAt = maxOf(screenOnAt, lastEvT)
                        if (endAt > screenOnAt) screenIntervals.add(longArrayOf(screenOnAt, endAt))
                    }
                    screenOnAt = ev.t
                }
                16, 26 -> {
                    if (screenOnAt >= 0 && ev.t > screenOnAt) screenIntervals.add(longArrayOf(screenOnAt, ev.t))
                    screenOnAt = -1L
                    for (pkg in open.keys.toList()) closePkg(pkg, ev.t)
                }
            }
            lastEvT = ev.t
        }
        if (screenOnAt >= 0) screenIntervals.add(longArrayOf(screenOnAt, now))
        for (pkg in open.keys.toList()) closePkg(pkg, now)

        // Moving between screens inside one app (or a launcher flicker) leaves tiny gaps.
        // Join sessions of the same app that are at most MERGE_GAP_MS apart.
        val appSessions = ArrayList<AppSession>()
        for ((_, list) in rawSessions.groupBy { it.pkg }) {
            val sorted = list.sortedBy { it.start }
            var cur = sorted[0]
            for (i in 1 until sorted.size) {
                val n = sorted[i]
                cur = if (n.start - cur.end <= MERGE_GAP_MS) {
                    AppSession(cur.pkg, cur.start, maxOf(cur.end, n.end))
                } else {
                    appSessions.add(cur)
                    n
                }
            }
            appSessions.add(cur)
        }

        // 3. Aggregate per day, hour and app.
        val days = java.util.TreeMap<String, DayAgg>()
        fun agg(d: String) = days.getOrPut(d) { DayAgg() }

        for (iv in screenIntervals) forEachDay(iv[0], iv[1]) { d, ms -> agg(d).screenOnMs += ms }
        for (ev in events) {
            when (ev.type) {
                15 -> agg(day(ev.t)).screenOns++
                17 -> agg(day(ev.t)).keyguardShown++
                18 -> agg(day(ev.t)).unlocks++
            }
        }
        val hourly = java.util.TreeMap<String, Long>()          // day|hour|pkg
        val dailyApp = java.util.TreeMap<String, LongArray>()   // day|pkg -> [ms, sessions]
        for (s in appSessions) {
            forEachDay(s.start, s.end) { d, ms ->
                val a = agg(d)
                a.appMs += ms
                if (!isSystemSurface(s.pkg)) a.userAppMs += ms
                a.apps.add(s.pkg)
                val da = dailyApp.getOrPut("$d|${s.pkg}") { LongArray(2) }
                da[0] += ms
            }
            val startDay = agg(day(s.start))
            startDay.sessions++
            startDay.first = minOf(startDay.first, s.start)
            val endDay = agg(day(s.end - 1))
            endDay.last = maxOf(endDay.last, s.end)
            dailyApp.getOrPut("${day(s.start)}|${s.pkg}") { LongArray(2) }[1]++
            forEachHour(s.start, s.end) { d, h, ms ->
                val key = String.format(Locale.US, "%s|%02d|%s", d, h, s.pkg)
                hourly[key] = (hourly[key] ?: 0L) + ms
            }
        }

        val installed = try {
            pm.getInstalledPackages(0)
        } catch (ex: Exception) {
            emptyList<PackageInfo>()
        }

        ZipOutputStream(out).use { zip ->
            zip.file("README.txt") { write(readme(now, from, events.size)) }

            zip.file("device_info.csv") { deviceInfo(this, now, events) }

            zip.file("daily_summary.csv") {
                row("date", "screen_on_minutes", "screen_on_count", "unlocks", "keyguard_shown_count",
                    "app_foreground_minutes", "app_minutes_excluding_launcher_and_system",
                    "app_sessions", "distinct_apps_used",
                    "first_app_use", "last_app_use")
                for ((d, a) in days) {
                    row(d, mins(a.screenOnMs), a.screenOns, a.unlocks, a.keyguardShown, mins(a.appMs),
                        mins(a.userAppMs),
                        a.sessions, a.apps.size,
                        if (a.first == Long.MAX_VALUE) "" else ts(a.first), ts(a.last))
                }
            }

            zip.file("daily_app_usage.csv") {
                row("date", "package", "app", "foreground_minutes", "foreground_ms", "sessions",
                    "is_system_surface")
                for ((k, v) in dailyApp) {
                    val (d, pkg) = k.split("|", limit = 2)
                    row(d, pkg, label(pkg), mins(v[0]), v[0], v[1], isSystemSurface(pkg))
                }
            }

            zip.file("hourly_usage.csv") {
                row("date", "hour", "package", "app", "foreground_minutes", "foreground_ms")
                for ((k, ms) in hourly) {
                    val p = k.split("|", limit = 3)
                    row(p[0], p[1].toInt(), p[2], label(p[2]), mins(ms), ms)
                }
            }

            zip.file("app_sessions.csv") {
                row("package", "app", "start", "end", "duration_seconds", "is_system_surface",
                    "start_epoch_ms", "end_epoch_ms")
                for (s in appSessions.sortedBy { it.start }) {
                    row(s.pkg, label(s.pkg), ts(s.start), ts(s.end), (s.end - s.start) / 1000,
                        isSystemSurface(s.pkg), s.start, s.end)
                }
            }

            zip.file("activity_sessions.csv") {
                row("package", "app", "screen_name", "activity_class", "start", "end", "duration_seconds")
                for (s in actSessions.sortedBy { it.start }) {
                    row(s.pkg, label(s.pkg), friendlyScreen(s.pkg, s.cls), s.cls,
                        ts(s.start), ts(s.end), (s.end - s.start) / 1000)
                }
            }

            // Time inside Instagram and YouTube by section (Reels, Feed, Shorts ...),
            // measured by the accessibility service since it was switched on.
            val focusPrefs = FocusPrefs(context)
            zip.file("in_app_sections.csv") {
                row("date", "package", "app", "section", "minutes")
                for (r in focusPrefs.sectionRows()) {
                    val d = r.date
                    val pretty = if (d.length == 8) "${d.substring(0, 4)}-${d.substring(4, 6)}-${d.substring(6)}" else d
                    row(pretty, r.pkg, label(r.pkg), r.section, mins(r.ms))
                }
            }

            // Element names the section detector saw. Used to fix the matching rules if
            // an Instagram or YouTube update changes how their screens are built.
            zip.file("ui_debug_ids.csv") {
                row("package", "element_name")
                for ((pkg, set) in focusPrefs.debugIds()) {
                    for (id in set.sorted()) row(pkg, id)
                }
            }

            zip.file("screen_sessions.csv") {
                row("screen_on", "screen_off", "duration_seconds")
                for (iv in screenIntervals) row(ts(iv[0]), ts(iv[1]), (iv[1] - iv[0]) / 1000)
            }

            zip.file("raw_events.csv") {
                row("time", "epoch_ms", "event_id", "event_type", "package", "app", "class", "standby_bucket")
                for (ev in events) {
                    row(ts(ev.t), ev.t, ev.type, eventNames[ev.type] ?: "UNKNOWN",
                        ev.pkg, if (ev.pkg.isEmpty()) "" else label(ev.pkg), ev.cls,
                        if (ev.type == 11) ev.bucket else "")
                }
            }

            zip.file("system_usage_stats.csv") {
                row("interval", "package", "app", "bucket_start", "bucket_end", "last_time_used",
                    "total_foreground_minutes", "total_visible_minutes",
                    "foreground_service_minutes", "last_visible", "last_foreground_service")
                val q = Build.VERSION.SDK_INT >= 29
                val intervals = listOf(
                    "DAILY" to UsageStatsManager.INTERVAL_DAILY,
                    "WEEKLY" to UsageStatsManager.INTERVAL_WEEKLY,
                    "MONTHLY" to UsageStatsManager.INTERVAL_MONTHLY,
                    "YEARLY" to UsageStatsManager.INTERVAL_YEARLY
                )
                for ((name, iv) in intervals) {
                    val list = usm.queryUsageStats(iv, now - 5L * 365 * 86_400_000L, now).orEmpty()
                    for (s in list.sortedWith(compareBy({ it.firstTimeStamp }, { it.packageName }))) {
                        if (s.totalTimeInForeground <= 0 && (!q || s.totalTimeVisible <= 0)) continue
                        row(name, s.packageName, label(s.packageName),
                            ts(s.firstTimeStamp), ts(s.lastTimeStamp), ts(s.lastTimeUsed),
                            mins(s.totalTimeInForeground),
                            if (q) mins(s.totalTimeVisible) else "",
                            if (q) mins(s.totalTimeForegroundServiceUsed) else "",
                            if (q) ts(s.lastTimeVisible) else "",
                            if (q) ts(s.lastTimeForegroundServiceUsed) else "")
                    }
                }
            }

            zip.file("installed_apps.csv") {
                row("package", "app", "version_name", "version_code", "installed", "last_updated",
                    "category", "target_sdk", "is_system_app", "enabled", "launchable", "installer")
                for (p in installed.sortedBy { label(it.packageName).lowercase() }) {
                    val ai = p.applicationInfo
                    val isSystem = ai != null && (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val versionCode = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else {
                        @Suppress("DEPRECATION") p.versionCode.toLong()
                    }
                    val category = if (ai != null && Build.VERSION.SDK_INT >= 26) {
                        ApplicationInfo.getCategoryTitle(context, ai.category)?.toString() ?: ""
                    } else ""
                    row(p.packageName, label(p.packageName), p.versionName, versionCode,
                        ts(p.firstInstallTime), ts(p.lastUpdateTime), category,
                        ai?.targetSdkVersion, isSystem, ai?.enabled,
                        pm.getLaunchIntentForPackage(p.packageName) != null,
                        installer(p.packageName))
                }
            }
        }

        return ExportResult(events.size, appSessions.size, days.size, dailyApp.keys.map { it.substringAfter("|") }.toSet().size)
    }

    private fun installer(pkg: String): String = try {
        if (Build.VERSION.SDK_INT >= 30) {
            pm.getInstallSourceInfo(pkg).installingPackageName ?: ""
        } else {
            @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg) ?: ""
        }
    } catch (e: Exception) {
        ""
    }

    private fun deviceInfo(w: BufferedWriter, now: Long, events: List<Ev>) {
        w.row("field", "value")
        w.row("exported_at", ts(now))
        w.row("time_zone", TimeZone.getDefault().id)
        w.row("locale", Locale.getDefault().toString())
        w.row("manufacturer", Build.MANUFACTURER)
        w.row("brand", Build.BRAND)
        w.row("model", Build.MODEL)
        w.row("device", Build.DEVICE)
        w.row("android_version", Build.VERSION.RELEASE)
        w.row("android_sdk", Build.VERSION.SDK_INT)
        if (Build.VERSION.SDK_INT >= 23) w.row("security_patch", Build.VERSION.SECURITY_PATCH)
        w.row("earliest_event_available", ts(events.firstOrNull()?.t ?: 0L))
        w.row("latest_event_available", ts(events.lastOrNull()?.t ?: 0L))
        w.row("event_count", events.size)

        val dm = context.resources.displayMetrics
        w.row("screen_pixels", "${dm.widthPixels}x${dm.heightPixels}")
        w.row("screen_dpi", dm.densityDpi)

        try {
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            w.row("ram_total_mb", mi.totalMem / 1_048_576)
            w.row("ram_available_mb_at_export", mi.availMem / 1_048_576)
        } catch (ex: Exception) { }

        try {
            val st = StatFs(Environment.getDataDirectory().path)
            w.row("storage_total_gb", String.format(Locale.US, "%.1f", st.totalBytes / 1e9))
            w.row("storage_free_gb", String.format(Locale.US, "%.1f", st.availableBytes / 1e9))
        } catch (ex: Exception) { }

        try {
            val b = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (b != null) {
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) w.row("battery_percent_at_export", level * 100 / scale)
                val status = b.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                w.row("charging_at_export",
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                w.row("battery_temperature_c", b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0)
                w.row("battery_health_code", b.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
            }
        } catch (ex: Exception) { }
    }

    private fun readme(now: Long, from: Long, eventCount: Int) = """
FocusLens export
Created: ${ts(now)}
Lookback requested: ${ts(from)} to ${ts(now)}
Raw events found: $eventCount

FILES
daily_summary.csv       One row per day: screen-on minutes, screen-on count (pickups), unlocks,
                        total app time, sessions, apps used, first and last app use.
daily_app_usage.csv     One row per day per app: foreground minutes and number of sessions.
hourly_usage.csv        One row per day, hour and app: foreground minutes (for heatmaps).
app_sessions.csv        Every app session with start, end and duration.
activity_sessions.csv   Every screen (activity) opened inside each app, with a readable name.
in_app_sections.csv     Minutes per day inside Instagram and YouTube by section (Reels, Feed,
                        Stories, Messages, Shorts ...). Only measured while the FocusLens
                        accessibility service is on.
ui_debug_ids.csv        Internal element names seen by the section detector. Used to fix the
                        rules if an app update breaks them. Contains no personal text.
screen_sessions.csv     Every time the screen was on, with start, end and duration.
raw_events.csv          Every raw Android usage event (app opened, closed, screen on/off,
                        lock/unlock, foreground services, standby bucket changes, and more).
system_usage_stats.csv  Totals kept by Android itself: daily, weekly, monthly and yearly.
                        These go back further than the raw events (months to about 2 years)
                        but have no per-session detail.
installed_apps.csv      Every installed app with version, install/update dates, category,
                        system flag and installer.
device_info.csv        Phone model, Android version, storage, memory, battery at export time.

LIMITS (set by Android, not by this app)
- Raw events, sessions, hourly and daily files only cover what Android still stores,
  usually about 1 to 2 weeks. Check device_info.csv for the earliest event available.
- To build a longer history, export regularly. Files can be merged by date.
- Not available to normal apps: what you did inside an app, notification text, browser
  history, call or message logs, location, and per-app battery use.
- Session times are rebuilt from the event log and can differ slightly from Digital Wellbeing.
- Sessions of the same app less than ${MERGE_GAP_MS / 1000} seconds apart are joined into one.
  activity_sessions.csv and raw_events.csv are NOT merged.
- is_system_surface = true marks the launcher, system UI and similar. These are not apps
  you chose to open. Use app_minutes_excluding_launcher_and_system in daily_summary.csv.
- Screen-on time can be too high on days when Android logs "screen on" without a matching
  "screen off", or when Developer options "Stay awake" is on while charging.
""".trimIndent()

    companion object {
        private const val MERGE_GAP_MS = 3_000L
    }
}
