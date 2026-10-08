package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val MOOD_SCORE = mapOf("low" to 1, "flat" to 2, "okay" to 3, "good" to 4, "great" to 5)
private val MOOD_COLOR = mapOf(
    1 to Color(0xFFB3261E), 2 to Color(0xFFC9A04A), 3 to Color(0xFFD9C26A),
    4 to Color(0xFF7FB7A4), 5 to Color(0xFF2E7D5B)
)

private data class DayMood(val date: String, val score: Int?)

/**
 * A plain mood trend over the last 14 days, read from whichever source the user actually
 * logged that day — a journal entry's mood tag, or a cycle-log energy level if no journal
 * entry exists. Not a diagnostic tool; just a visual of the user's own logged pattern.
 */
@Composable
fun MentalHealthScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    var days by remember { mutableStateOf<List<DayMood>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        val dates = (0 until 14).map {
            val d = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, -1)
            d
        }.reversed()

        val journalByDate = db.journalDao().recentOnce(60).associateBy { it.date }
        val cycleByDate = db.cycleLogDao().betweenOnce(dates.first(), dates.last()).associateBy { it.date }

        days = dates.map { date ->
            val journalMood = journalByDate[date]?.mood?.let { MOOD_SCORE[it] }
            val cycleEnergy = cycleByDate[date]?.energyLevel
            DayMood(date, journalMood ?: cycleEnergy)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Mental health", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your own logged mood over the last 14 days — from Journal entries and Cycle check-ins. Not a diagnosis, just your own pattern.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (days.all { it.score == null }) {
            Text("Nothing logged yet — a Journal entry or Cycle energy log will start filling this in.", fontSize = 13.sp, color = Color.Gray)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { d ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        val score = d.score
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (score != null) (score * 22).dp else 4.dp)
                                .background(
                                    score?.let { MOOD_COLOR[it] } ?: Color.LightGray,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("14 days ago → today", fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "If this pattern is sitting low for a while, that's worth talking through with someone — a GP, a counsellor, or a helpline. This screen is a mirror, not a professional.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}
