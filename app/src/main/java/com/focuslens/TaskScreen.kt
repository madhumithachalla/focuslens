package com.focuslens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.focuslens.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TaskScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { TaskRepository(db.taskDao(), db.checkInDao()) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    var reflection by remember { mutableStateOf<WeeklyReflection?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var missReasonFor by remember { mutableStateOf<TaskEntity?>(null) }

    suspend fun refresh() {
        reflection = repo.weeklyReflection()
    }

    LaunchedEffect(Unit) {
        db.taskDao().all().collect { tasks = it }
    }
    LaunchedEffect(Unit) { refresh() }

    if (showAdd) {
        AddTaskSheet(
            onAdd = { title, type, scheduledAt, addToCalendar ->
                scope.launch {
                    val eventId = if (addToCalendar) CalendarSync.addEvent(context, title, scheduledAt) else null
                    repo.addTask(title, type, scheduledAt, null, eventId)
                    showAdd = false
                }
            },
            onDismiss = { showAdd = false }
        )
        return
    }

    missReasonFor?.let { task ->
        MissReasonSheet(
            taskTitle = task.title,
            onPick = { reason, note ->
                scope.launch {
                    repo.markIncomplete(task, reason, note)
                    missReasonFor = null
                    refresh()
                }
            },
            onDismiss = { missReasonFor = null }
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("To-do", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }

            item {
                val r = reflection
                if (r != null && (r.completed > 0 || r.missed > 0)) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("THIS WEEK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${r.completed} completed · ${r.missed} missed",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            r.suggestion?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, fontSize = 12.5.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (tasks.isEmpty()) {
                item { Text("Nothing added yet — tap + to add your first task.", fontSize = 13.sp, color = Color.Gray) }
            }

            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = {
                        if (task.completedAt == null) {
                            scope.launch { repo.markComplete(task); refresh() }
                        } else {
                            missReasonFor = task
                        }
                    },
                    onDelete = {
                        scope.launch {
                            task.calendarEventId?.let { CalendarSync.removeEvent(context, it) }
                            repo.deleteTask(task.id)
                        }
                    }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.completedAt != null, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.completedAt != null) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                Text(task.type.label, fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** Common things people do, grouped by category. Shared by the To-do sheet and the daily planner. */
internal val PLAN_SUGGESTIONS: Map<TaskType, List<String>> = mapOf(
    TaskType.STUDY to listOf("Study 25 minutes", "Read 10 pages", "Review today's notes", "Practise one problem"),
    TaskType.ADMIN to listOf("Check emails", "Reply to important emails", "Pay a bill", "Sort my downloads", "Call the bank or provider"),
    TaskType.WELLNESS to listOf("Walk 10 minutes", "Drink a glass of water", "Stretch for 5 minutes", "Take my medication", "Get some sunlight", "Be in bed by 11pm"),
    TaskType.WORK to listOf("Apply for jobs", "Update my CV", "Reply to a recruiter", "Prepare for an interview", "Follow up on an application"),
    TaskType.HOME to listOf("Do laundry", "Tidy one small area", "Wash the dishes", "Grocery shopping", "Cook a meal", "Change the bed sheets"),
    TaskType.SOCIAL to listOf("Call someone I love", "Message a friend", "Plan a catch-up", "Thank someone")
)

@OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
private fun AddTaskSheet(onAdd: (String, TaskType, Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TaskType.ADMIN) }
    var addToCalendar by remember { mutableStateOf(false) }
    var hasCalendarPermission by remember { mutableStateOf(CalendarSync.hasPermission(context)) }

    val cal = remember { java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 1) } }
    var dateMillis by remember { mutableStateOf(cal.timeInMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    // The quick time buttons change the picker by building a new one (new key), because the
    // picker's hour can't be assigned directly.
    var presetKey by remember { mutableStateOf(0) }
    var presetHour by remember { mutableStateOf(cal.get(java.util.Calendar.HOUR_OF_DAY)) }
    var presetMinute by remember { mutableStateOf(cal.get(java.util.Calendar.MINUTE)) }
    val timeState = key(presetKey) {
        rememberTimePickerState(initialHour = presetHour, initialMinute = presetMinute, is24Hour = false)
    }

    fun scheduledAtMillis(): Long {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
        c.set(java.util.Calendar.HOUR_OF_DAY, timeState.hour)
        c.set(java.util.Calendar.MINUTE, timeState.minute)
        c.set(java.util.Calendar.SECOND, 0)
        return c.timeInMillis
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        addToCalendar = granted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add task") },
        text = {
            // Scrolls, so the Add and Cancel buttons stay on screen however much is shown.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Category", fontSize = 12.sp, color = Color.Gray)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Suggestions (tap to use)", fontSize = 12.sp, color = Color.Gray)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PLAN_SUGGESTIONS[type].orEmpty().forEach { sug ->
                        FilterChip(selected = title == sug, onClick = { title = sug }, label = { Text(sug) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What do you need to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("When (optional)", fontSize = 12.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Morning" to 9, "Afternoon" to 14, "Evening" to 19).forEach { (label, hour) ->
                        FilterChip(
                            selected = timeState.hour == hour && timeState.minute == 0,
                            onClick = { presetHour = hour; presetMinute = 0; presetKey++ },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (!addToCalendar && !hasCalendarPermission) {
                            permissionLauncher.launch(android.Manifest.permission.WRITE_CALENDAR)
                        } else {
                            addToCalendar = !addToCalendar
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = addToCalendar, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to Calendar", fontSize = 14.sp)
                }
                if (addToCalendar) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("When:", fontSize = 13.sp, color = Color.Gray)
                        TextButton(onClick = { showDatePicker = true }) { Text(formatPickedDate(dateMillis)) }
                    }
                    TimePicker(state = timeState)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onAdd(title.trim(), type, if (addToCalendar) scheduledAtMillis() else System.currentTimeMillis(), addToCalendar) }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (showDatePicker) {
        DatePickerDialogPicker(
            initial = dateMillis,
            onPicked = { dateMillis = it; showDatePicker = false },
            onDismiss = { showDatePicker = false }
        )
    }
}

@Composable
private fun MissReasonSheet(taskTitle: String, onPick: (MissReason?, String?) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<MissReason?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What got in the way?") },
        text = {
            Column {
                Text(taskTitle, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        MissReason.TIRED to "Tired",
                        MissReason.DISTRACTED to "Distracted",
                        MissReason.TOO_BIG to "Too big",
                        MissReason.FORGOT to "Forgot"
                    ).forEach { (r, label) ->
                        FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Anything else? (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onPick(reason ?: MissReason.OTHER, note.ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
