package com.focuslens

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the blocker remembers, kept on the phone only. */
class FocusPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("focus", Context.MODE_PRIVATE)

    /** Master switch for blocking. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(value) {
            sp.edit().putBoolean("enabled", value).apply()
        }

    fun blocked(): Set<String> = sp.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()

    fun isBlocked(pkg: String) = pkg in blocked()

    fun setBlocked(pkg: String, on: Boolean) {
        val s = blocked().toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        sp.edit().putStringSet("blocked", s).apply()
    }

    /** Daily minutes the user allows themselves in this app. 0 means always use the hard challenge. */
    fun limitMin(pkg: String): Int = sp.getInt("limit_$pkg", 30)

    fun setLimitMin(pkg: String, minutes: Int) {
        sp.edit().putInt("limit_$pkg", minutes.coerceIn(0, 480)).apply()
    }

    /**
     * How many times a day the user can pass the challenge and get back into an app
     * before it locks for the rest of the day — same idea as Minimalist Focus Launcher's
     * time-based app locks: once the daily allotment is spent, there is no further
     * challenge to beat, only a wait until the next day. Default 3, per-app.
     */
    fun maxSkipsPerDay(pkg: String): Int = sp.getInt("maxskips_$pkg", 3)

    fun setMaxSkipsPerDay(pkg: String, max: Int) {
        sp.edit().putInt("maxskips_$pkg", max.coerceIn(0, 20)).apply()
    }

    /** After a challenge is passed, the app is open until this time. */
    fun allowedUntil(pkg: String): Long = sp.getLong("allow_$pkg", 0L)

    fun grant(pkg: String, minutes: Int) {
        sp.edit().putLong("allow_$pkg", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    /**
     * The "day" for skip counts, limits and locks resets at 6am, not midnight — a 1am
     * reopen shouldn't quietly count as a fresh day. Shift the clock back 6 hours before
     * taking the date, so anything before 6am still belongs to the previous day's key.
     */
    private fun today(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.HOUR_OF_DAY, -RESET_HOUR)
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    /** Epoch millis of the next 6am — what a locked app is actually waiting for. */
    fun nextResetAt(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    companion object {
        private const val RESET_HOUR = 6
    }

    fun passesToday(pkg: String): Int = sp.getInt("passes_${pkg}_${today()}", 0)

    fun addPass(pkg: String) {
        sp.edit().putInt("passes_${pkg}_${today()}", passesToday(pkg) + 1).apply()
    }

    fun gaveUpToday(pkg: String): Int = sp.getInt("gaveup_${pkg}_${today()}", 0)

    fun addGaveUp(pkg: String) {
        sp.edit().putInt("gaveup_${pkg}_${today()}", gaveUpToday(pkg) + 1).apply()
    }

    // ---- In-app sections (Reels, Feed, Shorts ...) ----

    data class SectionRow(val date: String, val pkg: String, val section: String, val ms: Long)

    fun addSection(pkg: String, section: String, ms: Long) {
        val k = "sec|${today()}|$pkg|$section"
        sp.edit().putLong(k, sp.getLong(k, 0L) + ms).apply()
    }

    fun sectionRows(): List<SectionRow> = sp.all.mapNotNull { (k, v) ->
        if (!k.startsWith("sec|") || v !is Long) return@mapNotNull null
        val p = k.split("|")
        if (p.size < 4) null else SectionRow(p[1], p[2], p.drop(3).joinToString("|"), v)
    }.sortedWith(compareBy({ it.date }, { it.pkg }, { it.section }))

    /** Total milliseconds per section for one app over the last [days] days (1 = today). */
    fun sectionTotals(pkg: String, days: Int): Map<String, Long> {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_MONTH, -(days - 1))
        val cutoff = SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
        val out = HashMap<String, Long>()
        for (r in sectionRows()) {
            if (r.pkg == pkg && r.date >= cutoff) out[r.section] = (out[r.section] ?: 0L) + r.ms
        }
        return out
    }

    fun addDebugId(pkg: String, id: String) {
        val key = "dbg_$pkg"
        val cur = sp.getStringSet(key, emptySet())?.toSet() ?: emptySet()
        if (id.length > 60 || id in cur || cur.size >= 120) return
        sp.edit().putStringSet(key, cur + id).apply()
    }

    /** package -> element names the section detector saw (for tuning the rules). */
    fun debugIds(): Map<String, Set<String>> = sp.all.mapNotNull { (k, v) ->
        if (k.startsWith("dbg_") && v is Set<*>) {
            k.removePrefix("dbg_") to v.filterIsInstance<String>().toSet()
        } else {
            null
        }
    }.toMap()

    /** Times today the user saw the block screen and chose not to open the app. */
    fun totalGaveUpToday(): Int = blocked().sumOf { gaveUpToday(it) }

    /**
     * Sum of passes (opened anyway) and gave-ups (resisted) across every currently
     * blocked app over the last [days] days — the raw numbers the Focus Score reads.
     * Walking real stored per-date keys, not an estimate.
     */
    fun resistStatsLast(days: Int): Pair<Int, Int> {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = java.util.Calendar.getInstance()
        var passes = 0
        var gaveUp = 0
        for (pkg in blocked()) {
            val c = cal.clone() as java.util.Calendar
            repeat(days) {
                val d = fmt.format(c.time)
                passes += sp.getInt("passes_${pkg}_$d", 0)
                gaveUp += sp.getInt("gaveup_${pkg}_$d", 0)
                c.add(java.util.Calendar.DAY_OF_MONTH, -1)
            }
        }
        return passes to gaveUp
    }

    // ---- Hyper Focus: blocks every app device-wide, not just the ones toggled on ----

    /** 0 means off. Everything except the allowlist and FocusLens itself is blocked until this time. */
    fun hyperFocusUntil(): Long = sp.getLong("hyperfocus_until", 0L)

    fun startHyperFocus(minutes: Int) {
        sp.edit().putLong("hyperfocus_until", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    fun endHyperFocus() {
        sp.edit().putLong("hyperfocus_until", 0L).apply()
    }

    fun isHyperFocusActive(): Boolean = System.currentTimeMillis() < hyperFocusUntil()

    /** Apps the user explicitly exempted from Hyper Focus (e.g. a work chat app). The phone's own dialer is always allowed, separately. */
    fun hyperFocusAllowlist(): Set<String> = sp.getStringSet("hyperfocus_allow", emptySet())?.toSet() ?: emptySet()

    fun setHyperFocusAllowed(pkg: String, allowed: Boolean) {
        val s = hyperFocusAllowlist().toMutableSet()
        if (allowed) s.add(pkg) else s.remove(pkg)
        sp.edit().putStringSet("hyperfocus_allow", s).apply()
    }
}
