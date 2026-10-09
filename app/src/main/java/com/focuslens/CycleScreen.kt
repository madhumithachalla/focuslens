package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import com.focuslens.data.CycleMode
import com.focuslens.data.CyclePrediction
import com.focuslens.data.CycleRepository
import com.focuslens.data.ProfileEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Commonly-cited "cycle syncing" guidance — widely circulated wellness framing, not a
 * clinically proven formula. Everyone's actual experience varies; a starting suggestion to
 * notice how you feel against, not a rule to follow regardless of how you actually feel.
 * Colors are four distinct hues (not shades of one color) chosen to stay distinguishable for
 * common color blindness, and are always shown with the phase name as text, never alone.
 */
private data class PhaseGuidance(val color: Color, val text: String)

private val PHASE_GUIDANCE = mapOf(
    "Follicular phase" to PhaseGuidance(
        Color(0xFFB8872B),
        "Energy commonly starts rising through this phase. Often suggested for: starting new projects, planning, brainstorming, and ramping up exercise intensity as you feel able to."
    ),
    "Fertile window" to PhaseGuidance(
        Color(0xFFC96A3E),
        "Energy and confidence often climb further here. Often suggested for: social plans, presentations, or harder conversations, if that matches how you're feeling."
    ),
    "Ovulation" to PhaseGuidance(
        Color(0xFFA8336B),
        "Energy often peaks around now for many people. Often suggested for: your most demanding task or a big social event — but only if your own energy actually backs it up today."
    ),
    "Luteal phase" to PhaseGuidance(
        Color(0xFF4F5584),
        "Energy and focus commonly ease off through this phase, sometimes more sharply near the end. Often suggested for: detail work, wrapping up tasks, gentler exercise, and extra rest."
    )
)

private data class TrackerType(val mode: CycleMode, val label: String, val hint: String)

private val TRACKER_TYPES = listOf(
    TrackerType(CycleMode.GENERAL_ENERGY, "Energy", "1 low – 5 high"),
    TrackerType(CycleMode.MOOD, "Mood", "1 low – 5 great"),
    TrackerType(CycleMode.SLEEP, "Sleep quality", "1 poor – 5 great"),
    TrackerType(CycleMode.WATER, "Water intake", "1 very little – 5 plenty")
)

@Composable
fun CycleScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { CycleRepository(db.cycleLogDao()) }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var prediction by remember { mutableStateOf<CyclePrediction?>(null) }
    var trackerValues by remember { mutableStateOf<Map<CycleMode, Int?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var pickingPeriodDate by remember { mutableStateOf(false) }
    var healthConnectStatus by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        profile = db.profileDao().get()
        prediction = repo.predictNextCycle()
        trackerValues = TRACKER_TYPES.associate { it.mode to repo.todayValue(it.mode) }
        loading = false
    }

    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectBridge.requestPermissionContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectBridge.PERMISSIONS)) {
            scope.launch {
                val dates = HealthConnectBridge.readPeriodStarts(context)
                if (dates.isNotEmpty()) {
                    repo.importPeriodStarts(dates)
                    refresh()
                    healthConnectStatus = "Imported ${dates.size} period start date${if (dates.size == 1) "" else "s"} from Health Connect."
                } else {
                    healthConnectStatus = "No period data found in Health Connect yet — not every watch or app writes it there."
                }
            }
        } else {
            healthConnectStatus = "Permission not granted — nothing imported."
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun setTracking(menstrual: Boolean, general: Boolean) {
        val p = profile ?: return
        scope.launch {
            val updated = p.copy(trackMenstrualCycle = menstrual, trackGeneralEnergy = general)
            db.profileDao().update(updated)
            refresh()
        }
    }

    if (loading || profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val p = profile!!

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Tracking", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Anyone who gets periods can track that directly — everyone else can pick whichever of the other trackers fit, any subset, any day.",
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        if (!p.trackMenstrualCycle && !p.trackGeneralEnergy) {
            Text("Not tracked right now.", fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { setTracking(true, p.trackGeneralEnergy) }, label = { Text("Track my cycle") })
                FilterChip(selected = false, onClick = { setTracking(p.trackMenstrualCycle, true) }, label = { Text("Show other trackers") })
            }
            return@Column
        }

        if (p.trackMenstrualCycle) {
            val pred = prediction
            if (pred == null) {
                Text(
                    "No period start logged yet — log today as day 1 of a period to begin building an estimate.",
                    fontSize = 13.sp
                )
            } else {
                val guidance = PHASE_GUIDANCE[pred.phase]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (guidance != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(guidance.color)
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            if (pred.isConfident) "ESTIMATED NEXT CYCLE" else "EARLY ESTIMATE (fewer than 3 logged cycles)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("Day ${pred.currentCycleDay} · next start around ${formatDate(pred.predictedNextStart)}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("${pred.phase} · estimated fertile window: day ${pred.fertileWindowStartDay}–${pred.fertileWindowEndDay}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A rolling average of your last ${if (pred.isConfident) "3+" else "few"} logged cycles (${pred.averageCycleLengthDays} days avg) — your own data, not a clinical model. The phase and fertile window assume a standard ~14-day luteal phase, which varies person to person (roughly 11-17 days) — only basal body temperature, LH strips, or an ultrasound can actually confirm ovulation. Treat all of this as a rough guide, not a diagnosis or contraception method.",
                            fontSize = 11.sp
                        )
                        if (guidance != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "WHAT PEOPLE COMMONLY DO IN THIS PHASE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = guidance.color
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(guidance.text, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        repo.logToday(CycleMode.MENSTRUAL, periodStart = true, energyLevel = null, moodTags = null)
                        refresh()
                    }
                }) {
                    Text("Log today as period start")
                }
                OutlinedButton(onClick = { pickingPeriodDate = true }) {
                    Text("Log a different date")
                }
            }
            if (pickingPeriodDate) {
                DatePickerDialogPicker(
                    initial = null,
                    onPicked = { millis ->
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))
                        scope.launch {
                            repo.logForDate(date, CycleMode.MENSTRUAL, periodStart = true, energyLevel = null, moodTags = null)
                            refresh()
                        }
                        pickingPeriodDate = false
                    },
                    onDismiss = { pickingPeriodDate = false }
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = {
                when {
                    HealthConnectBridge.needsInstall(context) -> {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                        )
                    }
                    !HealthConnectBridge.isAvailable(context) -> {
                        healthConnectStatus = "Health Connect isn't available on this device."
                    }
                    else -> healthConnectPermissionLauncher.launch(HealthConnectBridge.PERMISSIONS)
                }
            }) {
                Text("Import from watch (Health Connect)")
            }
            Text(
                "Reads period-start dates your watch or another health app (Samsung Health, Fitbit, Flo, etc.) " +
                    "may have already saved to Health Connect — only if you tap this, and only that one data type.",
                fontSize = 11.sp,
                color = Color.Gray
            )
            healthConnectStatus?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (p.trackGeneralEnergy) {
            Text("Trackers", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text("Pick whichever you want to log today — logging one never requires the others.", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            TRACKER_TYPES.forEach { tracker ->
                Text("${tracker.label} (${tracker.hint})", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = trackerValues[tracker.mode] == level,
                            onClick = {
                                trackerValues = trackerValues + (tracker.mode to level)
                                scope.launch {
                                    repo.logToday(tracker.mode, periodStart = false, energyLevel = level, moodTags = null)
                                }
                            },
                            label = { Text("$level") }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        TextButton(onClick = { setTracking(false, false) }) {
            Text("Turn off tracking")
        }
    }
}

private fun formatDate(yyyymmdd: String): String {
    if (yyyymmdd.length != 8) return yyyymmdd
    val month = yyyymmdd.substring(4, 6).toInt()
    val day = yyyymmdd.substring(6, 8).toInt()
    val names = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return "${names[month - 1]} $day"
}
