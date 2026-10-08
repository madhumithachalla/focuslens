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
            onAdd = { title, type ->
                scope.launch {
                    repo.addTask(title, type, System.currentTimeMillis(), null)
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
                Text("To-do", fontSize = 32.sp, fontWeight = FontWeight.Bold)
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
                    onDelete = { scope.launch { repo.deleteTask(task.id) } }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Text("+", fontSize = 24.sp)
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
                Text(task.type.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp, color = Color.Gray)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun AddTaskSheet(onAdd: (String, TaskType) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TaskType.ADMIN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("What do you need to do?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskType.values().forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = { onAdd(title.trim(), type) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
