package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import android.content.Context
import com.focuslens.data.AppDatabase
import com.focuslens.data.CycleMode
import com.focuslens.data.MissReason
import com.focuslens.data.TaskRepository
import com.focuslens.data.WeeklyCheckInEntity
import kotlinx.coroutines.launch
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
 * Picks one Learn topic based on a few plain, inspectable rules — not AI, not a diagnosis.
 * A low mood/energy average points at Mental Health resources; "tired" as the top reason
 * for missed tasks this week points at Sleep; "distracted" points at Focus & Productivity.
 * Falls back to a general pick. Mirrors the same rules in the web app's app.js.
 */
private val STUDY_RELATED_TOPIC_IDS = setOf("student", "reading", "writing", "other_activities")
private val NEURODIVERGENT_TAG_IDS = setOf("adhd", "autism", "dyslexia", "dyspraxia", "ocd", "tics")
private val MENTAL_HEALTH_TAG_IDS = setOf("anxiety", "depression", "other_health_condition")

/**
 * Mood/energy and missed-task signals take priority since they're acute (something's off
 * right now); the onboarding topic tags are a steadier fallback for what kind of Learn
 * content fits this person day-to-day. Only the topic tags the user picked are read — this
 * never infers a "discipline" from anything else (journal text, app usage, etc).
 */
private fun pickSuggestion(
    context: Context,
    avgMoodScore: Double?,
    topMissReason: MissReason?,
    topics: Set<String>,
    latestStress: Int? = null
): Pair<LearnItem, String>? {
    val (topic, why) = when {
        latestStress != null && latestStress >= 4 -> LearnTopic.MENTAL_HEALTH to "your last check-in rated stress ${latestStress}/5"
        avgMoodScore != null && avgMoodScore < 2.5 -> LearnTopic.MENTAL_HEALTH to "your mood/energy trend has been low lately"
        topMissReason == MissReason.TIRED -> LearnTopic.SLEEP to "\"tired\" has been your top reason for missed tasks this week"
        topMissReason == MissReason.DISTRACTED -> LearnTopic.FOCUS_PRODUCTIVITY to "\"distracted\" has been your top reason for missed tasks this week"
        topics.any { it in STUDY_RELATED_TOPIC_IDS } -> LearnTopic.STUDY_SKILLS to "you flagged studying, reading or writing in your setup"
        else -> null to "a pick from Learn"
    }
    val prefs = LearnPrefs(context)
    val pool = if (topic != null) LEARN_ITEMS.filter { it.topic == topic } else LEARN_ITEMS
    val unread = pool.filter { !prefs.isRead(it.id) }
    val item = (unread.ifEmpty { pool }).randomOrNull() ?: return null
    return item to why
}

/**
 * A plain mood trend over the last 14 days, read from whichever source the user actually
 * logged that day — a journal entry's mood tag, or a cycle-log energy level if no journal
 * entry exists. Not a diagnostic tool; just a visual of the user's own logged pattern.
 */
@Composable
fun MentalHealthScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()
    var days by remember { mutableStateOf<List<DayMood>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var suggestion by remember { mutableStateOf<Pair<LearnItem, String>?>(null) }
    var showNeurodivergentCard by remember { mutableStateOf(false) }
    var showMentalHealthScreenerCard by remember { mutableStateOf(false) }
    var disclosureWasNotSure by remember { mutableStateOf(false) }
    var latestCheckIn by remember { mutableStateOf<WeeklyCheckInEntity?>(null) }
    var showCheckInSheet by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        val profile = db.profileDao().get()
        // STRICT: "prefer_not_to_say" is mutually exclusive with every other tag at entry
        // (OnboardingScreen.kt), so if it's set, tags contains nothing else and neither card
        // below can trigger — the disclosure is respected by construction, not a special case.
        val healthTags = profile?.neurodivergentTags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        disclosureWasNotSure = "not_sure" in healthTags
        showNeurodivergentCard = disclosureWasNotSure || healthTags.any { it in NEURODIVERGENT_TAG_IDS }
        showMentalHealthScreenerCard = disclosureWasNotSure || healthTags.any { it in MENTAL_HEALTH_TAG_IDS }
        val topics = profile?.topics?.split(",")?.toSet() ?: emptySet()
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()
        val dates = (0 until 14).map {
            val d = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_MONTH, -1)
            d
        }.reversed()

        val journalByDate = db.journalDao().recentOnce(60).associateBy { it.date }
        val cycleByDate = db.cycleLogDao().betweenOnce(dates.first(), dates.last())
            .filter { it.mode == CycleMode.GENERAL_ENERGY }
            .associateBy { it.date }
        val sinceFirstDate = SimpleDateFormat("yyyyMMdd", Locale.US).parse(dates.first())?.time ?: 0L
        val checkInByDate = db.weeklyCheckInDao().since(sinceFirstDate)
            .associateBy { SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date(it.createdAt)) }
        latestCheckIn = db.weeklyCheckInDao().latest()

        days = dates.map { date ->
            val journalMood = journalByDate[date]?.mood?.let { MOOD_SCORE[it] }
            val cycleEnergy = cycleByDate[date]?.energyLevel
            val checkInMood = checkInByDate[date]?.moodScore
            DayMood(date, journalMood ?: cycleEnergy ?: checkInMood)
        }
        loading = false

        val avg = days.mapNotNull { it.score }.let { if (it.isEmpty()) null else it.average() }
        val reflection = TaskRepository(db.taskDao(), db.checkInDao()).weeklyReflection()
        suggestion = pickSuggestion(context, avg, reflection.topMissReason, topics, latestCheckIn?.stressScore)
    }

    if (showCheckInSheet) {
        WeeklyCheckInSheet(
            onSubmit = { entry ->
                scope.launch {
                    db.weeklyCheckInDao().insert(entry)
                    showCheckInSheet = false
                    refreshTick++
                }
            },
            onDismiss = { showCheckInSheet = false }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Mental health", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Each bar is one day in the last two weeks. Its height and colour show the mood or energy you logged that day, " +
                "from Journal entries, Cycle energy logs and your weekly check-ins. A grey bar means nothing was logged. " +
                "This is your own pattern, not a diagnosis, and it stays on this phone.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        val due = isCheckInDue(latestCheckIn?.createdAt)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (due) Color(0xFFE3EFE8) else MaterialTheme.colorScheme.surfaceVariant
            ),
            onClick = { showCheckInSheet = true }
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (due) "Quick check-in" else "Quick check-in (not due yet, but always open)",
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    latestCheckIn?.let { c ->
                        "Last one: mood ${c.moodScore}/5, stress ${c.stressScore}/5" +
                            (c.topTimeSink?.let { " — \"$it\"" } ?: "")
                    } ?: "You haven't done one yet — mood, stress, and what's taking up your time/energy lately.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator()
        } else if (days.all { it.score == null }) {
            Text("Nothing logged yet — a Journal entry or Cycle energy log will start filling this in.", fontSize = 13.sp, color = Color.Gray)
        } else {
            val logged = days.mapNotNull { it.score }
            Text(
                "Average ${"%.1f".format(logged.average())} out of 5, on ${logged.size} of 14 days",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            val firstWeek = days.take(7).mapNotNull { it.score }
            val secondWeek = days.drop(7).mapNotNull { it.score }
            if (firstWeek.isNotEmpty() && secondWeek.isNotEmpty()) {
                val change = secondWeek.average() - firstWeek.average()
                val comparison = when {
                    change >= 0.5 -> "higher this week than the week before"
                    change <= -0.5 -> "lower this week than the week before"
                    else -> "about the same as the week before"
                }
                Text("Your mood looks $comparison.", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(10.dp))
            // Scale on the left: 5 at the top, 1 at the bottom.
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(44.dp).height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Text("Great", fontSize = 10.sp, color = Color.Gray)
                    Text("Okay", fontSize = 10.sp, color = Color.Gray)
                    Text("Low", fontSize = 10.sp, color = Color.Gray)
                }
                Row(
                    modifier = Modifier.weight(1f).height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEach { d ->
                        val score = d.score
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (score != null) (score * 26).dp else 4.dp)
                                .background(
                                    score?.let { MOOD_COLOR[it] } ?: Color.LightGray,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }
            // Day of month under each bar, so it is clear which bar is which day.
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(44.dp))
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    days.forEach { d ->
                        Text(
                            d.date.takeLast(2).trimStart('0'),
                            fontSize = 9.sp,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Red is low, green is good, grey is nothing logged. Dates are day of the month, oldest first.",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "If this pattern is sitting low for a while, that's worth talking through with someone — a GP, a counsellor, or a helpline. This screen is a mirror, not a professional.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        suggestion?.let { (item, why) ->
            Spacer(Modifier.height(20.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EFE8)),
                onClick = {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
                    )
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("SUGGESTED FOR YOU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D5B))
                    Spacer(Modifier.height(4.dp))
                    Text("Based on simple rules (not AI) — $why.", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Text(item.title, fontWeight = FontWeight.Medium)
                    Text(item.source, fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        if (showNeurodivergentCard) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF6EEDD)),
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.hcp.med.harvard.edu/ncs/asrs.php")
                        )
                    )
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("NEURODIVERGENCE & ADHD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A6D1F))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (disclosureWasNotSure)
                            "You said you're not sure during setup — completely fine, that's exactly what a screener " +
                                "is for. The Adult ADHD Self-Report Scale (ASRS) is a real symptom screener designed " +
                                "by Harvard Medical School and the WHO — not a diagnosis, just a starting point if " +
                                "you're curious. CHADD and ADDA (in Learn) can connect you with real support. " +
                                "Entirely optional — tap to open the screener, or find more under Learn."
                        else
                            "You shared that this is part of your experience during setup. If it's ever useful, the " +
                                "Adult ADHD Self-Report Scale (ASRS) is a real symptom screener designed by Harvard " +
                                "Medical School and the WHO — not a diagnosis, just a well-regarded starting point if " +
                                "you're curious. CHADD and ADDA (in Learn) can connect you with real support if you " +
                                "want it. Entirely up to you — tap to open the screener, or find more under Learn.",
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (showMentalHealthScreenerCard) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE3EC)),
                onClick = {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://screening.mhanational.org/")
                        )
                    )
                }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("ANXIETY, DEPRESSION & OTHER HEALTH CONDITIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6B3F66))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (disclosureWasNotSure)
                            "You said you're not sure during setup — Mental Health America's free, confidential " +
                                "screening covers anxiety, depression and more, built from the same real clinical " +
                                "questionnaires (GAD-7, PHQ-9) clinicians use. Not a diagnosis — just a real, quick " +
                                "way to check if you're curious. Entirely optional — tap to open it, or find more " +
                                "under Learn."
                        else
                            "You shared that this is part of your experience during setup. Mental Health America's " +
                                "free, confidential screening (the same GAD-7/PHQ-9 questionnaires clinicians use) " +
                                "is a real starting point, not a diagnosis — and NIMH's own pages on anxiety and " +
                                "depression (in Learn) are a grounded place to read more. Entirely up to you.",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
