package com.focuslens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Add / edit / delete / toggle block schedules, set the pause delay, see emergency passes left. */
@Composable
fun BlockSchedulesScreen() {
    val context = LocalContext.current
    val prefs = remember { BlockSchedulePrefs(context) }
    var rev by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<BlockSchedule?>(null) }

    val list = remember(rev) { prefs.schedules() }
    val delay = remember(rev) { prefs.delaySeconds() }
    val passesLeft = remember(rev) { prefs.passesLeft() }
    val perDay = remember(rev) { prefs.passesPerDay() }

    val e = editing
    if (e != null) {
        ScheduleEditor(
            initial = e,
            onSave = {
                prefs.upsert(it)
                editing = null
                rev++
            },
            onCancel = { editing = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Block schedules", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Your blocked apps (Focus tab) are shut during these windows.",
                fontSize = 13.sp
            )
        }
        item {
            Text("Pause before emergency pass", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (s in listOf(0, 5, 10, 30)) {
                    FilterChip(
                        selected = delay == s,
                        onClick = {
                            prefs.setDelaySeconds(s)
                            rev++
                        },
                        label = { Text("${s}s") }
                    )
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Emergency passes left today: $passesLeft of $perDay", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    prefs.setPassesPerDay(perDay - 1)
                    rev++
                }) { Text("-") }
                TextButton(onClick = {
                    prefs.setPassesPerDay(perDay + 1)
                    rev++
                }) { Text("+") }
            }
        }
        item {
            Button(
                onClick = {
                    editing = BlockSchedule(
                        id = System.currentTimeMillis().toString(),
                        name = "",
                        startMinutes = 21 * 60,
                        endMinutes = 0,
                        days = (1..7).toSet(),
                        strict = false,
                        enabled = true
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add schedule") }
        }
        items(list, key = { it.id }) { s ->
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifEmpty { "Schedule" }, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            formatMinutes(s.startMinutes) + " - " + formatMinutes(s.endMinutes) +
                                (if (s.strict) " - strict" else ""),
                            fontSize = 13.sp
                        )
                        Text(
                            s.days.sorted().joinToString(" ") { DAY_LABELS[it - 1] },
                            fontSize = 12.sp
                        )
                    }
                    Switch(checked = s.enabled, onCheckedChange = {
                        prefs.upsert(s.copy(enabled = it))
                        rev++
                    })
                }
                Row {
                    TextButton(onClick = { editing = s }) { Text("Edit") }
                    TextButton(onClick = {
                        prefs.delete(s.id)
                        rev++
                    }) { Text("Delete") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TimeStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(((minutes - 15) % 1440 + 1440) % 1440) }) { Text("-15m") }
        Text(formatMinutes(minutes), modifier = Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange((minutes + 15) % 1440) }) { Text("+15m") }
    }
}

@Composable
private fun ScheduleEditor(
    initial: BlockSchedule,
    onSave: (BlockSchedule) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var start by remember { mutableIntStateOf(initial.startMinutes) }
    var end by remember { mutableIntStateOf(initial.endMinutes) }
    var days by remember { mutableStateOf(initial.days) }
    var strict by remember { mutableStateOf(initial.strict) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Edit schedule", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name (e.g. Mindful Morning)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TimeStepper("From", start) { start = it }
        TimeStepper("Until", end) { end = it }
        Text("Windows that end before they start run past midnight.", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in 1..7) {
                FilterChip(
                    selected = d in days,
                    onClick = { days = if (d in days) days - d else days + d },
                    label = { Text(DAY_LABELS[d - 1].take(1)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Strict (no emergency pass)", modifier = Modifier.weight(1f))
            Switch(checked = strict, onCheckedChange = { strict = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            startMinutes = start,
                            endMinutes = end,
                            days = days,
                            strict = strict
                        )
                    )
                },
                enabled = days.isNotEmpty() && start != end
            ) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
