package com.focuslens

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Everything FocusLens needs, in one place, with live status. Each item is asked for once; after
 * that the status just shows as done. Nothing is requested in the background.
 */
@Composable
fun SetupChecklistCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Re-check every time the person comes back from a Settings screen.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val refresh = tick // read so the status below recomputes after each resume

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    val items = remember(refresh) { checklistItems(context) }

    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Set up FocusLens once", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Each permission below is asked for once. The features that need it start working as soon as it is on.",
                fontSize = 12.sp, color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text((if (item.granted) "✓  " else "•  ") + item.title, fontSize = 14.sp)
                        Text(item.why, fontSize = 11.sp, color = Color.Gray)
                    }
                    if (!item.granted) {
                        OutlinedButton(onClick = {
                            when (item.kind) {
                                SetupKind.NOTIFICATIONS -> notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                SetupKind.CALENDAR -> calendar.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                else -> context.startActivity(item.intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Text("Allow") }
                    }
                }
            }
        }
    }
}

private enum class SetupKind { ACCESSIBILITY, USAGE, OVERLAY, NOTIFICATIONS, EXACT_ALARMS, CALENDAR }

private class SetupItem(
    val kind: SetupKind,
    val title: String,
    val why: String,
    val granted: Boolean,
    val intent: Intent? = null
)

private fun checklistItems(context: Context): List<SetupItem> {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val exactOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    return listOf(
        SetupItem(
            SetupKind.ACCESSIBILITY, "Accessibility",
            "Blocks apps and Reels, and reads which part of an app you are in.",
            FocusAccessibilityService.connected,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        ),
        SetupItem(
            SetupKind.USAGE, "Usage access",
            "Shows your time per app, the same figures Digital Wellbeing uses.",
            UsageRepository(context).hasPermission(),
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        ),
        SetupItem(
            SetupKind.OVERLAY, "Display over other apps",
            "Shows the block and pause screens on top of the app you are trying to open.",
            Settings.canDrawOverlays(context),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        ),
        SetupItem(
            SetupKind.NOTIFICATIONS, "Notifications",
            "Medication, planner and reminder alerts.",
            notificationsAllowedHere(context)
        ),
        SetupItem(
            SetupKind.EXACT_ALARMS, "Exact alarms",
            "Reminders go off at the exact time you set.",
            exactOk,
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri)
        ),
        SetupItem(
            SetupKind.CALENDAR, "Calendar (optional)",
            "The planner reads your events to find free time, and adds events only when you confirm.",
            CalendarSync.hasPermission(context) && WeekCalendar.linkedCalendarCount(context) >= 0
        )
    )
}
