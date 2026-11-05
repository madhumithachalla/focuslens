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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private class CoachData(
    val insights: List<CoachInsight>,
    val plan: DayPlan,
    val risks: List<RiskedTask>,
    val signals: List<CoachInsight>,
    val loadedAt: Long,
    val learned: LearnedModel? = null,
    val personal: Boolean = true
)

private const val DAY_MS = 24L * 60 * 60 * 1000

private fun startOfDayMs(ms: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/**
 * Reads the on-device data behind the smart signals right now (UsageStats, the stored usage
 * history, Health Connect if connected) and hands it to the pure functions in CoachLogic.kt.
 * Any source that fails or has no permission is just left out.
 */
private suspend fun loadSmartSignals(context: android.content.Context, now: Long): List<CoachInsight> {
    val usage = UsageRepository(context)
    val history = UsageHistoryRepository(context, usage)
    val db = AppDatabase.get(context)
    val keyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val todayStart = startOfDayMs(now)

    try {
        history.catchUp(14)
        history.catchUpHourly(7)
    } catch (e: Exception) {
        // Carry on with whatever is already stored.
    }

    // (1) Minutes in 23:00-05:00 on each of the last 7 calendar days, from the raw event log.
    val late = ArrayList<Double>()
    try {
        for (back in 0..6) {
            val dayStart = todayStart - back * DAY_MS
            val breakdown = usage.hourlyBreakdown(dayStart, minOf(dayStart + DAY_MS, now))
            var ms = 0L
            for ((hour, byPkg) in breakdown) {
                if (hour in LATE_NIGHT_HOURS) ms += byPkg.values.sum()
            }
            late.add(ms / 60_000.0)
        }
    } catch (e: Exception) {
        late.clear()
    }

    // (2) Today so far, and up to 14 earlier days (stored snapshots first, live data for the last week).
    var todayMs = 0L
    val prior = ArrayList<Long>()
    try {
        todayMs = usage.load(Range.TODAY).sumOf { it.millis }
        for (back in 1..14) {
            val dayStart = todayStart - back * DAY_MS
            var total = db.dailyUsageDao().forDate(keyFmt.format(Date(dayStart))).sumOf { it.millis }
            if (total <= 0L && back <= 6) {
                total = usage.loadCustom(dayStart, dayStart + DAY_MS).sumOf { it.millis }
            }
            if (total > 0L) prior.add(total)
        }
    } catch (e: Exception) {
        prior.clear()
    }

    // (3) Time in blocked apps by hour of day: last 7 finished days from history plus today live.
    val blockedHours = HashMap<Int, Long>()
    try {
        val blocked = FocusPrefs(context).blocked().toList()
        if (blocked.isNotEmpty()) {
            val from = keyFmt.format(Date(todayStart - 7 * DAY_MS))
            val to = keyFmt.format(Date(todayStart - DAY_MS))
            db.hourlyUsageDao().hourTotalsForPackages(from, to, blocked).forEach {
                blockedHours[it.hour] = (blockedHours[it.hour] ?: 0L) + it.millis
            }
            for ((hour, byPkg) in usage.hourlyBreakdown(todayStart, now)) {
                val ms = byPkg.filterKeys { it in blocked }.values.sum()
                if (ms > 0) blockedHours[hour] = (blockedHours[hour] ?: 0L) + ms
            }
        }
    } catch (e: Exception) {
        blockedHours.clear()
    }

    // (4) Health Connect, only if it is there and already permitted.
    var hrvRatio: Double? = null
    var sleepMinutes: Double? = null
    try {
        if (HealthVitals.isAvailable(context) && HealthVitals.hasPermission(context)) {
            hrvRatio = HealthVitals.estimateStress(HealthVitals.readHrv(context, 14)).ratio
            val nights = HealthVitals.readSleep(context, 3).map {
                SleepInput(it.end.toEpochMilli(), it.totalMinutes, it.isNap)
            }
            sleepMinutes = lastNightSleepMinutes(nights, now)
        }
    } catch (e: Exception) {
        // Health Connect is optional.
    }

    return smartSignalInsights(
        SmartSignalInput(
            lateNightMinutesPerDay = late,
            todayMs = todayMs,
            priorDailyMs = prior,
            blockedHourMs = blockedHours,
            hrvRatio = hrvRatio,
            sleepMinutes = sleepMinutes
        )
    )
}

@Composable
fun CoachScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    var data by remember { mutableStateOf<CoachData?>(null) }

    // Live: reload when the screen is resumed and every 60 s while it is visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    var visible by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) visible = true
            if (event == Lifecycle.Event.ON_PAUSE) visible = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var reloadKey by remember { mutableStateOf(0) }
    var consentOpen by remember { mutableStateOf(!PrivacyPrefs.answered(context)) }
    if (consentOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text("Use your data to predict?") },
            text = { Text(PrivacyPrefs.CONSENT_TEXT, fontSize = 14.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    PrivacyPrefs.setPersonalised(context, true); consentOpen = false; reloadKey++
                }) { Text("Agree") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    PrivacyPrefs.setPersonalised(context, false); consentOpen = false; reloadKey++
                }) { Text("Not now") }
            }
        )
    }

    LaunchedEffect(visible, reloadKey) {
        while (visible) {
            data = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val tasks = db.taskDao().all().first().map {
                CoachTask(it.id, it.title, it.type.name, it.scheduledAt, it.completedAt)
            }
            // Opted out: nothing from your history is used for predictions or insights.
            val personal = PrivacyPrefs.personalised(context)
            val checkIns = if (!personal) emptyList() else db.checkInDao().allOnce().map {
                CoachCheckIn(it.taskId, it.completed, it.reason?.name)
            }
            val moods = if (!personal) emptyList() else db.journalDao().recentOnce(30).map { CoachMoodEntry(it.date, it.mood) }
            val learned = if (personal) trainOnDevice(tasks, checkIns) else null
            val risks = if (learned != null) {
                val overall = missProfile(tasks, checkIns).overall
                tasks.filter { it.completedAt == null && it.scheduledAt < now + DAY_MS }
                    .map { RiskedTask(it, learnedTaskRisk(it, learned, overall, now)) }
                    .sortedWith(compareByDescending<RiskedTask> { it.risk.risk }.thenBy { it.task.scheduledAt })
            } else {
                openTasksByRisk(tasks, checkIns, now)
            }
            CoachData(
                insights = coachInsights(tasks, checkIns, moods),
                plan = planDay(tasks, checkIns, now),
                risks = risks,
                signals = loadSmartSignals(context, now),
                loadedAt = now,
                learned = learned,
                personal = personal
            )
            }
            delay(60_000L)
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
            Text("Coach", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        if (d == null) {
            item { Text("Reading your data...", color = Color.Gray) }
        } else {
            item { SectionLabel("SMART SIGNALS FROM YOUR PHONE") }
            if (d.signals.isEmpty()) {
                item {
                    Text(
                        "Nothing stands out right now, or there isn't enough phone data yet " +
                            "(these need a few days of usage history; readiness needs Health Connect).",
                        color = Color.Gray
                    )
                }
            }
            items(d.signals) { i ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(i.head, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(i.why, fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
            item {
                Text(
                    "Updated ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(d.loadedAt))}, " +
                        "refreshes every minute while you are here.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

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
            if (!d.personal) {
                item { Text("Personal predictions are off. Turn them on in Settings → Privacy & your data.", color = Color.Gray, fontSize = 13.sp) }
            } else if (d.learned != null) {
                item { Text("Learned on this phone from ${d.learned.samples} past tasks (fit ${Math.round(d.learned.fitAccuracy * 100)}%).", color = Color.Gray, fontSize = 12.sp) }
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
