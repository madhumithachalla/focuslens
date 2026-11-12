package com.focuslens

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

/** One night of sleep. [manual] = typed in by the person; [ongoing] = still asleep at the time of the check. */
data class SleepNight(
    val startMs: Long,
    val endMs: Long,
    val manual: Boolean = false,
    val ongoing: Boolean = false,
    /** estimate, confirmed (you said yes), corrected (you typed it), or break (you were awake, not asleep). */
    val kind: String = if (manual) "corrected" else "estimate"
) {
    val hours: Double get() = (endMs - startMs) / 3_600_000.0
}

/**
 * Estimates sleep from screen use: a long quiet stretch at night, ended by the phone being used
 * again in the morning. It is an estimate. It cannot see whether you are asleep, so a phone left in
 * another room, or switched off, can look like sleep, and a phone check in the night ends it early.
 * Brief screen-ons (under 10 minutes) are treated as checks, not as waking up.
 */
object SleepEstimator {
    private const val ON = 15
    private const val OFF = 16
    private const val KEYGUARD_SHOWN = 17
    private const val KEYGUARD_HIDDEN = 18

    private const val MIN_WAKE_MS = 10 * 60_000L
    private const val MIN_QUIET_MS = 90 * 60_000L
    private const val MIN_SLEEP_MS = 3 * 3_600_000L
    private const val MAX_SLEEP_MS = 14 * 3_600_000L
    private val NIGHT_START_HOURS = setOf(21, 22, 23, 0, 1, 2, 3, 4)
    private val WAKE_HOURS = (4..11).toSet()

    private data class Span(val start: Long, val end: Long)

    fun hour(ms: Long, zone: TimeZone): Int =
        Calendar.getInstance(zone).apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    /** Screen-on and unlock events from the last [days] days, as (time, type). */
    @Suppress("DEPRECATION")
    fun readEvents(context: Context, days: Int, now: Long): List<Pair<Long, Int>> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(now - days * 86_400_000L, now)
        val out = ArrayList<Pair<Long, Int>>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == ON || e.eventType == OFF || e.eventType == KEYGUARD_SHOWN || e.eventType == KEYGUARD_HIDDEN) {
                out += e.timeStamp to e.eventType
            }
        }
        return out
    }

    /** Sleep nights from screen events (sorted by time), plus whether the phone is still in use now. */
    fun infer(events: List<Pair<Long, Int>>, now: Long, zone: TimeZone): List<SleepNight> {
        val kept = ArrayList<Span>()
        var openOn: Long? = null
        for ((t, type) in events.sortedBy { it.first }) {
            when (type) {
                ON, KEYGUARD_HIDDEN -> if (openOn == null) openOn = t
                OFF, KEYGUARD_SHOWN -> {
                    val on = openOn
                    if (on != null && t - on >= MIN_WAKE_MS) kept += Span(on, t)
                    openOn = null
                }
            }
        }
        val nights = ArrayList<SleepNight>()
        for (i in 0 until kept.size - 1) {
            val start = kept[i].end
            val end = kept[i + 1].start
            if (looksLikeSleep(start, end, zone)) nights += SleepNight(start, end)
        }
        // Still asleep: the last use ended a while ago, at night, and nothing has been used since.
        if (openOn == null && kept.isNotEmpty()) {
            val last = kept.last().end
            val quiet = now - last
            if (quiet in MIN_QUIET_MS..MAX_SLEEP_MS && hour(last, zone) in NIGHT_START_HOURS) {
                nights += SleepNight(last, now, ongoing = true)
            }
        }
        return nights
    }

    private fun looksLikeSleep(start: Long, end: Long, zone: TimeZone): Boolean {
        val d = end - start
        if (d < MIN_SLEEP_MS || d > MAX_SLEEP_MS) return false
        return hour(start, zone) in NIGHT_START_HOURS && hour(end, zone) in WAKE_HOURS
    }
}

/** Keeps the nights on the phone. Estimates are refreshed; your corrections are never overwritten. */
object SleepStore {
    private const val WINDOW_MS = 8 * 86_400_000L

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("sleep", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun nights(c: Context): List<SleepNight> = try {
        val arr = JSONArray(sp(c).getString("nights", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val manual = o.optBoolean("manual")
            SleepNight(
                o.getLong("start"), o.getLong("end"), manual, o.optBoolean("ongoing"),
                o.optString("kind", if (manual) "corrected" else "estimate")
            )
        }.sortedBy { it.startMs }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(c: Context, list: List<SleepNight>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(JSONObject().put("start", n.startMs).put("end", n.endMs).put("manual", n.manual)
                .put("ongoing", n.ongoing).put("kind", n.kind))
        }
        sp(c).edit().putString("nights", arr.toString()).apply()
    }

    /** Recomputes the estimates for the recent window from screen use. Older estimates and your corrections stay. */
    fun refresh(c: Context, now: Long): List<SleepNight> {
        val fresh = SleepEstimator.infer(SleepEstimator.readEvents(c, 8, now), now, TimeZone.getDefault())
        val windowStart = now - WINDOW_MS
        val manual = nights(c).filter { it.manual }
        val older = nights(c).filter { !it.manual && !it.ongoing && it.startMs < windowStart }
        // An estimate that overlaps one of your corrections is dropped, so your correction wins.
        val estimates = fresh.filter { f -> manual.none { m -> overlaps(f, m) } }
        val all = (manual + older + estimates).sortedBy { it.startMs }
        save(c, all)
        return all
    }

    fun addManual(c: Context, start: Long, end: Long) {
        val current = nights(c).filterNot { it.manual && it.startMs == start }
        save(c, (current + SleepNight(start, end, manual = true)).sortedBy { it.startMs })
    }

    fun deleteManual(c: Context, night: SleepNight) {
        save(c, nights(c).filterNot { it.manual && it.startMs == night.startMs })
    }

    private fun overlaps(a: SleepNight, b: SleepNight): Boolean = a.startMs < b.endMs && b.startMs < a.endMs

    private fun answeredSet(c: Context): Set<String> = sp(c).getStringSet("answered", emptySet())?.toSet() ?: emptySet()

    /** The estimated night to ask about: finished in the last day, and not answered yet. */
    fun pendingCheck(c: Context, now: Long): SleepNight? {
        val answered = answeredSet(c)
        return nights(c)
            .filter { !it.manual && !it.ongoing && it.kind == "estimate" && it.startMs.toString() !in answered }
            .filter { now - it.endMs in 0..(24 * 3_600_000L) }
            .maxByOrNull { it.endMs }
    }

    /**
     * Your answer to the check-in. "confirmed" = it was sleep (times may be adjusted), "break" = you were
     * awake, not asleep. Either way the estimate is replaced and not asked about again.
     */
    fun answer(c: Context, night: SleepNight, kind: String, startMs: Long = night.startMs, endMs: Long = night.endMs) {
        val rest = nights(c).filterNot { it.startMs == night.startMs && !it.ongoing && !it.manual }
        val updated = if (kind == "confirmed" || kind == "break") {
            rest + SleepNight(startMs, endMs, manual = true, kind = kind)
        } else rest
        save(c, updated.sortedBy { it.startMs })
        sp(c).edit().putStringSet("answered", answeredSet(c) + night.startMs.toString()).apply()
    }
}
