package com.focuslens

import android.Manifest
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Calendar.DAY_OF_WEEK values in Monday-first order. */
private val CAL_WEEKDAYS = listOf(
    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
)

/**
 * The weekly planner. One "Next up" task at a time, the seven days of the week (name, reorder,
 * template), routines, a task list, and calendar help. Nothing is added to the calendar or the
 * plan without a confirmation step.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekPlanScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf(WeekPlanStore.load(context)) }
    var editingDay by remember { mutableStateOf<Int?>(null) }
    var addingTask by remember { mutableStateOf(false) }
    var addingRoutine by remember { mutableStateOf(false) }
    var findTimeFor by remember { mutableStateOf<PlanItem?>(null) }
    var calendarTick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun commit(p: WeekPlan) {
        plan = p
        WeekPlanStore.save(context, p)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { calendarTick++ }

    val todayIndex = WeekLogic.dayIndex(Calendar.getInstance())
    val now = System.currentTimeMillis()
    val suggestion = WeekLogic.nextSuggestion(plan, now)
    val todayProtected = plan.days.getOrNull(todayIndex)?.protectedDay == true

    editingDay?.let { i ->
        DayEditor(
            index = i,
            day = plan.days[i],
            onCancel = { editingDay = null },
            onSave = { updated ->
                commit(plan.copy(days = plan.days.toMutableList().also { it[i] = updated }))
                editingDay = null
            }
        )
        return
    }
    if (addingTask) {
        TaskForm(
            onCancel = { addingTask = false },
            onSave = { item ->
                commit(plan.copy(items = plan.items + item))
                addingTask = false
                message = "Task added."
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Week planner", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("One thing at a time. Change anything here later.", fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(14.dp))

        // Next up: a single task, so there is one clear thing to look at.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("NEXT UP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                val s = suggestion
                when {
                    todayProtected -> Text("Today is protected for planning and rest. Nothing is pushed on you.", fontSize = 14.sp)
                    s == null -> Text("Nothing is waiting. Add a task below when something comes up.", fontSize = 14.sp)
                    else -> {
                        Text(s.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "${s.kind.label} · ${s.minutes} min" +
                                (if (s.startTime.isNotBlank()) " · ${s.startTime}" else ""),
                            fontSize = 12.sp, color = Color.Gray
                        )
                        Text(WeekLogic.reasonFor(s), fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                commit(plan.copy(items = plan.items.map { if (it.id == s.id) it.copy(done = true) else it }))
                            }) { Text("Done") }
                            OutlinedButton(onClick = {
                                // Hide it for three hours. It comes back on its own; nothing is lost.
                                val later = now + 3 * 60 * 60 * 1000L
                                commit(plan.copy(items = plan.items.map { if (it.id == s.id) it.copy(snoozeUntil = later) else it }))
                            }) { Text("Not now") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("This week", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text("Tap a day to name it, pick a template, or fill it in.", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        plan.days.forEachIndexed { i, d ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { editingDay = i }) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                DAY_SHORT[i] + "  " + d.theme.ifBlank { "Name this day" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (d.theme.isBlank()) Color.Gray else Color.Unspecified
                            )
                            val templateText = if (d.template == DayTemplate.NONE) "No template" else d.template.label
                            val todayTag = if (i == todayIndex) " · today" else ""
                            val protectedTag = if (d.protectedDay) " · protected" else ""
                            Text(templateText + todayTag + protectedTag, fontSize = 12.sp, color = Color.Gray)
                        }
                        TextButton(enabled = i > 0, onClick = {
                            val list = plan.days.toMutableList()
                            list[i - 1] = list[i].also { list[i] = list[i - 1] }
                            commit(plan.copy(days = list))
                        }) { Text("▲") }
                        TextButton(enabled = i < 6, onClick = {
                            val list = plan.days.toMutableList()
                            list[i + 1] = list[i].also { list[i] = list[i + 1] }
                            commit(plan.copy(days = list))
                        }) { Text("▼") }
                    }
                    if (d.suggested) {
                        Text("Suggested for you. Keep it or remove it.", fontSize = 12.sp, color = Color(0xFF2E7D5B))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                commit(plan.copy(days = plan.days.toMutableList().also { it[i] = d.copy(suggested = false) }))
                            }) { Text("Keep") }
                            TextButton(onClick = {
                                commit(plan.copy(days = plan.days.toMutableList().also {
                                    it[i] = WeekDay("", DayTemplate.NONE, emptyMap())
                                }))
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Routines today", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val todayKey = WeekLogic.dayKey(now)
        val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val doneToday = plan.routineDone[todayKey].orEmpty()
        val todaysRoutines = plan.routines.filter { it.weekday == todayDow }
        if (todaysRoutines.isEmpty()) {
            Text("No routines for today. Repeating chores (like weekly laundry or the bins) go here.", fontSize = 12.sp, color = Color.Gray)
        }
        todaysRoutines.forEach { r ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    val ids = if (r.id in doneToday) doneToday - r.id else doneToday + r.id
                    commit(plan.copy(routineDone = plan.routineDone + (todayKey to ids)))
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = r.id in doneToday, onCheckedChange = null)
                Text(r.title, fontSize = 15.sp)
            }
        }
        if (addingRoutine) {
            RoutineForm(
                onCancel = { addingRoutine = false },
                onSave = { r ->
                    commit(plan.copy(routines = plan.routines + r))
                    addingRoutine = false
                }
            )
        } else {
            TextButton(onClick = { addingRoutine = true }) { Text("+ Add a routine") }
        }

        Spacer(Modifier.height(18.dp))
        Text("Tasks", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val open = plan.items.filter { !it.done }.sortedWith(
            compareBy<PlanItem>({ it.kind.rank }).thenBy { if (it.dueDay.isBlank()) "99999999" else it.dueDay }
        )
        if (open.isEmpty()) {
            Text("No open tasks.", fontSize = 12.sp, color = Color.Gray)
        }
        open.forEach { t ->
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        t.kind.label + " · ${t.minutes} min · " + t.repeat.label.lowercase() +
                            (if (t.dueDay.isNotBlank()) " · due ${WeekLogic.prettyDay(t.dueDay)}" else ""),
                        fontSize = 12.sp, color = Color.Gray
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            commit(plan.copy(items = plan.items.map { if (it.id == t.id) it.copy(done = true) else it }))
                        }) { Text("Done") }
                        if (t.minutes > 0) {
                            TextButton(onClick = { findTimeFor = t }) { Text("Find a time") }
                        }
                    }
                }
            }
        }
        if (!addingTask) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { addingTask = true }) { Text("+ Add a task") }
        }
        message?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Color.Gray) }

        Spacer(Modifier.height(18.dp))
        Text("Calendar", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val linked = remember(calendarTick) { WeekCalendar.linkedCalendarCount(context) }
        when {
            linked < 0 -> {
                Text("FocusLens needs calendar access to see your events and find free time.", fontSize = 12.sp, color = Color.Gray)
                OutlinedButton(onClick = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }) { Text("Allow calendar access") }
            }
            linked == 0 -> {
                Text("No calendar is linked to this phone yet. Link your Google or other calendar, then come back here.", fontSize = 12.sp, color = Color.Gray)
                OutlinedButton(onClick = {
                    context.startActivity(android.content.Intent(Settings.ACTION_SYNC_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Link a calendar") }
            }
            else -> Text("Connected: $linked calendar(s). Events are checked before any time is suggested. Nothing is added without your OK.", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(24.dp))
    }

    findTimeFor?.let { item ->
        FindTimeDialog(
            item = item,
            onClose = { findTimeFor = null },
            onBooked = { updated, text ->
                commit(plan.copy(items = plan.items.map { if (it.id == updated.id) updated else it }))
                findTimeFor = null
                message = text
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayEditor(index: Int, day: WeekDay, onCancel: () -> Unit, onSave: (WeekDay) -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var theme by remember { mutableStateOf(day.theme) }
    var template by remember { mutableStateOf(day.template) }
    var fields by remember { mutableStateOf(day.fields) }
    var protectedDay by remember { mutableStateOf(day.protectedDay) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val suggested = DayTemplate.suggestFor(theme)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onCancel) { Text("← Back") }
        Text(DAY_NAMES[index], fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = theme,
            onValueChange = { theme = it },
            label = { Text("Theme for this day") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (suggested != DayTemplate.NONE && suggested != template) {
            TextButton(onClick = { template = suggested }) {
                Text("This looks like ${suggested.label}. Use that template?")
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Template", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayTemplate.values().forEach { t ->
                FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.label) })
            }
        }

        if (template == DayTemplate.FUN_SELF_CARE) {
            Spacer(Modifier.height(8.dp))
            Text("Pick one", fontSize = 12.sp, color = Color.Gray)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayTemplate.FUN_CHOICES.forEach { c ->
                    FilterChip(
                        selected = fields["choice"] == c,
                        onClick = { fields = fields + ("choice" to c) },
                        label = { Text(c) }
                    )
                }
            }
        }

        template.fields.filter { !(template == DayTemplate.FUN_SELF_CARE && it.first == "choice") }.forEach { (key, label) ->
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = fields[key].orEmpty(),
                onValueChange = { v -> fields = fields + (key to v) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                minLines = if (key in setOf("grocery", "meals", "review", "priorities", "top3", "plan")) 2 else 1
            )
        }

        if (template == DayTemplate.PLANNING_REST) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Protect this day", fontSize = 14.sp)
                    Text("Other tasks are not suggested on a protected day.", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(checked = protectedDay, onCheckedChange = { protectedDay = it })
            }
            TextButton(onClick = {
                val events = WeekCalendar.linkedCalendarCount(context)
                message = if (events < 0) "Allow calendar access in the Calendar section first."
                else "Check the calendar section for the week's events."
            }) { Text("Check my calendar") }
        }

        message?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { confirm = true }) { Text("Save day") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Save ${DAY_NAMES[index]}?") },
            text = { Text("This sets the theme and template for the day. You can change it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSave(WeekDay(theme.trim(), template, fields.filterKeys { k -> template.fields.any { it.first == k } }, suggested = false, protectedDay = protectedDay && template == DayTemplate.PLANNING_REST))
                }) { Text("Yes, save") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskForm(onCancel: () -> Unit, onSave: (PlanItem) -> Unit) {
    var kind by remember { mutableStateOf(TaskKind.CONSEQUENCE) }
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("15") }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var priority by remember { mutableIntStateOf(2) }
    var dueDay by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pickingDue by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        TextButton(onClick = onCancel) { Text("← Back") }
        Text("New task", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("What kind is it?", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TaskKind.values().forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) })
            }
        }
        Text(kind.hint, fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(title, { title = it }, label = { Text("Task") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(minutes, { minutes = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("Estimated minutes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Repeat", fontSize = 12.sp, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Repeat.values().forEach { r -> FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(r.label) }) }
        }
        Spacer(Modifier.height(8.dp))
        Text("Priority", fontSize = 12.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "High", 2 to "Medium", 3 to "Low").forEach { (p, label) ->
                FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickingDue = true }) {
                Text(if (dueDay.isBlank()) "Due date (optional)" else "Due ${WeekLogic.prettyDay(dueDay)}")
            }
            if (dueDay.isNotBlank()) TextButton(onClick = { dueDay = "" }) { Text("Clear") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(startTime, { startTime = it.take(5) }, label = { Text("Start time HH:mm (only if you want one)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = title.isNotBlank(), onClick = { confirm = true }) { Text("Review") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }

    if (pickingDue) {
        DatePickerDialogPicker(
            initial = null,
            onPicked = { millis ->
                dueDay = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
                pickingDue = false
            },
            onDismiss = { pickingDue = false }
        )
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Add this task?") },
            text = {
                Text(
                    "${title.trim()} · ${kind.label} · ${minutes.ifBlank { "0" }} min · ${repeat.label.lowercase()}" +
                        (if (dueDay.isNotBlank()) " · due ${WeekLogic.prettyDay(dueDay)}" else "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onSave(
                        PlanItem(
                            id = System.currentTimeMillis(),
                            title = title.trim(),
                            kind = kind,
                            minutes = minutes.toIntOrNull() ?: 0,
                            repeat = repeat,
                            priority = priority,
                            dueDay = dueDay,
                            startTime = startTime.trim(),
                            notes = notes.trim(),
                            done = false,
                            snoozeUntil = 0L
                        )
                    )
                }) { Text("Yes, add it") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Not yet") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineForm(onCancel: () -> Unit, onSave: (Routine) -> Unit) {
    var title by remember { mutableStateOf("") }
    var weekday by remember { mutableIntStateOf(Calendar.MONDAY) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("Routine, e.g. take out the bins") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CAL_WEEKDAYS.forEachIndexed { i, dow ->
                FilterChip(selected = weekday == dow, onClick = { weekday = dow }, label = { Text(DAY_SHORT[i]) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = title.isNotBlank(), onClick = {
                onSave(Routine(System.currentTimeMillis(), title.trim(), weekday))
            }) { Text("Save routine") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun FindTimeDialog(item: PlanItem, onClose: () -> Unit, onBooked: (PlanItem, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dayOffset by remember { mutableIntStateOf(0) }
    var slots by remember { mutableStateOf<List<Long>>(emptyList()) }
    var conflictText by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Long?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    fun dayBounds(offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    LaunchedEffect(dayOffset) {
        val (start, end) = dayBounds(dayOffset)
        val busy = withContext(Dispatchers.IO) { WeekCalendar.eventsBetween(context, start, end) }
        slots = WeekCalendar.freeSlots(busy, start, item.minutes)
        val planned = if (item.startTime.isNotBlank() && item.dueDay == WeekLogic.dayKey(start)) {
            val parts = item.startTime.split(":")
            val at = Calendar.getInstance().apply {
                timeInMillis = start
                set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 0)
                set(Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 0)
            }.timeInMillis
            WeekCalendar.conflicts(busy, at, item.minutes)
        } else emptyList()
        conflictText = if (planned.isEmpty()) null
        else "Your planned time overlaps: " + planned.joinToString { it.title }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Find a time for ${item.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = dayOffset == 0, onClick = { dayOffset = 0 }, label = { Text("Today") })
                    FilterChip(selected = dayOffset == 1, onClick = { dayOffset = 1 }, label = { Text("Tomorrow") })
                }
                conflictText?.let { Text(it, fontSize = 12.sp, color = Color(0xFFB00020)) }
                if (slots.isEmpty()) {
                    Text("No free time of ${item.minutes} minutes between 8:00 and 21:00 that day.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text("Free times (tap one to add it to your calendar):", fontSize = 12.sp, color = Color.Gray)
                    slots.forEach { s ->
                        OutlinedButton(onClick = { pending = s }) {
                            Text("${timeFmt.format(Date(s))} · ${item.minutes} min" + if (item.repeat != Repeat.NONE) " · ${item.repeat.label.lowercase()}" else "")
                        }
                    }
                }
                status?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } }
    )

    pending?.let { start ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add to your calendar?") },
            text = {
                Text(
                    "${item.title} at ${timeFmt.format(Date(start))} for ${item.minutes} min" +
                        if (item.repeat != Repeat.NONE) ", ${item.repeat.label.lowercase()}." else "."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            CalendarSync.addEvent(context, item.title, start, item.minutes, item.repeat.rrule)
                        }
                        if (id == null) {
                            status = "Could not add it. Check calendar access."
                        } else {
                            val dayKey = WeekLogic.dayKey(start)
                            val updated = item.copy(
                                startTime = timeFmt.format(Date(start)),
                                dueDay = if (item.dueDay.isBlank()) dayKey else item.dueDay
                            )
                            onBooked(updated, "Added ${item.title} to your calendar.")
                        }
                    }
                }) { Text("Yes, add it") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Not yet") } }
        )
    }
}
