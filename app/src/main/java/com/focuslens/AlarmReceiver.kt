package com.focuslens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager (see AlarmScheduler) at the scheduled time, even if the app was fully
 * closed. Launches the full-screen ringing activity, then re-arms the next occurrence for a
 * repeating alarm, or disables a one-off alarm so it doesn't fire again.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarmId", -1L)
        if (alarmId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).alarmDao()
                val alarm = dao.allEnabledOnce().firstOrNull { it.id == alarmId } ?: return@launch

                context.startActivity(
                    Intent(context, AlarmRingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                        putExtra("label", alarm.label)
                        putExtra("time", String.format("%02d:%02d", alarm.hour, alarm.minute))
                    }
                )

                val days = AlarmScheduler.parseDays(alarm.days)
                if (days.isEmpty()) {
                    dao.setEnabled(alarm.id, false)
                } else {
                    AlarmScheduler.schedule(context, alarm) // re-arm for the next matching day
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
