package com.focuslens

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Fired by MedScheduler at a dose time (ACTION_REMIND), by the notification's "Mark taken"
 * button (ACTION_TAKEN) and "Snooze 10 min" button (ACTION_SNOOZE). All data stays on the phone.
 * The notification names the medication only if the user turned off "Hide medication name".
 */
class MedReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = MedPrefs(context)
        when (intent.action) {
            MedScheduler.ACTION_REMIND -> {
                val at = intent.getLongExtra(MedScheduler.EXTRA_AT, -1L)
                if (at > 0) {
                    MedLogic.dosesAt(prefs.plans(), at).forEach { (plan, time) ->
                        notifyDose(context, prefs, plan, time)
                    }
                }
                MedScheduler.rescheduleAll(context) // arm the next dose time
            }
            MedScheduler.ACTION_TAKEN -> {
                val planId = intent.getStringExtra(MedScheduler.EXTRA_PLAN_ID) ?: return
                val time = intent.getStringExtra(MedScheduler.EXTRA_TIME) ?: return
                prefs.markTaken(MedLogic.doseKey(planId, time))
                cancelNotification(context, planId, time)
            }
            MedScheduler.ACTION_SNOOZE -> {
                val planId = intent.getStringExtra(MedScheduler.EXTRA_PLAN_ID) ?: return
                val time = intent.getStringExtra(MedScheduler.EXTRA_TIME) ?: return
                if (intent.getBooleanExtra("fire", false)) {
                    // The snooze alarm itself: show the reminder again unless already taken.
                    val plan = prefs.plans().firstOrNull { it.id == planId } ?: return
                    val done = prefs.dueToday().any { it.key == MedLogic.doseKey(planId, time) && it.state == DoseState.TAKEN }
                    if (!done) notifyDose(context, prefs, plan, time)
                } else {
                    MedScheduler.snooze(context, planId, time, 10)
                    cancelNotification(context, planId, time)
                }
            }
        }
    }

    private fun notifId(planId: String, time: String) = MedLogic.doseKey(planId, time).hashCode()

    private fun cancelNotification(context: Context, planId: String, time: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notifId(planId, time))
    }

    private fun actionIntent(context: Context, action: String, planId: String, time: String): PendingIntent {
        val i = Intent(context, MedReminderReceiver::class.java).apply {
            this.action = action
            putExtra(MedScheduler.EXTRA_PLAN_ID, planId)
            putExtra(MedScheduler.EXTRA_TIME, time)
        }
        return PendingIntent.getBroadcast(
            context, (action + MedLogic.doseKey(planId, time)).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notifyDose(context: Context, prefs: MedPrefs, plan: MedPlan, time: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Medication reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val generic = "Time for a medication"
        val title = if (prefs.hideNamesInNotifications()) generic else "Time for ${plan.name}"
        val open = PendingIntent.getActivity(
            context, 7101, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // What shows on the lock screen when the phone hides private content: always generic.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(generic)
            .build()

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(time)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Mark taken", actionIntent(context, MedScheduler.ACTION_TAKEN, plan.id, time))
            .addAction(0, "Snooze 10 min", actionIntent(context, MedScheduler.ACTION_SNOOZE, plan.id, time))
            .build()
        nm.notify(notifId(plan.id, time), n)
    }

    companion object {
        const val CHANNEL_ID = "med_reminders"
    }
}
