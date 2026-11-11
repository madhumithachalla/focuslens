package com.focuslens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** One day of social-media use: the total, and each app's time and opens. */
data class SocialDay(val label: String, val total: Long, val perApp: Map<String, Long>, val opens: Map<String, Int>)

data class SocialData(
    val today: SocialDay,
    val yesterday: SocialDay,
    val week: List<SocialDay>,
    /** Per app, time per feature today (section name to milliseconds). */
    val featuresToday: Map<String, Map<String, Long>>,
    /** Per app, time per feature yesterday. */
    val featuresYesterday: Map<String, Map<String, Long>>
)

private fun loadSocial(context: android.content.Context): SocialData? {
    val usage = UsageRepository(context)
    if (!usage.hasPermission()) return null
    val prefs = FocusPrefs(context)
    val now = System.currentTimeMillis()
    val social = SectionDetector.tracked

    fun dayFrom(label: String, rows: List<AppUsage>): SocialDay {
        val mine = rows.filter { it.packageName in social }
        return SocialDay(
            label = label,
            total = mine.sumOf { it.millis },
            perApp = mine.associate { it.packageName to it.millis },
            opens = mine.associate { it.packageName to it.launches }
        )
    }

    val t0 = TrackersStore.dayStart(0)
    val t1 = TrackersStore.dayStart(1)
    val todayRows = usage.loadCustom(t0, now)
    val yesterdayRows = usage.loadCustom(t1, t0)
    val names = listOf("Today", "Yesterday") + (2..6).map { back ->
        SimpleDateFormat("EEE", Locale.US).format(TrackersStore.dayStart(back))
    }
    val week = (0..6).map { back ->
        val start = TrackersStore.dayStart(back)
        val end = if (back == 0) now else TrackersStore.dayStart(back - 1)
        dayFrom(names[back], usage.loadCustom(start, end))
    }

    val todayKey = TrackersStore.dayKey(now)
    val yesterdayKey = TrackersStore.dayKey(t1 + 1000L)
    val featToday = social.associateWith { prefs.sectionTotalsForDay(it, todayKey) }.filterValues { it.isNotEmpty() }
    val featYesterday = social.associateWith { prefs.sectionTotalsForDay(it, yesterdayKey) }.filterValues { it.isNotEmpty() }

    return SocialData(
        today = dayFrom("Today", todayRows),
        yesterday = dayFrom("Yesterday", yesterdayRows),
        week = week,
        featuresToday = featToday,
        featuresYesterday = featYesterday
    )
}

/** Social media: today against yesterday, what was done in each app, and the week. */
@Composable
fun SocialScreen() {
    val context = LocalContext.current
    var data by remember { mutableStateOf<SocialData?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var openApp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { loadSocial(context) }
        loaded = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Social media", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Time spent in social apps when they are in front of you, today against yesterday, " +
                "and what you did inside each one.",
            fontSize = 13.sp, color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))

        val d = data
        if (!loaded) {
            Text("Loading…", color = Color.Gray)
            return@Column
        }
        if (d == null) {
            Text("Usage access is needed to show this. Allow it in Settings → Permissions.", color = Color.Gray)
            return@Column
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Today", fontSize = 13.sp, color = Color.Gray)
                        Text(formatDuration(d.today.total), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Yesterday", fontSize = 13.sp, color = Color.Gray)
                        Text(formatDuration(d.yesterday.total), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val diff = d.today.total - d.yesterday.total
                Text(
                    when {
                        d.yesterday.total == 0L && d.today.total == 0L -> "No social media use recorded yet."
                        d.yesterday.total == 0L -> "No social media use yesterday to compare with."
                        diff > 0 -> "${formatDuration(diff)} more than yesterday."
                        diff < 0 -> "${formatDuration(-diff)} less than yesterday."
                        else -> "The same as yesterday."
                    },
                    fontSize = 13.sp, color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("By app", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        val apps = SectionDetector.tracked.filter { (d.today.perApp[it] ?: 0L) > 0L || (d.yesterday.perApp[it] ?: 0L) > 0L }
            .sortedByDescending { d.today.perApp[it] ?: 0L }
        if (apps.isEmpty()) Text("No social apps used in these two days.", fontSize = 13.sp, color = Color.Gray)
        val maxMs = apps.maxOfOrNull { maxOf(d.today.perApp[it] ?: 0L, d.yesterday.perApp[it] ?: 0L) }?.coerceAtLeast(1L) ?: 1L

        apps.forEach { pkg ->
            val name = SectionDetector.displayName[pkg] ?: pkg
            val t = d.today.perApp[pkg] ?: 0L
            val y = d.yesterday.perApp[pkg] ?: 0L
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { openApp = if (openApp == pkg) null else pkg }) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text("Opened ${d.today.opens[pkg] ?: 0}× today", fontSize = 12.sp, color = Color.Gray)
                    }
                    Text("Today ${formatDuration(t)}  ·  Yesterday ${formatDuration(y)}", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (t.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = UsageColors.Apps, trackColor = UsageColors.Track
                    )
                    LinearProgressIndicator(
                        progress = { (y.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(), color = Color(0xFFB8C4BC), trackColor = UsageColors.Track
                    )
                    if (openApp == pkg) {
                        Spacer(Modifier.height(8.dp))
                        Text("Inside $name, today", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        FeatureRows(d.featuresToday[pkg].orEmpty())
                        Spacer(Modifier.height(6.dp))
                        Text("Inside $name, yesterday", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        FeatureRows(d.featuresYesterday[pkg].orEmpty())
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Last 7 days, social media in total", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        WeekBars(d.week)
        Spacer(Modifier.height(16.dp))
        Text(
            "LinkedIn: \"Jobs / applications\" is counted when the Jobs tab or an Easy Apply screen is on screen. " +
                "Reels, videos, shorts, posts and stories are counted from what is on screen, so they are best effort.",
            fontSize = 11.sp, color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeatureRows(features: Map<String, Long>) {
    if (features.isEmpty()) {
        Text("No detail recorded yet. Use the app with FocusLens on, then check again.", fontSize = 12.sp, color = Color.Gray)
        return
    }
    features.entries.sortedByDescending { it.value }.forEach { (name, ms) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, fontSize = 13.sp)
            Text(formatDuration(ms), fontSize = 13.sp)
        }
    }
}

@Composable
private fun WeekBars(days: List<SocialDay>) {
    val max = (days.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1L)
    Row(Modifier.fillMaxWidth().height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.reversed().forEach { day ->
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.fillMaxWidth()
                        .height((90.dp * (day.total.toFloat() / max.toFloat())).coerceAtLeast(2.dp))
                        .background(if (day.total > 0) UsageColors.Apps else UsageColors.Track)
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.reversed().forEach { day ->
            Text("${day.label}\n${formatDuration(day.total)}", fontSize = 9.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        }
    }
}
