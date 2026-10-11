package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AlarmEntity
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.launch

private val DAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val dao = remember { db.alarmDao() }
    val scope = rememberCoroutineScope()

    var alarms by remember { mutableStateOf<List<AlarmEntity>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { dao.all().collect { alarms = it } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("Alarms", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "A real alarm — fires even if FocusLens is fully closed or your phone is locked.",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))
            }

            if (alarms.isEmpty()) {
                item { Text("No alarms set yet.", color = Color.Gray) }
            }

            items(alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    onToggle = { enabled ->
                        scope.launch {
                            dao.setEnabled(alarm.id, enabled)
                            if (enabled) AlarmScheduler.schedule(context, alarm.copy(enabled = true))
                            else AlarmScheduler.cancel(context, alarm.id)
                        }
                    },
                    onDelete = {
                        scope.launch {
                            AlarmScheduler.cancel(context, alarm.id)
                            dao.delete(alarm.id)
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) { Text("+", fontSize = 24.sp) }
    }

    if (showAdd) {
        AddAlarmSheet(
            onAdd = { hour, minute, label, days ->
                scope.launch {
                    val entity = AlarmEntity(hour = hour, minute = minute, label = label, days = AlarmScheduler.joinDays(days))
                    val id = dao.insert(entity)
                    AlarmScheduler.schedule(context, entity.copy(id = id))
                    showAdd = false
                }
            },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun AlarmRow(alarm: AlarmEntity, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val days = AlarmScheduler.parseDays(alarm.days)
    val daysText = if (days.isEmpty()) "Once" else days.sorted().joinToString(" ") { DAY_LABELS[it] }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    String.format("%02d:%02d", alarm.hour, alarm.minute) + " — " + alarm.label,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(daysText, fontSize = 12.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alarm.enabled, onCheckedChange = onToggle)
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAlarmSheet(
    onAdd: (hour: Int, minute: Int, label: String, days: List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val timeState = rememberTimePickerState(is24Hour = false)
    var label by remember { mutableStateOf("") }
    val selectedDays = remember { mutableStateListOf<Int>() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("New alarm", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            TimePicker(state = timeState)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Text("Repeat (leave all off for a one-time alarm)", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            FlowRowChips(selectedDays)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onAdd(timeState.hour, timeState.minute, label.ifBlank { "Alarm" }, selectedDays.toList())
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add alarm") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(selectedDays: androidx.compose.runtime.snapshots.SnapshotStateList<Int>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DAY_LABELS.forEachIndexed { idx, label ->
            FilterChip(
                selected = selectedDays.contains(idx),
                onClick = {
                    if (selectedDays.contains(idx)) selectedDays.remove(idx) else selectedDays.add(idx)
                },
                label = { Text(label) }
            )
        }
    }
}
