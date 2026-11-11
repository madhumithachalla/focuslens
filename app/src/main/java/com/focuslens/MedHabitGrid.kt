package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val GRID_DAYS = 28
private val CELL = 34.dp

/** One day in the grid. [doses] maps each scheduled time ("HH:mm") to when it was taken, or null. */
private class HabitDay(val cal: Calendar, val daysAgo: Int, val doses: Map<String, Long?>)

/**
 * A 4-week habit tracker for one medication: a row per time of day, a column per day.
 * Tap a scheduled cell to record when it was taken, or that it was not taken.
 * Everything is stored on the phone, in the same log the Today list uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedHabitGrid(plan: MedPlan, tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MedPrefs(context) }
    val rows = remember(plan) { plan.times.mapNotNull { MedLogic.normalizeTime(it) }.distinct().sorted() }
    val days = remember(tick, plan) {
        (GRID_DAYS - 1 downTo 0).map { back ->
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -back) }
            val doses = prefs.dosesOn(cal).filter { it.first.id == plan.id }
            HabitDay(cal, back, doses.associate { it.second to it.third })
        }
    }
    val nowCal = Calendar.getInstance()
    val nowMinute = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val dayNumFmt = remember { SimpleDateFormat("d", Locale.US) }
    var selected by remember { mutableStateOf<Pair<HabitDay, String>?>(null) }

    if (rows.isEmpty()) return

    Column(Modifier.padding(top = 10.dp)) {
        Text("Last 4 weeks · tap a box to record", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(54.dp)) {
                Spacer(Modifier.height(18.dp))
                rows.forEach { t ->
                    Box(Modifier.height(CELL).width(54.dp), contentAlignment = Alignment.CenterStart) {
                        Text(t, fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(18.dp).width(CELL), contentAlignment = Alignment.Center) {
                        Text(dayNumFmt.format(d.cal.time), fontSize = 10.sp, color = Color.Gray)
                    }
                    rows.forEach { t ->
                        val scheduled = d.doses.containsKey(t)
                        val takenAt = d.doses[t]
                        val state = when {
                            !scheduled -> null
                            takenAt != null -> DoseState.TAKEN
                            d.daysAgo > 0 -> DoseState.MISSED
                            else -> MedLogic.doseState(nowMinute, MedLogic.minutesOf(t), null)
                        }
                        val (bg, symbol, ink) = when (state) {
                            DoseState.TAKEN -> Triple(Color(0xFF3A7D5C), "✓", Color.White)
                            DoseState.MISSED -> Triple(Color(0xFFF3D6D9), "✕", Color(0xFFB00020))
                            DoseState.DUE -> Triple(Color(0xFFFFE9B0), "•", Color(0xFF6B4E00))
                            DoseState.UPCOMING -> Triple(Color(0xFFE8E6E1), "", Color.Gray)
                            null -> Triple(Color.Transparent, "", Color.Gray)
                        }
                        Box(
                            Modifier
                                .padding(2.dp)
                                .size(CELL - 4.dp)
                                .background(bg, RoundedCornerShape(6.dp))
                                .then(if (scheduled) Modifier.clickable { selected = d to t } else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(symbol, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("✓ taken   ✕ missed   • due now   empty box = not scheduled", fontSize = 11.sp, color = Color.Gray)
    }

    selected?.let { (day, time) ->
        DoseDialog(
            plan = plan,
            day = day,
            time = time,
            onDone = { selected = null; onChanged() },
            onDismiss = { selected = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseDialog(plan: MedPlan, day: HabitDay, time: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MedPrefs(context) }
    val key = MedLogic.doseKey(plan.id, time)
    val existing = day.doses[time]
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale.US) }
    val scheduledHour = time.substringBefore(":").toInt()
    val scheduledMinute = time.substringAfter(":").toInt()
    val startHour = existing?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY) } ?: scheduledHour
    val startMinute = existing?.let { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.MINUTE) } ?: scheduledMinute
    val timeState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plan.name) },
        text = {
            Column {
                Text("Scheduled $time on ${dateFmt.format(day.cal.time)}", fontSize = 13.sp, color = Color.Gray)
                Text(
                    if (existing == null) "Not recorded yet."
                    else "Recorded as taken at ${timeFmt.format(java.util.Date(existing))}.",
                    fontSize = 13.sp, color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Text("Time it was taken", fontSize = 12.sp, color = Color.Gray)
                TimePicker(state = timeState)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val taken = (day.cal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, timeState.hour)
                    set(Calendar.MINUTE, timeState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                prefs.setTakenOn(day.cal, key, taken)
                onDone()
            }) { Text("Mark taken") }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { prefs.setTakenOn(day.cal, key, null); onDone() }) { Text("Not taken") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
