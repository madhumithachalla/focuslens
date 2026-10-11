package com.focuslens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.focuslens.data.AlarmEntity
import java.util.Calendar

/**
 * Schedules a real alarm via AlarmManager.setAlarmClock — the same API Android's own Clock app
 * uses. Unlike setExactAndAllowWhileIdle, this needs no special permission on any API level and
 * is explicitly exempt from Doze/battery-optimization throttling, because the OS treats it as a
 * user-visible alarm clock (it shows the little alarm icon in the status bar). This is what
 * lets the alarm fire even if the app is fully closed or the phone is locked — the one thing
 * the web app's version cannot do.
 */
object AlarmScheduler {
    private fun pendingIntent(context: Context, alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("alarmId", alarmId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next occurrence of hour:minute on one of `days` (0=Sun..6=Sat), or just the next
     *  occurrence of that time (today or tomorrow) if days is empty (one-off alarm). */
    fun nextTriggerMillis(hour: Int, minute: Int, days: List<Int>): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (days.isEmpty()) {
            if (candidate.timeInMillis <= now.timeInMillis) candidate.add(Calendar.DAY_OF_YEAR, 1)
            return candidate.timeInMillis
        }
        // Calendar.DAY_OF_WEEK is 1=Sun..7=Sat; our days are 0=Sun..6=Sat.
        for (offset in 0..7) {
            val check = candidate.clone() as Calendar
            check.add(Calendar.DAY_OF_YEAR, offset)
            val dow = check.get(Calendar.DAY_OF_WEEK) - 1
            if (dow in days && check.timeInMillis > now.timeInMillis) return check.timeInMillis
        }
        // Shouldn't happen (days is non-empty), but fall back to tomorrow at the same time.
        candidate.add(Calendar.DAY_OF_YEAR, 1)
        return candidate.timeInMillis
    }

    fun schedule(context: Context, alarm: AlarmEntity) {
        val days = parseDays(alarm.days)
        val triggerAt = nextTriggerMillis(alarm.hour, alarm.minute, days)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            context, alarm.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAt, showIntent),
            pendingIntent(context, alarm.id)
        )
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, alarmId))
    }

    fun parseDays(days: String): List<Int> =
        if (days.isBlank()) emptyList() else days.split(",").mapNotNull { it.trim().toIntOrNull() }

    fun joinDays(days: List<Int>): String = days.sorted().joinToString(",")
}
