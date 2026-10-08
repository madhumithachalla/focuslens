package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import com.focuslens.data.CycleMode
import com.focuslens.data.CyclePrediction
import com.focuslens.data.CycleRepository
import kotlinx.coroutines.launch

@Composable
fun CycleScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val repo = remember { CycleRepository(db.cycleLogDao()) }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf<CycleMode?>(null) }
    var prediction by remember { mutableStateOf<CyclePrediction?>(null) }
    var loading by remember { mutableStateOf(true) }

    suspend fun refresh() {
        prediction = repo.predictNextCycle()
        loading = false
    }

    LaunchedEffect(Unit) {
        val profile = db.profileDao().get()
        mode = profile?.defaultCycleMode ?: CycleMode.OFF
        refresh()
    }

    if (loading || mode == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Cycle & energy", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        when (mode) {
            CycleMode.OFF -> {
                Text("Not tracked right now.", fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { mode = CycleMode.MENSTRUAL }, label = { Text("Track menstrual cycle") })
                    FilterChip(selected = false, onClick = { mode = CycleMode.GENERAL_ENERGY }, label = { Text("Track general energy") })
                }
            }

            CycleMode.MENSTRUAL -> {
                val p = prediction
                if (p == null) {
                    Text(
                        "No period start logged yet — log today as day 1 of a period to begin building an estimate.",
                        fontSize = 13.sp
                    )
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (p.isConfident) "ESTIMATED NEXT CYCLE" else "EARLY ESTIMATE (fewer than 3 logged cycles)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Day ${p.currentCycleDay} · next start around ${formatDate(p.predictedNextStart)}", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "A rolling average of your last ${if (p.isConfident) "3+" else "few"} logged cycles (${p.averageCycleLengthDays} days avg) — your own data, not a clinical model. Treat it as a rough guide, not a diagnosis.",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    scope.launch {
                        repo.logToday(CycleMode.MENSTRUAL, periodStart = true, energyLevel = null, moodTags = null)
                        refresh()
                    }
                }) {
                    Text("Log today as period start")
                }
            }

            CycleMode.GENERAL_ENERGY -> {
                Text("Logging general energy & mood — no cycle prediction, just your own pattern over time.", fontSize = 13.sp)
            }

            else -> {}
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
