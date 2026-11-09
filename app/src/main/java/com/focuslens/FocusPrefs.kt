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
        /** Minutes as "HH:MM" (e.g. 90 -> "01:30"). */
        fun hhmm(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

        /**
         * Start of the current limit day: the most recent 6am. Usage for the daily limit is counted
         * from here, not midnight, so it matches the lock and skip day (which resets at 6am).
         */
        fun limitDayStartMs(now: Long = System.currentTimeMillis()): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now
            cal.add(java.util.Calendar.HOUR_OF_DAY, -RESET_HOUR)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.add(java.util.Calendar.HOUR_OF_DAY, RESET_HOUR)
            return cal.timeInMillis
        }
        const val HYPERFOCUS_INDEFINITE = Long.MAX_VALUE
    }

    fun passesToday(pkg: String): Int = sp.getInt("passes_${pkg}_${today()}", 0)

    /** Max times per day you may open Reels (0 = no limit). Counted per limit day, which resets at 6am. */
    fun reelLimit(): Int = sp.getInt("reel_limit", 0)
    fun setReelLimit(n: Int) { sp.edit().putInt("reel_limit", n.coerceIn(0, 500)).apply() }
    fun reelsToday(): Int = sp.getInt("reels_${today()}", 0)
    fun addReelToday() { sp.edit().putInt("reels_${today()}", reelsToday() + 1).apply() }

    fun addPass(pkg: String) {
        sp.edit().putInt("passes_${pkg}_${today()}", passesToday(pkg) + 1).apply()
    }

    fun gaveUpToday(pkg: String): Int = sp.getInt("gaveup_${pkg}_${today()}", 0)

    fun addGaveUp(pkg: String) {
        sp.edit().putInt("gaveup_${pkg}_${today()}", gaveUpToday(pkg) + 1).apply()
    }

    // ---- In-app sections (Reels, Feed, Shorts ...) ----

    data class SectionRow(val date: String, val pkg: String, val section: String, val ms: Long)

    /** Counts one thing that happened today: an open attempt, a reminder, a block. */
    fun bump(pkg: String, kind: String, n: Long = 1L) {
        val k = "ev|${today()}|$pkg|$kind"
        sp.edit().putLong(k, sp.getLong(k, 0L) + n).apply()
    }

    /** Counts per app and kind over the last [days] limit-days (6am-shifted, like the limits). */
    fun eventTotals(days: Int, now: Long = System.currentTimeMillis()): Map<String, Map<String, Long>> {
        val keep = (0 until days).map { back ->
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now - back * 24L * 60 * 60 * 1000
            cal.add(java.util.Calendar.HOUR_OF_DAY, -RESET_HOUR)
            SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
        }.toSet()
        val out = HashMap<String, HashMap<String, Long>>()
        for ((key, value) in sp.all) {
            if (!key.startsWith("ev|")) continue
            val parts = key.split("|")
            if (parts.size < 4 || parts[1] !in keep) continue
            val pkg = parts[2]
            val kind = parts.subList(3, parts.size).joinToString("|")
            val n = (value as? Long) ?: continue
            val byKind = out.getOrPut(pkg) { HashMap() }
            byKind[kind] = (byKind[kind] ?: 0L) + n
        }
        return out
    }

    /** Last section FocusLens worked out for a tracked app, for the status line on the Focus tab. */
    fun setLastSeen(pkg: String, section: String, capped: Boolean = false) {
        sp.edit().putString("seen_pkg", pkg).putString("seen_section", section)
            .putBoolean("seen_capped", capped).putLong("seen_at", System.currentTimeMillis()).apply()
    }

    fun lastSeen(): Triple<String, String, Long>? {
        val pkg = sp.getString("seen_pkg", null) ?: return null
        val section = sp.getString("seen_section", null) ?: return null
        return Triple(pkg, section, sp.getLong("seen_at", 0L))
    }

    fun lastSeenCapped(): Boolean = sp.getBoolean("seen_capped", false)

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

    /** 0 means off. Everything except the allowlist and FocusLens itself is blocked until this
     *  time — or, if this equals [HYPERFOCUS_INDEFINITE], until [endHyperFocus] is called. */
    fun hyperFocusUntil(): Long = sp.getLong("hyperfocus_until", 0L)

    fun startHyperFocus(minutes: Int) {
        sp.edit().putLong("hyperfocus_until", System.currentTimeMillis() + minutes * 60_000L).apply()
    }

    /** "Stay on until I turn it off" — no auto-end time, same block everything else. */
    fun startHyperFocusIndefinite() {
        sp.edit().putLong("hyperfocus_until", HYPERFOCUS_INDEFINITE).apply()
    }

    fun isHyperFocusIndefinite(): Boolean = hyperFocusUntil() == HYPERFOCUS_INDEFINITE

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

    // ---- Feature-level blocking: a specific section of an app (Reels, Shorts), not the
    // whole app. Independent of the daily-limit/skip system above — while this is on,
    // opening that section is a flat rule with no challenge to beat. ----

    fun blockedSections(pkg: String): Set<String> =
        sp.getStringSet("blocksec_$pkg", emptySet())?.toSet() ?: emptySet()

    fun isSectionBlocked(pkg: String, section: String) = section in blockedSections(pkg)

    fun setSectionBlocked(pkg: String, section: String, blocked: Boolean) {
        val s = blockedSections(pkg).toMutableSet()
        if (blocked) s.add(section) else s.remove(section)
        sp.edit().putStringSet("blocksec_$pkg", s).apply()
    }

    // ---- Chrome: block social media domains entirely, never academic ones. Off by default. ----

    fun socialBrowserBlockEnabled(): Boolean = sp.getBoolean("browser_block_social", false)

    fun setSocialBrowserBlockEnabled(on: Boolean) {
        sp.edit().putBoolean("browser_block_social", on).apply()
    }

    /** Extra sites the user has personally added to the academic allowlist, on top of the defaults. */
    fun customAcademicAllowlist(): Set<String> =
        sp.getStringSet("academic_allow", emptySet())?.toSet() ?: emptySet()

    fun addAcademicAllow(domain: String) {
        val d = domain.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        if (d.isEmpty()) return
        sp.edit().putStringSet("academic_allow", customAcademicAllowlist() + d).apply()
    }

    fun removeAcademicAllow(domain: String) {
        sp.edit().putStringSet("academic_allow", customAcademicAllowlist() - domain).apply()
    }

    // ---- Doom-scroll reminders: a nudge after continuous scrolling in a tracked app, not a
    // block — see FocusAccessibilityService's scroll tracking. On by default, since it's a
    // reminder, not a restriction. ----

    fun doomScrollReminderEnabled(): Boolean = sp.getBoolean("doomscroll_enabled", true)

    fun skipReelEnabled(): Boolean = sp.getBoolean("skip_reel_enabled", false)

    fun setSkipReelEnabled(on: Boolean) = sp.edit().putBoolean("skip_reel_enabled", on).apply()

    fun setDoomScrollReminderEnabled(on: Boolean) {
        sp.edit().putBoolean("doomscroll_enabled", on).apply()
    }

    /** How many scroll events in one continuous streak before a reminder shows. */
    fun doomScrollThreshold(): Int = sp.getInt("doomscroll_threshold", 40)

    // ---- Scrolling limits (time based): a reminder every N minutes, and a break after a long run ----

    /** Minutes between scrolling reminders while you keep scrolling. Default 4. */
    fun scrollReminderMinutes(): Int = sp.getInt("scroll_reminder_min", 4).coerceIn(1, 60)

    fun setScrollReminderMinutes(n: Int) = sp.edit().putInt("scroll_reminder_min", n.coerceIn(1, 60)).apply()

    /** Continuous scrolling, in minutes, before a break starts. Default 30. */
    fun scrollBreakAfterMinutes(): Int = sp.getInt("scroll_break_after_min", 30).coerceIn(5, 180)

    fun setScrollBreakAfterMinutes(n: Int) = sp.edit().putInt("scroll_break_after_min", n.coerceIn(5, 180)).apply()

    /** How long a break lasts. Default 15 minutes. */
    fun breakMinutes(): Int = sp.getInt("break_min", 15).coerceIn(1, 120)

    fun setBreakMinutes(n: Int) = sp.edit().putInt("break_min", n.coerceIn(1, 120)).apply()

    /** Breaks allowed per app per day. Past this, the app is locked until 6am. Default 3. */
    fun breaksPerDayLimit(): Int = sp.getInt("breaks_per_day_limit", 3).coerceIn(1, 10)

    fun setBreaksPerDayLimit(n: Int) = sp.edit().putInt("breaks_per_day_limit", n.coerceIn(1, 10)).apply()

    fun breaksToday(pkg: String): Int = sp.getInt("breaks_${pkg}_${today()}", 0)

    fun addBreak(pkg: String) = sp.edit().putInt("breaks_${pkg}_${today()}", breaksToday(pkg) + 1).apply()

    /** Until when [pkg] is on a break (epoch millis), 0 if not. */
    fun breakUntil(pkg: String): Long = sp.getLong("break_until_$pkg", 0L)

    fun startBreak(pkg: String, until: Long) = sp.edit().putLong("break_until_$pkg", until).apply()

    /** Until when [pkg] is locked for the rest of the day (epoch millis), 0 if not. */
    fun dayLockUntil(pkg: String): Long = sp.getLong("day_lock_$pkg", 0L)

    fun setDayLockUntil(pkg: String, until: Long) = sp.edit().putLong("day_lock_$pkg", until).apply()

    /** Skip reel: the button shows on Instagram when turned on (see SkipReelOverlay). */

    fun setDoomScrollThreshold(n: Int) {
        sp.edit().putInt("doomscroll_threshold", n.coerceIn(10, 200)).apply()
    }

    // ---- YouTube: "academic/podcast only" mode. Off by default; a best-effort keyword
    // check, not real content classification. ----

    fun youtubeAcademicOnly(): Boolean = sp.getBoolean("yt_academic_only", false)

    fun setYoutubeAcademicOnly(on: Boolean) {
        sp.edit().putBoolean("yt_academic_only", on).apply()
    }
}
