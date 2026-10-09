package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import com.focuslens.data.HourTotal
import com.focuslens.data.MissReason
import com.focuslens.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A compact 24-bar chart of today's screen time by hour — Android itself never offers this
 *  view, so this reads whatever UsageHistoryRepository has (live for today, always available
 *  since today's raw event log is always present). */
@Composable
fun HourlyUsageCard(hours: List<HourTotal>) {
    val max = hours.maxOfOrNull { it.millis }?.coerceAtLeast(1L) ?: 1L
    val busiest = hours.maxByOrNull { it.millis }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Today by hour", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            if (busiest != null && busiest.millis > 0) {
                Text(
                    "Busiest so far: around ${formatHourLabel(busiest.hour)} (${formatDuration(busiest.millis)}).",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                hours.sortedBy { it.hour }.forEach { h ->
                    val frac = (h.millis.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(if (h.millis > 0) frac.coerceAtLeast(0.03f) else 0.015f)
                                .background(
                                    if (h.millis > 0) Color(0xFF3A5245) else Color(0xFFE3E0D8)
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("12am", fontSize = 10.sp, color = Color.Gray)
                Text("12pm", fontSize = 10.sp, color = Color.Gray)
                Text("11pm", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

private fun formatHourLabel(hour: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "am" else "pm"
    return "$h$suffix"
}

data class ReportLine(val text: String)

/**
 * Rule-based (not AI), each line tied to a specific in-app toggle so the suggestion is
 * actually actionable, not just a number — "ensure the user gets it fixed" means naming the
 * real screen/control, not just surfacing the data and leaving it there.
 */
private suspend fun buildWeeklyReport(
    repo: UsageRepository,
    history: UsageHistoryRepository,
    taskRepo: TaskRepository
): List<ReportLine> {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val thisWeekStart = now - 7 * dayMs
    val lastWeekStart = now - 14 * dayMs

    val thisWeekRows = history.tryExact(thisWeekStart, now) ?: repo.loadCustom(thisWeekStart, now)
    val lastWeekRows = history.tryExact(lastWeekStart, thisWeekStart) ?: repo.loadCustom(lastWeekStart, thisWeekStart)
    val thisTotal = thisWeekRows.sumOf { it.millis }
    val lastTotal = lastWeekRows.sumOf { it.millis }
    val topApp = thisWeekRows.maxByOrNull { it.millis }
    val hourly = history.hourlyTotalsRecent(7)
    val busiest = hourly.entries.maxByOrNull { it.value }
    val reflection = taskRepo.weeklyReflection()

    val lines = mutableListOf<ReportLine>()

    if (lastTotal > 0 && thisTotal > (lastTotal * 1.15).toLong()) {
        val pct = ((thisTotal - lastTotal) * 100 / lastTotal)
        lines.add(
            ReportLine(
                "Screen time is up ${pct}% from last week (${formatDuration(thisTotal)} vs ${formatDuration(lastTotal)}). " +
                    "A lower daily limit in Focus, or a scheduled Study Mode session, directly targets that."
            )
        )
    } else if (lastTotal > 0 && thisTotal < (lastTotal * 0.85).toLong()) {
        val pct = ((lastTotal - thisTotal) * 100 / lastTotal)
        lines.add(ReportLine("Screen time is down ${pct}% from last week (${formatDuration(thisTotal)} vs ${formatDuration(lastTotal)}). Whatever you changed, it's working."))
    }

    if (busiest != null && busiest.value > 0) {
        lines.add(
            ReportLine(
                "Your busiest hour recently is around ${formatHourLabel(busiest.key)} (${formatDuration(busiest.value)} total " +
                    "over the last week). A Study Mode session or a Focus limit timed for then would hit the actual peak, " +
                    "not just an average."
            )
        )
    }

    if (topApp != null && topApp.millis > 0) {
        lines.add(
            ReportLine(
                "${topApp.label} is your top app this week (${formatDuration(topApp.millis)}). Focus lets you set a daily " +
                    "limit on it, or Hard daily locks for a firmer stop once you hit it."
            )
        )
    }

    when (reflection.topMissReason) {
        MissReason.TIRED -> lines.add(ReportLine("\"Tired\" was your top reason for missed tasks this week — Tracking's Sleep tracker, or moving a task earlier in the day, targets that more directly than a stricter block would."))
        MissReason.DISTRACTED -> lines.add(ReportLine("\"Distracted\" was your top reason for missed tasks this week — Hyper Focus, or a tighter limit on your top app above, directly targets that."))
        MissReason.TOO_BIG -> lines.add(ReportLine("\"Too big\" was your top reason for missed tasks this week — splitting it into smaller to-dos measurably finishes more often than one large one."))
        MissReason.FORGOT -> lines.add(ReportLine("\"Forgot\" was your top reason for missed tasks this week — To-do's calendar button adds a reminder outside the app, for exactly this."))
        else -> {}
    }

    return lines
}

@Composable
fun WeeklyReportCard(repo: UsageRepository, history: UsageHistoryRepository) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<ReportLine>?>(null) }

    LaunchedEffect(Unit) {
        val taskRepo = TaskRepository(AppDatabase.get(context).taskDao(), AppDatabase.get(context).checkInDao())
        lines = withContext(Dispatchers.IO) { buildWeeklyReport(repo, history, taskRepo) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EFE8))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Weekly report", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            when {
                lines == null -> CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                lines!!.isEmpty() -> Text("Nothing stands out yet — keep logging and this fills in.", fontSize = 13.sp, color = Color.Gray)
                else -> lines!!.forEach {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Color.Gray)
                        Text(it.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
