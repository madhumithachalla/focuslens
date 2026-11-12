package com.focuslens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun fmtDayTime(ms: Long): String = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(ms))
private fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
private fun fmtHours(h: Double): String {
    val total = Math.round(h * 60).toInt()
    return "${total / 60}h ${total % 60}m"
}

/** Parses "HH:mm" into (hour, minute), or null. */
private fun parseHm(s: String): Pair<Int, Int>? {
    val m = Regex("""^(\d{1,2}):(\d{2})$""").find(s.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) h to min else null
}

/**
 * Builds a night from a wake-up date and two clock times. A bedtime in the afternoon or evening
 * is taken to be the day before the wake-up date.
 */
internal fun nightFromInputs(wakeDayMs: Long, bed: Pair<Int, Int>, wake: Pair<Int, Int>): SleepNight {
    val wakeCal = Calendar.getInstance().apply {
        timeInMillis = wakeDayMs
        set(Calendar.HOUR_OF_DAY, wake.first); set(Calendar.MINUTE, wake.second)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val bedCal = Calendar.getInstance().apply {
        timeInMillis = wakeDayMs
        set(Calendar.HOUR_OF_DAY, bed.first); set(Calendar.MINUTE, bed.second)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (bed.first >= 12) add(Calendar.DAY_OF_MONTH, -1)
    }
    return SleepNight(bedCal.timeInMillis, wakeCal.timeInMillis, manual = true)
}

/** Sleep, estimated from screen use, with your corrections. Not a medical measure. */
@Composable
fun SleepScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(SleepStore.enabled(context)) }
    var nights by remember { mutableStateOf<List<SleepNight>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<SleepNight?>(null) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val hasUsage = UsageRepository(context).hasPermission()

    LaunchedEffect(enabled, tick) {
        val now = System.currentTimeMillis()
        nights = withContext(Dispatchers.IO) {
            if (enabled && hasUsage) SleepStore.refresh(context, now) else SleepStore.nights(context)
        }
        loaded = true
    }

    val now = System.currentTimeMillis()
    val ongoing = nights.firstOrNull { it.ongoing }
    val week = nights.filter { !it.ongoing && it.kind != "break" && now - it.endMs < 7 * 86_400_000L }
    val recent = nights.filter { !it.ongoing }.sortedByDescending { it.endMs }.take(14)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Sleep", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Estimated from when your screen goes off and on at night. It is not a measurement and can be wrong. " +
                "Correct any night you know. Everything stays on this phone.",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))

        if (!enabled) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Turn on sleep tracking?", fontWeight = FontWeight.Medium)
                    Text(
                        "FocusLens reads only the times your screen turns on and off. It does not record what you do. " +
                            "You can turn it off at any time, and your corrections stay if you do.",
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        SleepStore.setEnabled(context, true)
                        enabled = true
                    }) { Text("Turn on") }
                }
            }
            return@Column
        }

        if (!hasUsage) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Usage access is needed to read screen times.", fontSize = 13.sp)
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Allow usage access") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (ongoing != null) {
                    Text("Asleep since ${fmtTime(ongoing.startMs)} (estimated)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Using your phone ends it.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text("Awake", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                if (week.isEmpty()) {
                    Text("No nights estimated in the last week yet.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    val avg = week.map { it.hours }.average()
                    Text("Average last 7 nights: ${fmtHours(avg)} (${week.size} nights)", fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Last 14 nights", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("Tap a night to correct it.", fontSize = 12.sp, color = Color.Gray)
        if (!loaded) {
            Text("Loading…", color = Color.Gray)
        } else if (recent.isEmpty()) {
            Text("No nights yet. A night shows up after a long quiet stretch with the screen off.", fontSize = 12.sp, color = Color.Gray)
        }
        recent.forEach { n ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { editing = n }) {
                Column(Modifier.padding(12.dp)) {
                    Text("${fmtDayTime(n.startMs)} → ${fmtTime(n.endMs)}", fontSize = 14.sp)
                    Text(
                        fmtHours(n.hours) + when (n.kind) {
                            "break" -> " · a break, not sleep"
                            "confirmed" -> " · you confirmed this"
                            "corrected" -> " · you corrected this"
                            else -> " · estimated"
                        },
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { adding = true }) { Text("+ Add a night") }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = {
            SleepStore.setEnabled(context, false)
            enabled = false
            message = "Sleep tracking is off. Your corrections are kept."
        }) { Text("Turn sleep tracking off", color = Color.Gray) }
        message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { night ->
        NightDialog(
            title = if (night.manual) "Your correction" else "Correct this night",
            initialBed = fmtTime(night.startMs),
            initialWake = fmtTime(night.endMs),
            onCancel = { editing = null },
            onSave = { bed, wake ->
                val built = nightFromInputs(night.endMs, bed, wake)
                scope.launch(Dispatchers.IO) {
                    SleepStore.addManual(context, built.startMs, built.endMs)
                    if (night.manual) SleepStore.deleteManual(context, night)
                }
                editing = null
                tick++
            },
            onDelete = if (night.manual) {
                {
                    scope.launch(Dispatchers.IO) { SleepStore.deleteManual(context, night) }
                    editing = null
                    tick++
                }
            } else null
        )
    }

    if (adding) {
        var dayMs by remember { mutableStateOf<Long?>(null) }
        var picking by remember { mutableStateOf(false) }
        NightDialog(
            title = "Add a night",
            initialBed = "23:00",
            initialWake = "07:00",
            extra = {
                OutlinedButton(onClick = { picking = true }) {
                    Text(dayMs?.let { "Woke up on ${SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(it))}" } ?: "Pick the day you woke up")
                }
            },
            onCancel = { adding = false },
            onSave = { bed, wake ->
                val day = dayMs
                if (day != null) {
                    val built = nightFromInputs(day, bed, wake)
                    scope.launch(Dispatchers.IO) { SleepStore.addManual(context, built.startMs, built.endMs) }
                    adding = false
                    tick++
                }
            },
            onDelete = null
        )
        if (picking) {
            DatePickerDialogPicker(
                initial = null,
                onPicked = { millis ->
                    // The picker gives UTC midnight; use the same calendar day in local time.
                    val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
                    dayMs = Calendar.getInstance().apply {
                        set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    picking = false
                },
                onDismiss = { picking = false }
            )
        }
    }
}

@Composable
internal fun NightDialog(
    title: String,
    initialBed: String,
    initialWake: String,
    extra: (@Composable () -> Unit)? = null,
    onCancel: () -> Unit,
    onSave: (Pair<Int, Int>, Pair<Int, Int>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var bed by remember { mutableStateOf(initialBed) }
    var wake by remember { mutableStateOf(initialWake) }
    val bedParsed = parseHm(bed)
    val wakeParsed = parseHm(wake)
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                extra?.invoke()
                OutlinedTextField(bed, { bed = it }, label = { Text("Went to sleep (HH:mm)") }, singleLine = true, isError = bedParsed == null)
                OutlinedTextField(wake, { wake = it }, label = { Text("Woke up (HH:mm)") }, singleLine = true, isError = wakeParsed == null)
                if (bedParsed == null || wakeParsed == null) {
                    Text("Use 24-hour time, like 23:30 or 07:00.", fontSize = 12.sp, color = Color(0xFFB00020))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bedParsed != null && wakeParsed != null,
                onClick = { onSave(bedParsed!!, wakeParsed!!) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Remove") } }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}
