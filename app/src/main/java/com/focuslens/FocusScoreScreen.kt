package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.focuslens.data.JournalRepository

private data class ScoreBreakdown(
    val total: Int,
    val resistRatio: Double,
    val resistPasses: Int,
    val resistGaveUp: Int,
    val journalDays30: Int,
    val journalStreak: Int
)

/**
 * A composite index of how consistently the user is sticking to the limits and habits
 * they set for themselves in FocusLens — NOT a measure of grades, productivity, or any
 * real-world outcome. There's no grade data anywhere in this app (no LMS connection),
 * and the research behind the blocker design (Zimmermann & Sobolev, 2023) found that
 * cutting screen time did not reliably move academic performance either — so this
 * screen must never imply it predicts that. It only reflects behavior inside the app.
 */
@Composable
fun FocusScoreScreen() {
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }
    val db = remember { AppDatabase.get(context) }
    val journalRepo = remember { JournalRepository(db.journalDao()) }

    var breakdown by remember { mutableStateOf<ScoreBreakdown?>(null) }

    LaunchedEffect(Unit) {
        val (passes, gaveUp) = prefs.resistStatsLast(30)
        val resistRatio = if (passes + gaveUp == 0) 0.5 else gaveUp.toDouble() / (passes + gaveUp)

        val journalEntries = journalRepo.recent(30).size
        val journalDays30 = journalEntries.coerceAtMost(30)
        val streak = journalRepo.currentStreak()

        val journalConsistency = (journalDays30 / 30.0).coerceIn(0.0, 1.0)
        val streakBonus = (streak / 14.0).coerceIn(0.0, 1.0)

        val score = (resistRatio * 0.4 + journalConsistency * 0.3 + streakBonus * 0.3) * 100

        breakdown = ScoreBreakdown(
            total = score.toInt().coerceIn(0, 100),
            resistRatio = resistRatio,
            resistPasses = passes,
            resistGaveUp = gaveUp,
            journalDays30 = journalDays30,
            journalStreak = streak
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Focus Score", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "How consistently you're sticking to your own limits — not a measure of grades, productivity, or any outcome outside the app.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        val b = breakdown
        if (b == null) {
            CircularProgressIndicator()
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${b.total}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Text("/100", fontSize = 20.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(20.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("WHAT MADE THIS UP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(Modifier.height(10.dp))
                ScoreLine("Resisted vs. opened anyway (last 30 days)", "${b.resistGaveUp} resisted / ${b.resistPasses} opened", "40%")
                ScoreLine("Journal entries (last 30 days)", "${b.journalDays30}/30 days", "30%")
                ScoreLine("Current journal streak", "${b.journalStreak} days", "30%")
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "This can't tell you anything about your grades or real-world outcomes — there's no " +
                "connection to any gradebook here, and even published research on screen-time " +
                "interventions hasn't found a reliable link to academic performance. Treat this as " +
                "a mirror on your own app habits, nothing more.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun ScoreLine(label: String, value: String, weight: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.5.sp)
            Text(value, fontSize = 11.sp, color = Color.Gray)
        }
        Text(weight, fontSize = 11.sp, color = Color.Gray)
    }
}
