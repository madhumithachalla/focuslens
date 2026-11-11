package com.focuslens

import android.Manifest
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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

/** The parts of the day the planner groups by. The hour is the default when an exact time is set. */
private data class DaySlot(val label: String, val hour: Int)

private val DAY_SLOTS = listOf(
    DaySlot("Morning", 9),
    DaySlot("Afternoon", 14),
    DaySlot("Evening", 19),
    DaySlot("Night", 21)
)

private const val ANY_TIME = "Any time"

private fun slotFor(millis: Long): String {
    if (isAnyTime(millis)) return ANY_TIME
    val h = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "Morning"
        h < 17 -> "Afternoon"
        h < 21 -> "Evening"
        else -> "Night"
    }
}

/**
 * Midnight means no time was set: the item is for "any time that day". Only items with a
 * time chosen by the person get a clock time, so nothing is given a time they did not ask for.
 */
internal fun isAnyTime(millis: Long): Boolean {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    return c.get(Calendar.HOUR_OF_DAY) == 0 && c.get(Calendar.MINUTE) == 0
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
 * The daily planner. Items are tasks, events or reminders. A time is only set when the person
 * chooses one (a reminder needs one). Plan today, or tomorrow from the afternoon. Each day has
 * an intention and a morning and evening journal, saved in the diary.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DailyPlannerScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { TaskRepository(db.taskDao(), db.checkInDao()) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("planner", android.content.Context.MODE_PRIVATE) }

    var audience by remember { mutableStateOf(PlanPrefs.audience(context)) }
    val categories = if (audience == PlanAudience.STUDENT) STUDENT_CATEGORIES else ADULT_CATEGORIES

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

    // Add form. The three sections set what the item is.
    val kinds = PlanKind.values()
    var kindIndex by remember { mutableIntStateOf(0) }
    val kind = kinds[kindIndex]
    var category by remember(audience) { mutableStateOf(categories.first()) }
    var title by remember { mutableStateOf("") }
    // A time is only used when the person sets one. Reminders always need one.
    var setTime by remember(kindIndex) { mutableStateOf(false) }
    val timeOn = kind == PlanKind.REMINDER || setTime
    var slotIndex by remember { mutableIntStateOf(0) }
    val timeState = key(slotIndex) {
        rememberTimePickerState(initialHour = DAY_SLOTS[slotIndex].hour, initialMinute = 0, is24Hour = false)
    }
    var hasCalendar by remember { mutableStateOf(CalendarSync.hasPermission(context)) }
    var addToCalendar by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        addToCalendar = granted
    }

    val scheduledAt = if (!timeOn) {
        dayStart
    } else {
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, timeState.hour)
            set(Calendar.MINUTE, timeState.minute)
        }.timeInMillis
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Daily planner", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(fullDateFmt.format(Date()), fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        Text("I am a", fontSize = 12.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanAudience.values().forEach { a ->
                FilterChip(
                    selected = audience == a,
                    onClick = { audience = a; PlanPrefs.setAudience(context, a) },
                    label = { Text(a.label) }
                )
            }
        }

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
            val groups = listOf(ANY_TIME) + DAY_SLOTS.map { it.label }
            groups.forEach { group ->
                val inGroup = tasks.filter { slotFor(it.scheduledAt) == group }
                if (inGroup.isNotEmpty()) {
                    Text(group, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Gray,
                        modifier = Modifier.padding(top = 6.dp))
                    inGroup.forEach { t ->
                        val itemKind = PlanPrefs.kindOf(context, t.id)
                        PlanRow(
                            task = t,
                            kindLabel = itemKind.label,
                            whenText = if (isAnyTime(t.scheduledAt)) {
                                "${dayFmt.format(Date(t.scheduledAt))} · any time"
                            } else {
                                "${dayFmt.format(Date(t.scheduledAt))} · ${timeFmt.format(Date(t.scheduledAt))}"
                            },
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
                                        TaskReminders.cancel(context, t.id)
                                        PlanPrefs.clearKind(context, t.id)
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
        Spacer(Modifier.height(6.dp))
        TabRow(selectedTabIndex = kindIndex) {
            kinds.forEachIndexed { i, k ->
                Tab(selected = kindIndex == i, onClick = { kindIndex = i }, text = { Text(k.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (kind) {
                PlanKind.TASK -> "A to-do for the day. No time unless you add one."
                PlanKind.EVENT -> "Something happening on the day. Add a time if it has one."
                PlanKind.REMINDER -> "FocusLens notifies you at the time you pick."
            },
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))

        Text("Category", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { t ->
                FilterChip(selected = category == t, onClick = { category = t }, label = { Text(t.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Suggestions (tap to use)", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PLAN_SUGGESTIONS[category].orEmpty().forEach { s ->
                FilterChip(selected = title == s, onClick = { title = s }, label = { Text(s) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What is it?") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        if (kind != PlanKind.REMINDER) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { setTime = !setTime },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = setTime, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
                Text("Set a time", fontSize = 14.sp)
            }
        } else {
            Text("Reminder time", fontSize = 12.sp, color = Color.Gray)
        }
        if (timeOn) {
            Spacer(Modifier.height(4.dp))
            Text("Part of the day", fontSize = 12.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_SLOTS.forEachIndexed { i, s ->
                    FilterChip(selected = slotIndex == i, onClick = { slotIndex = i }, label = { Text(s.label) })
                }
            }
            TimePicker(state = timeState)
            Text(
                "At ${timeFmt.format(Date(scheduledAt))} on ${dayFmt.format(Date(scheduledAt))}",
                fontSize = 12.sp, color = Color.Gray
            )
        } else {
            Text("No time. It will show under Any time for ${dayWord}.", fontSize = 12.sp, color = Color.Gray)
        }

        if (kind == PlanKind.EVENT) {
            Spacer(Modifier.height(6.dp))
            when {
                !timeOn -> Text("Set a time to add this to your calendar.", fontSize = 12.sp, color = Color.Gray)
                hasCalendar -> Row(
                    modifier = Modifier.fillMaxWidth().clickable { addToCalendar = !addToCalendar },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = addToCalendar, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to my calendar", fontSize = 14.sp)
                }
                else -> Row(
                    modifier = Modifier.fillMaxWidth().clickable { permissionLauncher.launch(Manifest.permission.WRITE_CALENDAR) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = false, onCheckedChange = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add to my calendar (tap to allow)", fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            enabled = title.isNotBlank(),
            onClick = {
                val wantsCalendar = kind == PlanKind.EVENT && timeOn && addToCalendar && hasCalendar
                val addedTitle = title.trim()
                val chosenKind = kind
                val at = scheduledAt
                val addedWithTime = timeOn
                scope.launch {
                    val eventId = if (wantsCalendar) {
                        withContext(Dispatchers.IO) { CalendarSync.addEvent(context, addedTitle, at) }
                    } else null
                    val newId = withContext(Dispatchers.IO) {
                        repo.addTask(addedTitle, category, at, null, eventId)
                    }
                    withContext(Dispatchers.IO) {
                        PlanPrefs.setKind(context, newId, chosenKind)
                        if (chosenKind == PlanKind.REMINDER) TaskReminders.schedule(context, newId, addedTitle, at)
                    }
                    reload()
                    title = ""
                    message = buildString {
                        append("${chosenKind.label} added for $dayWord")
                        if (addedWithTime) append(" at ${timeFmt.format(Date(at))}") else append(", no time set")
                        append(".")
                        if (wantsCalendar && eventId != null) append(" It is also on your calendar.")
                        else if (wantsCalendar) append(" Could not add it to your calendar.")
                    }
                }
            }
        ) { Text("Add ${kind.label.lowercase()} to $dayWord") }
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
    kindLabel: String,
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
                    "$kindLabel · ${task.type.label} · $whenText" +
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
