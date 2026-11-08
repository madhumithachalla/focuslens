package com.focuslens

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

data class Insight(val text: String, val warn: Boolean = false)

private val whyNotes = listOf(
    "Android only keeps detailed history for about 10 days. Older days exist only as totals.",
    "Calls, videos and music keep the screen on, so they count as screen time even when you are not touching the phone.",
    "Screen-on time is always higher than app time. It also includes the lock screen, notifications and the screen sitting on.",
    "Moving between screens inside one app used to be counted as many separate visits. Visits less than 3 seconds apart are now joined.",
    "Alarm and timer screens in Clock count as use while they are showing, even if you are not looking.",
    "The launcher and system screens are left out of the app list.",
    "Digital Wellbeing can differ by a few minutes because it handles edge cases a little differently."
)

fun buildInsights(
    context: Context,
    rows: List<AppUsage>,
    breakdown: Map<String, List<ScreenTime>>,
    range: Range,
    prefs: FocusPrefs
): List<Insight> {
    val out = ArrayList<Insight>()
    val total = rows.sumOf { it.millis }

    val stayAwake = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) != 0
    } catch (e: Exception) {
        false
    }
    if (stayAwake) {
        out.add(
            Insight(
                "Developer option \"Stay awake\" is ON. While charging, your screen stays lit, so " +
                    "screen-on time is overstated. Turn it off in Settings > System > Developer options.",
                warn = true
            )
        )
    }

    if (rows.size >= 2 && total > 0) {
        val a = rows[0]
        val b = rows[1]
        out.add(Insight("${a.label} and ${b.label} take ${(a.millis + b.millis) * 100 / total}% of your app time."))
    }

    if (!range.approximate) {
        val wa = rows.firstOrNull { it.packageName == "com.whatsapp" }
        val calls = breakdown["com.whatsapp"]?.firstOrNull { it.screen == "Calls" }
        if (wa != null && calls != null && wa.millis > 0) {
            out.add(
                Insight(
                    "WhatsApp: ${calls.millis * 100 / wa.millis}% of its time (${formatDuration(calls.millis)}) " +
                        "is calls. Calls keep the screen on, so they count as screen time."
                )
            )
        }

        val days = if (range == Range.TODAY) 1 else 7
        for ((pkg, name) in listOf(SectionDetector.INSTAGRAM to "Instagram", SectionDetector.YOUTUBE to "YouTube")) {
            if (rows.none { it.packageName == pkg }) continue
            val secs = prefs.sectionTotals(pkg, days).entries.sortedByDescending { it.value }
            val measured = secs.sumOf { it.value }
            if (measured < 60_000L) {
                out.add(
                    Insight(
                        "$name keeps Reels, feed and messages inside one screen, so Android cannot split them. " +
                            "Turn on Blocking in the Focus tab and FocusLens measures them from then on."
                    )
                )
            } else {
                val top = secs.take(4).joinToString(", ") {
                    "${it.key} ${formatDuration(it.value)} (${it.value * 100 / measured}%)"
                }
                out.add(Insight("$name by section: $top."))
            }
        }
    }
    return out
}

@Composable
fun InsightsCard(
    rows: List<AppUsage>,
    breakdown: Map<String, List<ScreenTime>>,
    range: Range
) {
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }
    var showWhy by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    val lines = remember(rows, breakdown, range) { buildInsights(context, rows, breakdown, range, prefs) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEAE0))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Collapsed by default: the graphs carry the story, the written notes are one tap away.
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("What your data says", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(if (open) "Hide" else "${lines.size} notes", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
    if (open) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEAE0))
        ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (lines.isEmpty()) {
                Text("Not enough data yet.", fontSize = 13.sp, color = Color.Gray)
            }
            lines.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (it.warn) "!" else "•", color = if (it.warn) Color(0xFFB3261E) else Color.Gray)
                    Text(it.text, fontSize = 13.sp)
                }
            }
            TextButton(onClick = { showWhy = !showWhy }) {
                Text(if (showWhy) "Hide why numbers can look wrong" else "Why numbers can look wrong")
            }
            if (showWhy) {
                whyNotes.forEach {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = Color.Gray)
                        Text(it, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
        }
    }
}

/** Shown when an app row is tapped: time by screen, plus time by section for Instagram and YouTube. */
@Composable
fun AppDetails(app: AppUsage, screens: List<ScreenTime>, range: Range, prefs: FocusPrefs) {
    Column(
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text("Where the time went (by screen)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        if (range.approximate) {
            Text("Screen detail covers the last 7 days only.", fontSize = 11.sp, color = Color.Gray)
        }
        if (screens.isEmpty()) {
            Text("No screen detail recorded.", fontSize = 12.sp, color = Color.Gray)
        }
        screens.take(8).forEach {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.screen, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(formatDuration(it.millis), fontSize = 12.sp)
            }
        }
        if (app.packageName in SectionDetector.tracked) {
            Spacer(Modifier.height(6.dp))
            Text("Inside the app (measured by FocusLens)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            val days = if (range == Range.TODAY) 1 else 7
            val secs = prefs.sectionTotals(app.packageName, days).entries.sortedByDescending { it.value }
            val total = secs.sumOf { it.value }
            if (total < 60_000L) {
                Text(
                    "Not measured yet. Turn on Blocking in the Focus tab, use the app, then check here.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            } else {
                secs.forEach {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(it.key, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${formatDuration(it.value)} (${it.value * 100 / total}%)", fontSize = 12.sp)
                    }
                }
                Text(
                    "\"Other\" is time FocusLens could not place in a section.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
