package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun CycleScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { CycleRepository(db.cycleLogDao()) }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<ProfileEntity?>(null) }
    var prediction by remember { mutableStateOf<CyclePrediction?>(null) }
    var energyLevel by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        profile = db.profileDao().get()
        prediction = repo.predictNextCycle()
        loading = false
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
        Text("Cycle & energy", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Pick as many as apply — not an either/or.", fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))

        if (!p.trackMenstrualCycle && !p.trackGeneralEnergy) {
            Text("Not tracked right now.", fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { setTracking(true, p.trackGeneralEnergy) }, label = { Text("Track menstrual cycle") })
                FilterChip(selected = false, onClick = { setTracking(p.trackMenstrualCycle, true) }, label = { Text("Track general energy") })
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
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
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                scope.launch {
                    repo.logToday(CycleMode.MENSTRUAL, periodStart = true, energyLevel = null, moodTags = null)
                    refresh()
                }
            }) {
                Text("Log today as period start")
            }
            Spacer(Modifier.height(20.dp))
        }

        if (p.trackGeneralEnergy) {
            Text("General energy", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { level ->
                    FilterChip(
                        selected = energyLevel == level,
                        onClick = {
                            energyLevel = level
                            scope.launch {
                                repo.logToday(CycleMode.GENERAL_ENERGY, periodStart = false, energyLevel = level, moodTags = null)
                            }
                        },
                        label = { Text("$level") }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        TextButton(onClick = { setTracking(false, false) }) {
            Text("Turn off cycle & energy tracking")
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
