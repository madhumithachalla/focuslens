package com.focuslens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import com.focuslens.data.TaskEntity
import com.focuslens.data.TaskRepository
import com.focuslens.data.TaskType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The parts of the day the planner groups by. The hour is the default when no exact time is set. */
private data class DaySlot(val label: String, val hour: Int)

private val DAY_SLOTS = listOf(
    DaySlot("Morning", 9),
    DaySlot("Afternoon", 14),
    DaySlot("Evening", 19),
    DaySlot("Night", 21)
)

private fun slotFor(millis: Long): String {
    val h = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Morning"
        h < 17 -> "Afternoon"
        h < 21 -> "Evening"
        else -> "Night"
    }
}

/** Midnight that starts the day [offset] days from today, and midnight that ends it. */
private fun dayBounds(offset: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, offset)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
    return start.timeInMillis to end.timeInMillis
}

/**
 * The daily planner. Plan today in the morning and tomorrow later in the day (the switch can
 * be changed either way). Each part of the day has a schedule with date and time, a category
 * and suggestions, an optional calendar entry, and Done. The day also has an intention and
 * a morning and evening journal, saved in the diary.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyPlannerScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { TaskRepository(db.taskDao(), db.checkInDao()) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("planner", Context.MODE_PRIVATE) }

    // Before noon the day to plan is today; from noon on it is usually tomorrow.
    var dayOffset by remember { mutableIntStateOf(if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) >= 12) 1 else 0) }
    val (dayStart, dayEnd) = remember(dayOffset) { dayBounds(dayOffset) }
    val dayWord = if (dayOffset == 0) "today" else "tomorrow"
    val dayKey = remember(dayStart) { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dayStart)) }

    val dayFmt = remember { SimpleDateFormat("EEE d MMM", Locale.US) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val fullDateFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.US) }

    var tasks by remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        tasks = withContext(Dispatchers.IO) { db.taskDao().betweenOnce(dayStart, dayEnd) }.sortedBy { it.scheduledAt }
        loaded = true
    }
    LaunchedEffect(dayStart) {
        message = null
        reload()
    }

    // Intention for the chosen day, kept on the phone.
    val intentionKey = "intention_$dayKey"
    var intention by remember(dayKey) { mutableStateOf(prefs.getString(intentionKey, "") ?: "") }
    var intentionSaved by remember(dayKey) { mutableStateOf(false) }

    // Add form.
    var category by remember { mutableStateOf(TaskType.WELLNESS) }
    var title by remember { mutableStateOf("") }
    var slotIndex by remember { mutableIntStateOf(0) }
    var exactTime by remember { mutableStateOf(false) }
    val timeState = key(slotIndex) {
        rememberTimePickerState(initialHour = DAY_SLOTS[slotIndex].hour, initialMinute = 0, is24Hour = false)
    }
    var hasCalendar by remember { mutableStateOf(CalendarSync.hasPermission(context)) }
    var addToCalendar by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        addToCalendar = granted
    }

    val pickedHour = if (exactTime) timeState.hour else DAY_SLOTS[slotIndex].hour
    val pickedMinute = if (exactTime) timeState.minute else 0
    val scheduledAt = Calendar.getInstance().apply {
        timeInMillis = dayStart
        set(Calendar.HOUR_OF_DAY, pickedHour)
        set(Calendar.MINUTE, pickedMinute)
    }.timeInMillis

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Daily planner", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(fullDateFmt.format(Date()), fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        Text("Plan for", fontSize = 12.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dayOffset == 0, onClick = { dayOffset = 0 }, label = { Text("Today") })
            FilterChip(selected = dayOffset == 1, onClick = { dayOffset = 1 }, label = { Text("Tomorrow") })
        }

        Spacer(Modifier.height(18.dp))
        Text("Intention for $dayWord", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = intention,
            onValueChange = { intention = it; intentionSaved = false },
            label = { Text("One thing that would make $dayWord count") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                prefs.edit().putString(intentionKey, intention.trim()).apply()
                intentionSaved = true
            }) { Text("Save intention") }
            if (intentionSaved) Text("Saved", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(18.dp))
        Text("Schedule for $dayWord", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        if (!loaded) {
            Text("Loading…", color = Color.Gray)
        } else if (tasks.isEmpty()) {
            Text("Nothing planned yet. Add something below.", fontSize = 12.sp, color = Color.Gray)
        } else {
            DAY_SLOTS.forEach { slot ->
                val inSlot = tasks.filter { slotFor(it.scheduledAt) == slot.label }
                if (inSlot.isNotEmpty()) {
                    Text(slot.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp))
                    inSlot.forEach { t ->
                        PlanRow(
                            task = t,
                            whenText = "${dayFmt.format(Date(t.scheduledAt))} · ${timeFmt.format(Date(t.scheduledAt))}",
                            onDone = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { repo.markComplete(t) }
                                    reload()
                                }
                            },
                            onUndo = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { db.taskDao().update(t.copy(completedAt = null)) }
                                    reload()
                                }
                            },
                            onRemove = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        t.calendarEventId?.let { CalendarSync.removeEvent(context, it) }
                                        repo.deleteTask(t.id)
                                    }
                                    reload()
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Add to $dayWord", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("1. Pick a category", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskType.values().forEach { t ->
                FilterChip(selected = category == t, onClick = { category = t }, label = { Text(t.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("2. Pick something, or type your own", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PLAN_SUGGESTIONS[category].orEmpty().forEach { s ->
                FilterChip(selected = title == s, onClick = { title = s }, label = { Text(s) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What do you want to do?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text("3. Part of the day", fontSize = 12.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DAY_SLOTS.forEachIndexed { i, s ->
                FilterChip(selected = slotIndex == i, onClick = { slotIndex = i }, label = { Text(s.label) })
            }
        }
        TextButton(onClick = { exactTime = !exactTime }) {
            Text(if (exactTime) "Use the part-of-day time" else "Set an exact time")
        }
        if (exactTime) TimePicker(state = timeState)
        Text(
            "Scheduled: ${dayFmt.format(Date(scheduledAt))} at ${timeFmt.format(Date(scheduledAt))}",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(Modifier.height(6.dp))
        if (hasCalendar) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { addToCalendar = !addToCalendar },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = addToCalendar, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
                Text("Add to my calendar", fontSize = 14.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { permissionLauncher.launch(Manifest.permission.WRITE_CALENDAR) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = false, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
                Text("Add to my calendar (tap to allow)", fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = title.isNotBlank(),
            onClick = {
                val wantsCalendar = addToCalendar && hasCalendar
                val addedTitle = title.trim()
                scope.launch {
                    val eventId = if (wantsCalendar) {
                        withContext(Dispatchers.IO) { CalendarSync.addEvent(context, addedTitle, scheduledAt) }
                    } else null
                    withContext(Dispatchers.IO) { repo.addTask(addedTitle, category, scheduledAt, null, eventId) }
                    reload()
                    title = ""
                    message = buildString {
                        append("Added to $dayWord at ${timeFmt.format(Date(scheduledAt))}.")
                        if (wantsCalendar && eventId != null) append(" It is also on your calendar.")
                        else if (wantsCalendar) append(" Could not add it to your calendar.")
                    }
                }
            }
        ) { Text("Add to $dayWord") }
        message?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Color.Gray) }

        Spacer(Modifier.height(18.dp))
        Text("Journal for today", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("A few lines in the morning and in the evening. Saved in your diary.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        JournalCard(heading = "Morning journal", templateId = "morning_journal")
        JournalCard(heading = "Evening journal", templateId = "evening_journal")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanRow(
    task: TaskEntity,
    whenText: String,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onRemove: () -> Unit
) {
    val done = task.completedAt != null
    Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                Text(
                    whenText + " · " + task.type.label +
                        if (task.calendarEventId != null) " · On your calendar" else "",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
            if (done) {
                TextButton(onClick = onUndo) { Text("Undo") }
            } else {
                Button(onClick = onDone) { Text("Done") }
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

/** One journal for today, saved as a diary entry with the template's own questions. Editing it replaces today's entry. */
@Composable
private fun JournalCard(heading: String, templateId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val template = remember(templateId) { DIARY_TEMPLATES.first { it.id == templateId } }
    val day = remember { localDayUtc(System.currentTimeMillis()) }

    var existing by remember { mutableStateOf<DiaryEntry?>(null) }
    var answers by remember { mutableStateOf(template.prompts.map { "" }) }
    var loaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(templateId) {
        val found = withContext(Dispatchers.IO) { DiaryStore.load(context) }
            .firstOrNull { it.templateId == templateId && it.entryDate == day }
        existing = found
        if (found != null) {
            answers = template.prompts.indices.map { i -> found.sections.getOrNull(i)?.second.orEmpty() }
        }
        loaded = true
    }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(heading, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (!loaded) {
                Text("Loading…", fontSize = 12.sp, color = Color.Gray)
            } else {
                template.prompts.forEachIndexed { i, prompt ->
                    OutlinedTextField(
                        value = answers[i],
                        onValueChange = { v -> answers = answers.toMutableList().also { it[i] = v } },
                        label = { Text(prompt) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = answers.any { it.isNotBlank() },
                        onClick = {
                            val now = System.currentTimeMillis()
                            val prior = existing
                            val entry = DiaryEntry(
                                id = prior?.id ?: now,
                                createdAt = prior?.createdAt ?: now,
                                entryDate = day,
                                templateId = templateId,
                                title = "",
                                sections = template.prompts.indices.map { i -> template.prompts[i] to answers[i].trim() },
                                attachments = prior?.attachments.orEmpty()
                            )
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    val others = DiaryStore.load(context)
                                        .filterNot { it.templateId == templateId && it.entryDate == day }
                                    DiaryStore.saveAll(context, sortDiary(others + entry))
                                }
                                existing = entry
                                status = "Saved to your diary."
                            }
                        }
                    ) { Text(if (existing == null) "Save" else "Update") }
                    status?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
    }
}
