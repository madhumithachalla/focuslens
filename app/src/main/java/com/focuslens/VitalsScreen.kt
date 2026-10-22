package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class VitalsState { LOADING, UNAVAILABLE, NEEDS_PERMISSION, DENIED, READY }

private val StageAwakeColor = Color(0xFFE8A33D)
private val StageLightColor = Color(0xFF8FB8DE)
private val StageDeepColor = Color(0xFF2F5D9E)
private val StageRemColor = Color(0xFF8E6BBF)
private val StageOtherColor = Color(0xFFB8B4AA)

@Composable
fun VitalsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(VitalsState.LOADING) }
    var snapshot by remember { mutableStateOf<HealthVitals.Snapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            refreshing = true
            error = null
            try {
                snapshot = HealthVitals.readSnapshot(context)
                state = VitalsState.READY
            } catch (e: Exception) {
                error = "Couldn't read from Health Connect: ${e.message}"
            }
            refreshing = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = HealthConnectBridge.requestPermissionContract()
    ) { granted ->
        if (granted.containsAll(HealthVitals.PERMISSIONS)) {
            load()
        } else {
            state = VitalsState.DENIED
        }
    }

    // Initial check only reads the permission state; no health data is read until it's granted.
    LaunchedEffect(Unit) {
        state = when {
            !HealthVitals.isAvailable(context) -> VitalsState.UNAVAILABLE
            else -> {
                val ok = try { HealthVitals.hasPermission(context) } catch (e: Exception) { false }
                if (ok) VitalsState.READY else VitalsState.NEEDS_PERMISSION
            }
        }
        if (state == VitalsState.READY) load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Watch & vitals", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Real heart rate, HRV and sleep your watch app saved to Health Connect. " +
                "Read-only, and only after you connect it.",
            fontSize = 13.sp, color = Color.Gray
        )

        when (state) {
            VitalsState.LOADING -> Text("Checking Health Connect...", color = Color.Gray)

            VitalsState.UNAVAILABLE -> {
                val needsInstall = HealthVitals.needsInstall(context)
                Text(
                    if (needsInstall) {
                        "Health Connect needs to be installed or updated before FocusLens can read your watch data."
                    } else {
                        "Health Connect isn't available on this device, so there is no watch data to read. " +
                            "It is built into Android 14 and newer, and is a free Play Store app on Android 9 to 13."
                    },
                    fontSize = 14.sp
                )
                if (needsInstall) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                        )
                    }) { Text("Open Health Connect in Play Store") }
                }
            }

            VitalsState.NEEDS_PERMISSION, VitalsState.DENIED -> {
                if (state == VitalsState.DENIED) {
                    Text(
                        "Permission wasn't granted for all three data types (heart rate, HRV, sleep), so nothing was read. " +
                            "You can try again, or manage access in Health Connect settings.",
                        fontSize = 14.sp, color = Color(0xFF9A3B2E)
                    )
                } else {
                    Text(
                        "FocusLens will ask Android for read-only access to heart rate, heart-rate variability and sleep. " +
                            "It never writes to Health Connect.",
                        fontSize = 14.sp
                    )
                }
                Button(onClick = { permissionLauncher.launch(HealthVitals.PERMISSIONS) }) {
                    Text("Connect Health Connect")
                }
            }

            VitalsState.READY -> {
                val snap = snapshot
                if (snap == null) {
                    Text(if (refreshing) "Reading your data..." else "No data loaded yet.", color = Color.Gray)
                } else if (snap.isEmpty) {
                    NoDataCard()
                } else {
                    HeartRateCard(snap)
                    HrvStressCard(snap)
                    SleepCard(snap)
                    NapsCard(snap)
                }
                error?.let { Text(it, fontSize = 12.sp, color = Color(0xFF9A3B2E)) }
                OutlinedButton(onClick = { load() }, enabled = !refreshing) {
                    Text(if (refreshing) "Reading..." else "Refresh")
                }
            }
        }

        Text(
            "This is a wellness estimate, not medical advice. It can't diagnose or treat anything; " +
                "talk to a clinician if you're worried about your health.",
            fontSize = 12.sp, color = Color.Gray
        )
    }
}

@Composable
private fun VitalsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFECE4))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun NoDataCard() {
    VitalsCard("Connected, but no data found") {
        Text(
            "Health Connect has no heart rate, HRV or sleep records from the last 14 days. " +
                "Apps that can write to Health Connect include Samsung Health, Fitbit, Garmin Connect, " +
                "Google Fit, Oura and others. Open your watch's app, turn on its Health Connect sync, " +
                "sync the watch, then tap Refresh.",
            fontSize = 14.sp
        )
        Text(
            "Not every app writes everything: many don't write HRV or sleep stages, and some only share " +
                "heart rate during workouts.",
            fontSize = 13.sp, color = Color.Gray
        )
    }
}

@Composable
private fun HeartRateCard(snap: HealthVitals.Snapshot) {
    VitalsCard("Heart rate, last 24 hours") {
        val summary = snap.hrSummary
        if (summary == null || snap.hrSamples.size < 2) {
            Text("No heart-rate samples in the last 24 hours.", fontSize = 14.sp, color = Color.Gray)
        } else {
            Text(
                "Min ${summary.min}  |  Avg ${summary.avg}  |  Max ${summary.max} bpm  (${summary.count} samples)",
                fontSize = 14.sp
            )
            HeartRateChart(snap.hrSamples)
            Text(
                "Min/avg/max cover everything recorded, including exercise, so avg is not a true resting rate.",
                fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

@Composable
private fun HeartRateChart(samples: List<HealthVitals.HrSample>) {
    val lineColor = Color(0xFFC0392B)
    val gridColor = Color(0xFFD5D1C6)
    val minBpm = (samples.minOf { it.bpm } - 5L).coerceAtLeast(0L).toFloat()
    val maxBpm = (samples.maxOf { it.bpm } + 5L).toFloat()
    val t0 = samples.first().time.toEpochMilli()
    val t1 = samples.last().time.toEpochMilli()
    val span = (t1 - t0).coerceAtLeast(1L).toFloat()
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val w = size.width
        val h = size.height
        for (i in 0..2) {
            val y = h * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        val path = Path()
        samples.forEachIndexed { i, s ->
            val x = (s.time.toEpochMilli() - t0) / span * w
            val y = h - ((s.bpm - minBpm) / (maxBpm - minBpm).coerceAtLeast(1f)) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${minBpm.toInt()} bpm", fontSize = 11.sp, color = Color.Gray)
        Text(
            "${timeFmt.format(Date(t0))} to ${timeFmt.format(Date(t1))}",
            fontSize = 11.sp, color = Color.Gray
        )
        Text("${maxBpm.toInt()} bpm", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun HrvStressCard(snap: HealthVitals.Snapshot) {
    VitalsCard("HRV and stress estimate") {
        val hrv = snap.hrv
        if (hrv.latest == null) {
            Text(
                "No HRV (RMSSD) records found. Many watch apps don't write HRV to Health Connect, " +
                    "so this section stays empty rather than guessing.",
                fontSize = 14.sp, color = Color.Gray
            )
        } else {
            val fmt = SimpleDateFormat("EEE HH:mm", Locale.getDefault())
            Text(
                "Latest: ${"%.0f".format(hrv.latest.rmssdMs)} ms RMSSD (${fmt.format(Date.from(hrv.latest.time))})",
                fontSize = 14.sp
            )
            Text(
                hrv.sevenDayAvgMs?.let { "7-day average: ${"%.0f".format(it)} ms (${hrv.sevenDayCount} readings)" }
                    ?: "No readings in the last 7 days.",
                fontSize = 14.sp
            )
            val stress = snap.stress
            val level = stress.level
            if (level != null) {
                val color = when (level) {
                    HealthVitals.StressLevel.RELAXED -> Color(0xFF2E7D32)
                    HealthVitals.StressLevel.NORMAL -> Color(0xFF1F3A34)
                    HealthVitals.StressLevel.ELEVATED -> Color(0xFFB26A00)
                    HealthVitals.StressLevel.HIGH_STRAIN -> Color(0xFFB3261E)
                }
                Text(level.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(stress.message, fontSize = 13.sp)
            Text(
                "Compared only with your own recent normal, not with other people. Relaxed 90% or more of baseline, " +
                    "Normal 70-90%, Elevated 50-70%, High strain under 50%.",
                fontSize = 11.sp, color = Color.Gray
            )
        }
    }
}

private fun mainSleep(snap: HealthVitals.Snapshot): HealthVitals.SleepNight? =
    snap.sleepSessions.firstOrNull { !it.isNap }

@Composable
private fun SleepCard(snap: HealthVitals.Snapshot) {
    VitalsCard("Last night's sleep") {
        val night = mainSleep(snap)
        if (night == null) {
            Text("No overnight sleep session in the last 14 days.", fontSize = 14.sp, color = Color.Gray)
            return@VitalsCard
        }
        val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
        Text(
            "${fmt.format(Date.from(night.start))} to ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date.from(night.end))}  " +
                "(${formatVitalMinutes(night.totalMinutes)})",
            fontSize = 14.sp
        )
        if (!night.stagesReported) {
            Text(
                "Your watch/app did not report sleep stages for this night, so only the total time is shown.",
                fontSize = 14.sp, color = Color.Gray
            )
        } else {
            StageBar(night)
        }
    }
}

@Composable
private fun StageBar(night: HealthVitals.SleepNight) {
    val s = night.stages
    val parts = listOf(
        Triple("Awake", s.awake, StageAwakeColor),
        Triple("Light", s.light, StageLightColor),
        Triple("Deep", s.deep, StageDeepColor),
        Triple("REM", s.rem, StageRemColor),
        Triple("Unspecified", s.other, StageOtherColor)
    ).filter { it.second > 0L }

    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        parts.forEach { p ->
            Box(
                Modifier
                    .weight(p.second.toFloat())
                    .fillMaxHeight()
                    .background(p.third)
            )
        }
    }
    parts.forEach { p ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .background(p.third)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${p.first}: ${formatVitalMinutes(p.second)} (${night.percent(p.second)}%)",
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun NapsCard(snap: HealthVitals.Snapshot) {
    val naps = snap.sleepSessions.filter { it.isNap }
    VitalsCard("Naps") {
        if (naps.isEmpty()) {
            Text(
                "No naps found. Sleep under 3 hours that starts between 09:00 and 20:00 counts as a nap.",
                fontSize = 14.sp, color = Color.Gray
            )
        } else {
            val fmt = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
            naps.forEach { n ->
                Text(
                    "Nap  ${fmt.format(Date.from(n.start))}  |  ${formatVitalMinutes(n.totalMinutes)}  |  " +
                        if (n.stagesReported) "stages reported" else "no stages reported",
                    fontSize = 14.sp
                )
            }
        }
    }
}

private fun formatVitalMinutes(m: Long): String {
    val h = m / 60
    val r = m % 60
    return if (h > 0) "${h}h ${r}m" else "${r}m"
}
