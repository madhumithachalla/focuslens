package com.focuslens

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/**
 * Writes (and removes) a single one-hour event per to-do task on the device's default
 * calendar — the same account Google Calendar, Samsung Calendar etc. already sync, so a task
 * added here shows up wherever the person already looks at their calendar. Read-only on
 * everything else: it never reads existing events, only ever writes the one it created.
 */
object CalendarSync {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** The device's first visible, writable calendar — usually the signed-in Google account's
     *  primary one. FocusLens doesn't ask which calendar to use; most phones only have one
     *  that's actually writable anyway. */
    private fun defaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Adds a one-hour event titled [title] starting at [whenMillis]. Returns the new event's
     *  row id (store it as TaskEntity.calendarEventId) or null if it couldn't be added — no
     *  permission, or no writable calendar on the device. Never throws. */
    fun addEvent(context: Context, title: String, whenMillis: Long): Long? {
        if (!hasPermission(context)) return null
        val calId = defaultCalendarId(context) ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, whenMillis)
            put(CalendarContract.Events.DTEND, whenMillis + 60 * 60 * 1000)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?.let { ContentUris.parseId(it) }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Removes a previously-added event — called when its task is deleted, so a finished or
     *  discarded to-do doesn't leave a stray calendar entry behind. */
    fun removeEvent(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            // Permission revoked since the event was created — nothing more we can do.
        }
    }
}
