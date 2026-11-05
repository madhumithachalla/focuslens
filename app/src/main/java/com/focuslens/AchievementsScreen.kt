package com.focuslens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The numbers badges are worked out from. All of it is read from this phone. */
data class AchievementStats(
    val journalEntries: Int,
    val journalStreak: Int,
    val tasksDone: Int,
    val weeklyCheckIns: Int,
    val rewardsDone: Int,
)

/** Consecutive days with a journal entry, ending today (or yesterday, so an unwritten today doesn't break it). */
fun journalStreakOf(dates: Set<String>, todayKey: String): Int {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    val cal = Calendar.getInstance()
    cal.time = fmt.parse(todayKey) ?: return 0
    if (fmt.format(cal.time) !in dates) cal.add(Calendar.DAY_OF_YEAR, -1)
    var streak = 0
    while (fmt.format(cal.time) in dates) {
        streak++
        cal.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

/** A badge: unlocked when [value] reaches [goal]. */
data class Badge(val name: String, val how: String, val value: Int, val goal: Int)

fun badgesFor(s: AchievementStats): List<Badge> = listOf(
    Badge("First page", "Write your first journal entry", s.journalEntries, 1),
    Badge("Steady writer", "Write in your journal 10 times", s.journalEntries, 10),
    Badge("Committed writer", "Write in your journal 50 times", s.journalEntries, 50),
    Badge("One week", "Journal 7 days in a row", s.journalStreak, 7),
    Badge("One month", "Journal 30 days in a row", s.journalStreak, 30),
    Badge("First task", "Finish your first to-do", s.tasksDone, 1),
    Badge("Getting things done", "Finish 25 to-dos", s.tasksDone, 25),
    Badge("Checking in", "Complete a weekly check-in", s.weeklyCheckIns, 1),
    Badge("Reward finder", "Do a suggested reward", s.rewardsDone, 1),
    Badge("Reward regular", "Do 10 suggested rewards", s.rewardsDone, 10),
)

private suspend fun loadStats(context: Context): AchievementStats = withContext(Dispatchers.IO) {
    val db = AppDatabase.get(context)
    val entries = db.journalDao().recentOnce(100000)
    val dates = entries.map { it.date }.toSet()
    val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)
    AchievementStats(
        journalEntries = entries.size,
        journalStreak = journalStreakOf(dates, today),
        tasksDone = db.checkInDao().allOnce().count { it.completed },
        weeklyCheckIns = db.weeklyCheckInDao().since(0L).size,
        rewardsDone = rewardsDone(context),
    )
}

@Composable
fun AchievementsScreen() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<AchievementStats?>(null) }
    LaunchedEffect(Unit) { stats = loadStats(context) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Achievements", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Badges come from what you actually do in FocusLens. Nothing is shared unless you choose to.",
            fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        val s = stats
        if (s == null) {
            Text("Counting your badges…", color = Color.Gray)
            return@Column
        }
        val badges = badgesFor(s)
        val unlocked = badges.count { it.value >= it.goal }
        Text("$unlocked of ${badges.size} unlocked", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))

        badges.forEach { b ->
            val got = b.value >= b.goal
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (got) "🏅 " else "🔒 ") + b.name,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = if (got) Color.Unspecified else Color.Gray
                        )
                        Text(b.how, fontSize = 13.sp, color = Color.Gray)
                    }
                    Text(
                        if (got) "Unlocked" else "${b.value.coerceAtMost(b.goal)}/${b.goal}",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val text = "I've unlocked $unlocked of ${badges.size} badges in FocusLens, the app that helps me focus and look after my wellbeing. " +
                "Try it: https://madhumithachalla.github.io/focuslens/"
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) },
                "Share FocusLens"
            ))
        }) { Text("Share my progress") }
    }
}
