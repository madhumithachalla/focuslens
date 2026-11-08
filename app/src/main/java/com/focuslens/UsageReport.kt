package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import com.focuslens.data.HourTotal
import com.focuslens.data.MissReason
import com.focuslens.data.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Today's screen time as 24 hourly bars. */
@Composable
fun HourlyUsageCard(hours: List<HourTotal>) {
    val sorted = hours.sortedBy { it.hour }
    val busiest = sorted.maxByOrNull { it.millis }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today by hour", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (busiest != null && busiest.millis > 0) {
                    Text(formatHourLabel(busiest.hour), fontSize = 13.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(10.dp))
            BarChart(
                values = sorted.map { it.millis },
                firstLabel = "12am",
                lastLabel = "11pm",
                color = UsageColors.Apps
            )
        }
    }
}

fun formatHourLabel(hour: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    val suffix = if (hour < 12) "am" else "pm"
    return "$h$suffix"
}

private data class WeeklyStats(
    val thisTotal: Long,
    val lastTotal: Long,
    val peakHour: Int?,
    val topLabel: String?,
    val topMillis: Long,
    val missReason: MissReason?
)

private suspend fun loadWeeklyStats(
    repo: UsageRepository,
    history: UsageHistoryRepository,
    taskRepo: TaskRepository
): WeeklyStats {
    val now = System.currentTimeMillis()
    val dayMs = 24L * 60 * 60 * 1000
    val thisWeekStart = now - 7 * dayMs
    val lastWeekStart = now - 14 * dayMs

    val thisWeekRows = history.tryExact(thisWeekStart, now) ?: repo.loadCustom(thisWeekStart, now)
    val lastWeekRows = history.tryExact(lastWeekStart, thisWeekStart) ?: repo.loadCustom(lastWeekStart, thisWeekStart)
    val top = thisWeekRows.maxByOrNull { it.millis }
    val busiest = history.hourlyTotalsRecent(7).entries.maxByOrNull { it.value }
    val reflection = taskRepo.weeklyReflection()

    return WeeklyStats(
        thisTotal = thisWeekRows.sumOf { it.millis },
        lastTotal = lastWeekRows.sumOf { it.millis },
        peakHour = busiest?.takeIf { it.value > 0 }?.key,
        topLabel = top?.takeIf { it.millis > 0 }?.label,
        topMillis = top?.millis ?: 0L,
        missReason = reflection.topMissReason
    )
}

private fun missLabel(r: MissReason): String = when (r) {
    MissReason.TIRED -> "Tired"
    MissReason.DISTRACTED -> "Distracted"
    MissReason.TOO_BIG -> "Too big"
    MissReason.FORGOT -> "Forgot"
    else -> "Other"
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** This week against last week as bars, plus a change badge and three short tiles. No prose. */
@Composable
fun WeeklyReportCard(repo: UsageRepository, history: UsageHistoryRepository) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<WeeklyStats?>(null) }

    LaunchedEffect(Unit) {
        val taskRepo = TaskRepository(AppDatabase.get(context).taskDao(), AppDatabase.get(context).checkInDao())
        stats = withContext(Dispatchers.IO) { loadWeeklyStats(repo, history, taskRepo) }
        // Opt-in account sync (no-op unless enabled + signed in; throttled to once per 30 min).
        UsageSync.maybeSync(context, repo, history)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EFE8))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This week vs last", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val s = stats
            if (s == null) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                val max = maxOf(s.thisTotal, s.lastTotal).coerceAtLeast(1L)
                UsageBarRow("This week", s.thisTotal, max, UsageColors.Apps)
                UsageBarRow("Last week", s.lastTotal, max, Color(0xFF9AA59E))
                if (s.lastTotal > 0) {
                    val pct = (s.thisTotal - s.lastTotal) * 100 / s.lastTotal
                    val color = when {
                        pct > 15 -> Color(0xFFB3261E)
                        pct < -15 -> Color(0xFF2E7D32)
                        else -> Color.Gray
                    }
                    val text = if (pct >= 0) "▲ $pct%" else "▼ ${-pct}%"
                    Box(
                        Modifier
                            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.peakHour != null) {
                        StatTile("Peak hour", formatHourLabel(s.peakHour), Modifier.weight(1f))
                    }
                    if (s.topLabel != null) {
                        StatTile("Top app", "${s.topLabel} · ${formatDuration(s.topMillis)}", Modifier.weight(1f))
                    }
                    s.missReason?.let { StatTile("Missed tasks", missLabel(it), Modifier.weight(1f)) }
                }
            }
        }
    }
}
