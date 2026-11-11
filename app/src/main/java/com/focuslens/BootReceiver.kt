package com.focuslens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager entries are cleared on reboot — re-arm every enabled alarm so none silently
 *  stop working just because the phone restarted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).alarmDao()
                dao.allEnabledOnce().forEach { AlarmScheduler.schedule(context, it) }
                MedScheduler.rescheduleAll(context)
                TaskReminders.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
