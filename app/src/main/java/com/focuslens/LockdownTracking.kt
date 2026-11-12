package com.focuslens

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Phone lockdown: when you go past your daily phone limit, or past your limit during your peak
 * hour, only the apps you allow stay open (alarm, music, WhatsApp by default). Settings, system
 * screens, the dialler and FocusLens itself always stay open, so you cannot get stuck.
 * Unlocking takes the quiz, the same as the other blocks.
 */
object LockdownPrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("lockdown", Context.MODE_PRIVATE)

    /** Apps that stay open in a lockdown. Chosen by name, so these are only defaults. */
    val DEFAULT_ALLOWED = listOf(
        "com.whatsapp",
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.android.deskclock"
    )

    /** Always open: the dialler, system screens, settings and FocusLens. */
    val ALWAYS_OPEN_PREFIXES = listOf("com.android.", "com.google.android.dialer", "com.samsung.android.dialer", "com.google.android.as", "com.samsung.android.app.telephonyui")

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun dailyLimitMin(c: Context) = sp(c).getInt("daily_limit", 240).coerceIn(30, 720)
    fun setDailyLimitMin(c: Context, v: Int) = sp(c).edit().putInt("daily_limit", v.coerceIn(30, 720)).apply()

    fun peakGuard(c: Context) = sp(c).getBoolean("peak_guard", false)
    fun setPeakGuard(c: Context, v: Boolean) = sp(c).edit().putBoolean("peak_guard", v).apply()

    fun peakLimitMin(c: Context) = sp(c).getInt("peak_limit", 20).coerceIn(5, 60)
    fun setPeakLimitMin(c: Context, v: Int) = sp(c).edit().putInt("peak_limit", v.coerceIn(5, 60)).apply()

    fun allowed(c: Context): Set<String> =
        sp(c).getStringSet("allowed", null)?.toSet() ?: DEFAULT_ALLOWED.toSet()

    fun setAllowed(c: Context, v: Set<String>) = sp(c).edit().putStringSet("allowed", v).apply()

    /** Set after a successful quiz: lockdown is off until this time. */
    fun unlockUntil(c: Context) = sp(c).getLong("unlock_until", 0L)
    fun setUnlockUntil(c: Context, v: Long) = sp(c).edit().putLong("unlock_until", v).apply()

    fun isAlwaysOpen(pkg: String, own: String): Boolean =
        pkg == own || ALWAYS_OPEN_PREFIXES.any { pkg.startsWith(it) }
}

/** Set by the service's check. Read on every window change, so it must be cheap. */
object LockdownState {
    @Volatile var locked = false
    @Volatile var reason = ""
    @Volatile var checkedAt = 0L
}

/** Works out whether lockdown applies right now, from usage. Runs off the accessibility thread. */
object LockdownChecker {
    /** The hour (0-23) with the most usage over the recent history, or null if there is none. */
    fun peakHour(recent: Map<Int, Long>): Int? =
        recent.filterValues { it > 0 }.maxByOrNull { it.value }?.key

    fun evaluate(context: Context, now: Long): Pair<Boolean, String> {
        if (!LockdownPrefs.enabled(context)) return false to ""
        if (LockdownPrefs.unlockUntil(context) > now) return false to ""
        val repo = UsageRepository(context)
        if (!repo.hasPermission()) return false to ""

        val dayStart = FocusPrefs.limitDayStartMs(now)
        val totalMin = repo.loadCustom(dayStart, now, withLaunches = false).sumOf { it.millis } / 60_000L
        val limit = LockdownPrefs.dailyLimitMin(context)
        if (totalMin >= limit) {
            return true to "You have used $totalMin of your $limit-minute phone limit today."
        }

        if (LockdownPrefs.peakGuard(context)) {
            val history = UsageHistoryRepository(context, repo)
            val (peak, nowHourMin) = runBlocking {
                val recent = history.hourlyTotalsRecent(7)
                val hour = peakHour(recent)
                val today = history.hourlyTotalsForToday()
                val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                hour to (today.firstOrNull { it.hour == currentHour }?.millis ?: 0L) / 60_000L
            }
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val peakLimit = LockdownPrefs.peakLimitMin(context)
            if (peak != null && peak == currentHour && nowHourMin >= peakLimit) {
                return true to "This is your peak hour (${peak}:00). You have used $nowHourMin of $peakLimit minutes in it."
            }
        }
        return false to ""
    }
}
