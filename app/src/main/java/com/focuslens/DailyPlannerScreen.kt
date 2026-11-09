package com.focuslens

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

/** The parts of the day the planner groups by. Times are the defaults the person can pick. */
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

/** Today, laid out by part of the day, with quick ways to add something to a slot. */
@Composable
fun DailyPlannerScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { TaskRepository(db.taskDao(), db.checkInDao()) }
    val scope = rememberCoroutineScope()

    var tasks by remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var slotIndex by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val dayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = dayStart + 24L * 60 * 60 * 1000

    LaunchedEffect(Unit) {
        tasks = withContext(Dispatchers.IO) { db.taskDao().betweenOnce(dayStart, dayEnd) }
        loaded = true
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val dateFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.US) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Today", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(dateFmt.format(Date()), fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(14.dp))

        if (!loaded) {
            Text("Loading…", color = Color.Gray)
        } else {
            DAY_SLOTS.forEach { slot ->
                val inSlot = tasks.filter { slotFor(it.scheduledAt) == slot.label }
                Text(slot.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (inSlot.isEmpty()) {
                    Text("Nothing planned.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    inSlot.forEach { t ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(timeFmt.format(Date(t.scheduledAt)), fontSize = 12.sp, color = Color.Gray)
                                }
                                if (t.completedAt == null) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                db.taskDao().update(t.copy(completedAt = System.currentTimeMillis()))
                                                tasks = db.taskDao().betweenOnce(dayStart, dayEnd)
                                            }
                                        }
                                    }) { Text("Done") }
                                } else {
                                    Text("Done", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text("Add to today", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("Pick the part of the day, then a suggestion or your own words.", fontSize = 12.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_SLOTS.forEachIndexed { i, s ->
                    FilterChip(selected = slotIndex == i, onClick = { slotIndex = i }, label = { Text(s.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TASK_SUGGESTIONS.take(4).forEach { s ->
                    FilterChip(selected = false, onClick = { title = s }, label = { Text(s) })
                }
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("What do you want to do?") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = title.isNotBlank(),
                onClick = {
                    val slot = DAY_SLOTS[slotIndex]
                    val at = Calendar.getInstance().apply {
                        timeInMillis = dayStart
                        set(Calendar.HOUR_OF_DAY, slot.hour)
                    }.timeInMillis
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            repo.addTask(title.trim(), TaskType.WELLNESS, at, null, null)
                            tasks = db.taskDao().betweenOnce(dayStart, dayEnd)
                        }
                        message = "Added to ${slot.label.lowercase()}."
                        title = ""
                    }
                }
            ) { Text("Add to ${DAY_SLOTS[slotIndex].label.lowercase()}") }
            message?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 12.sp, color = Color.Gray) }
        }
    }
}
