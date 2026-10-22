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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private class CoachData(
    val insights: List<CoachInsight>,
    val plan: DayPlan,
    val risks: List<RiskedTask>
)

@Composable
fun CoachScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    var data by remember { mutableStateOf<CoachData?>(null) }

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val tasks = db.taskDao().all().first().map {
                CoachTask(it.id, it.title, it.type.name, it.scheduledAt, it.completedAt)
            }
            val checkIns = db.checkInDao().allOnce().map {
                CoachCheckIn(it.taskId, it.completed, it.reason?.name)
            }
            val moods = db.journalDao().recentOnce(30).map { CoachMoodEntry(it.date, it.mood) }
            CoachData(
                insights = coachInsights(tasks, checkIns, moods),
                plan = planDay(tasks, checkIns, now),
                risks = openTasksByRisk(tasks, checkIns, now)
            )
        }
    }

    val d = data
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Coach", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        if (d == null) {
            item { Text("Reading your data...", color = Color.Gray) }
        } else {
            item { SectionLabel("INSIGHTS") }
            items(d.insights) { i ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(i.head, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(i.why, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }

            item { SectionLabel("TODAY'S PLAN") }
            if (d.plan.blocks.isEmpty()) {
                item { Text("Nothing to plan: no open tasks due today or overdue.", color = Color.Gray) }
            }
            items(d.plan.blocks) { b ->
                val range = timeFmt.format(Date(b.start)) + " - " + timeFmt.format(Date(b.end))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(range, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            b.title,
                            fontSize = 15.sp,
                            fontWeight = if (b.kind == "focus") FontWeight.Medium else FontWeight.Normal,
                            color = if (b.kind == "focus") Color.Unspecified else Color.Gray
                        )
                        Text(b.why, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            if (d.plan.overflow.isNotEmpty()) {
                item {
                    Text(
                        "${d.plan.overflow.size} more task(s) don't fit before 21:00: " +
                            d.plan.overflow.joinToString(", ") { it.task.title },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            item { SectionLabel("OPEN TASK RISK") }
            if (d.risks.isEmpty()) {
                item { Text("No open tasks due today or overdue.", color = Color.Gray) }
            }
            items(d.risks) { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                r.task.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            val label = when (r.risk.level) {
                                RiskLevel.HIGH -> "High"
                                RiskLevel.MEDIUM -> "Medium"
                                RiskLevel.LOW -> "Low"
                            }
                            AssistChip(onClick = {}, label = { Text("$label ${r.risk.risk}%") })
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(r.risk.why.joinToString(" "), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Rules and statistics on your own data, on this device. Not AI, not medical advice.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
}
