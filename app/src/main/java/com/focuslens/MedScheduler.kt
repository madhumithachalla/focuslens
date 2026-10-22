package com.focuslens

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Medication reminders. Same approach as AlarmScheduler: AlarmManager.setAlarmClock, which needs
 * no special permission and is exempt from Doze. Only ONE alarm is ever pending: the next dose
 * time across all plans. MedReminderReceiver posts the notification(s) and re-arms the next one.
 * Call rescheduleAll() whenever plans change and from BootReceiver.
 */
object MedScheduler {
    const val ACTION_REMIND = "com.focuslens.MED_REMIND"
    const val ACTION_TAKEN = "com.focuslens.MED_TAKEN"
    const val ACTION_SNOOZE = "com.focuslens.MED_SNOOZE"
    const val EXTRA_AT = "at"
    const val EXTRA_PLAN_ID = "planId"
    const val EXTRA_TIME = "time"
    private const val REQ_NEXT = 7100

    private fun nextIntent(context: Context): PendingIntent {
        val intent = Intent(context, MedReminderReceiver::class.java).apply {
            action = ACTION_REMIND
        }
        return PendingIntent.getBroadcast(
            context, REQ_NEXT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val plans = MedPrefs(app).plans()
        val next = MedLogic.nextDoseMillis(plans, System.currentTimeMillis())
        val pi = nextIntent(app)
        if (next == null) {
            am.cancel(pi)
            return
        }
        // The pending intent carries the trigger time so the receiver knows which dose time fired.
        val intent = Intent(app, MedReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_AT, next)
        }
        val withTime = PendingIntent.getBroadcast(
            app, REQ_NEXT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showIntent = PendingIntent.getActivity(
            app, REQ_NEXT,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(next, showIntent), withTime)
    }

    /** One-off re-reminder for a single dose (Snooze 10 min). */
    fun snooze(context: Context, planId: String, time: String, minutes: Int = 10) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(app, MedReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_PLAN_ID, planId)
            putExtra(EXTRA_TIME, time)
            putExtra("fire", true)
        }
        val pi = PendingIntent.getBroadcast(
            app, MedLogic.doseKey(planId, time).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = System.currentTimeMillis() + minutes * 60_000L
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}
