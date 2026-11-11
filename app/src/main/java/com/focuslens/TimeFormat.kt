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

    /** Time left of a daily limit, as hh:mm:ss. */
    fun limitLeftHms(limitMin: Int, usedMin: Long): String = hms((limitMin - usedMin) * 60L)
}
