package com.focuslens

import java.util.Locale

/** Time shown as hours, minutes and seconds (hh:mm:ss), so a countdown is never only in minutes. */
object Clock {
    fun hms(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0L)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    /** Time left until [untilMs], as hh:mm:ss. */
    fun untilHms(untilMs: Long, now: Long = System.currentTimeMillis()): String = hms((untilMs - now) / 1000L)

    /** Minutes as people read them: 45 -> "45 min", 120 -> "2 h 00 min", 95 -> "1 h 35 min". */
    fun minutesHuman(totalMinutes: Long): String {
        val m = totalMinutes.coerceAtLeast(0L)
        return if (m < 60L) "$m min" else String.format(Locale.US, "%d h %02d min", m / 60L, m % 60L)
    }

    /** Time left of a daily limit, as hh:mm:ss. */
    fun limitLeftHms(limitMin: Int, usedMin: Long): String = hms((limitMin - usedMin) * 60L)
}
