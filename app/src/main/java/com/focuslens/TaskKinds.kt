package com.focuslens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.runBlocking

/** What a planner item is: a to-do, an event, or a reminder that notifies at its time. */
enum class PlanKind(val label: String) { TASK("Task"), EVENT("Event"), REMINDER("Reminder") }

/** Who the planner is set up for. Decides which categories are offered. */
enum class PlanAudience(val label: String) { STUDENT("Student"), ADULT("Adult") }

/**
 * Kind and audience are kept in the planner's SharedPreferences on the phone, so the task
 * table does not need a new column (and no database migration).
 */
object PlanPrefs {
    private const val FILE = "planner"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun kindOf(c: Context, taskId: Long): PlanKind =
        sp(c).getString("kind_$taskId", null)?.let { runCatching { PlanKind.valueOf(it) }.getOrNull() } ?: PlanKind.TASK

    fun setKind(c: Context, taskId: Long, kind: PlanKind) {
        sp(c).edit().putString("kind_$taskId", kind.name).apply()
    }

    fun clearKind(c: Context, taskId: Long) {
        sp(c).edit().remove("kind_$taskId").apply()
    }

    /** Task ids whose kind is REMINDER. */
    fun reminderIds(c: Context): List<Long> =
        sp(c).all.mapNotNull { (k, v) ->
            if (k.startsWith("kind_") && v == PlanKind.REMINDER.name) k.removePrefix("kind_").toLongOrNull() else null
        }

    fun audience(c: Context): PlanAudience =
        sp(c).getString("audience", null)?.let { runCatching { PlanAudience.valueOf(it) }.getOrNull() } ?: PlanAudience.ADULT

    fun setAudience(c: Context, a: PlanAudience) {
        sp(c).edit().putString("audience", a.name).apply()
    }
}

/**
 * Notifications for reminder items. Same approach as the medication reminders: one exact
 * alarm per reminder, re-armed after a reboot by BootReceiver. A reminder that was marked
 * done before it fires does not notify.
 */
object TaskReminders {
    const val CHANNEL_ID = "task_reminders"
    private const val ACTION_FIRE = "com.focuslens.TASK_REMIND"
    private const val EXTRA_ID = "taskId"
    private const val EXTRA_TITLE = "title"

    private fun alarmIntent(context: Context, taskId: Long, title: String = ""): Intent =
        Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            data = Uri.parse("focuslens://task/$taskId")
            putExtra(EXTRA_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }

    private fun pendingFor(context: Context, taskId: Long, title: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (taskId % Int.MAX_VALUE).toInt(),
            alarmIntent(context, taskId, title),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arms a notification for [at]. Does nothing if that time has already passed. */
    fun schedule(context: Context, taskId: Long, title: String, at: Long) {
        if (at <= System.currentTimeMillis()) return
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingFor(context.applicationContext, taskId, title)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(context: Context, taskId: Long) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingFor(context.applicationContext, taskId))
    }

    /** Re-arms every upcoming reminder. Called after a reboot. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        val dao = AppDatabase.get(app).taskDao()
        PlanPrefs.reminderIds(app).forEach { id ->
            val t = dao.byId(id) ?: return@forEach
            if (t.completedAt == null) schedule(app, id, t.title, t.scheduledAt)
        }
    }

    fun notify(context: Context, taskId: Long, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Planner reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            context, (taskId % Int.MAX_VALUE).toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify((taskId % Int.MAX_VALUE).toInt(), n)
    }
}

/** Fires at a reminder's time. Posts the notification unless the item was already marked done. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("taskId", -1L)
        if (id < 0) return
        val title = intent.getStringExtra("title").orEmpty()
        val pending = goAsync()
        Thread {
            try {
                val task = runBlocking { AppDatabase.get(context).taskDao().byId(id) }
                if (task != null && task.completedAt == null) {
                    TaskReminders.notify(context, id, title.ifBlank { task.title })
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
