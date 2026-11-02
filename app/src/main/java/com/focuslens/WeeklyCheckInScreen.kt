package com.focuslens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslens.data.CHECKIN_INTERVAL_DAYS
import com.focuslens.data.WeeklyCheckInEntity
import java.util.concurrent.TimeUnit

/** True once [CHECKIN_INTERVAL_DAYS] have passed since the last check-in, or there's never
 *  been one — this app still runs the check-in entirely on the user's own terms (a button,
 *  never a forced interruption), this just decides whether to surface it proactively. */
fun isCheckInDue(lastCreatedAt: Long?): Boolean {
    if (lastCreatedAt == null) return true
    val elapsedDays = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - lastCreatedAt)
    return elapsedDays >= CHECKIN_INTERVAL_DAYS
}

private val SCALE_1_5 = 1..5

/**
 * A short recurring check-in — mood, stress, and what's actually been taking up time/energy
 * lately, in the user's own words. Distinct from the one-time Quick setup profile: that's
 * "who you are," this is "how it's actually been going," re-askable as often as it's useful.
 */
@Composable
fun WeeklyCheckInSheet(onSubmit: (WeeklyCheckInEntity) -> Unit, onDismiss: () -> Unit) {
    var mood by remember { mutableStateOf(3) }
    var stress by remember { mutableStateOf(3) }
    var topTimeSink by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick check-in") },
        text = {
            Column {
                Text(
                    "Takes under a minute. Private to you, same as everything else here.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
                Text("Mood this week (1 low – 5 great)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SCALE_1_5.forEach { n ->
                        FilterChip(selected = mood == n, onClick = { mood = n }, label = { Text("$n") })
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Stress this week (1 low – 5 high)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SCALE_1_5.forEach { n ->
                        FilterChip(selected = stress == n, onClick = { stress = n }, label = { Text("$n") })
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = topTimeSink,
                    onValueChange = { topTimeSink = it },
                    label = { Text("What's taken up most of your time/energy? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Anything else? (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSubmit(
                    WeeklyCheckInEntity(
                        moodScore = mood,
                        stressScore = stress,
                        topTimeSink = topTimeSink.trim().ifBlank { null },
                        note = note.trim().ifBlank { null },
                        createdAt = System.currentTimeMillis()
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
    )
}
