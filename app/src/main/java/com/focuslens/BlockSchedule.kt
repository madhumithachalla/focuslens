package com.focuslens

/**
 * A time window during which the user's blocked apps (see FocusPrefs.blocked) are shut.
 * [days] uses ISO numbering: 1 = Monday ... 7 = Sunday. Minutes are minutes since midnight;
 * if [endMinutes] <= [startMinutes] the window crosses midnight (e.g. 21:00-00:00).
 */
data class BlockSchedule(
    val id: String,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val strict: Boolean,
    val enabled: Boolean
)

/**
 * Pure time-window check, no Android types so it can be unit tested on the JVM.
 * [dayOfWeek] is ISO (1 = Monday ... 7 = Sunday) and [minuteOfDay] is 0..1439.
 * A window that crosses midnight belongs to the day it starts on: Friday 21:00-02:00 is
 * still active on Saturday at 01:00.
 */
fun isWindowActive(dayOfWeek: Int, minuteOfDay: Int, start: Int, end: Int, days: Set<Int>): Boolean {
    if (start == end) return false
    return if (start < end) {
        dayOfWeek in days && minuteOfDay >= start && minuteOfDay < end
    } else {
        val prevDay = if (dayOfWeek == 1) 7 else dayOfWeek - 1
        (dayOfWeek in days && minuteOfDay >= start) || (prevDay in days && minuteOfDay < end)
    }
}

/** "07:30" style label for minutes since midnight (24 -> 00:00). */
fun formatMinutes(m: Int): String {
    val mm = ((m % 1440) + 1440) % 1440
    return String.format("%02d:%02d", mm / 60, mm % 60)
}
