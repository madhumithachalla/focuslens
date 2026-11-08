package com.focuslens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape

/** Colours used for usage graphics. Apps and browsers always get the same colours everywhere. */
object UsageColors {
    val Apps = Color(0xFF3A5245)
    val Browsers = Color(0xFF4EA8F7)
    val Track = Color(0xFFE3E0D8)
}

/** Packages that are web browsers. Their time is shown in its own section, not with apps. */
object BrowserApps {
    private val packages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
        "com.brave.browser", "com.microsoft.emmx", "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.opera.browser", "com.opera.mini.native", "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser", "com.kiwibrowser.browser",
        "org.torproject.torbrowser"
    )

    fun isBrowser(pkg: String): Boolean = pkg in packages
}

/** Splits [fetch]-able time into up to 31 daily buckets, or 12 equal buckets for longer spans. */
suspend fun bucketTotals(
    from: Long,
    to: Long,
    fetch: suspend (Long, Long) -> List<AppUsage>
): List<Pair<String, Long>> {
    val day = 24L * 60 * 60 * 1000
    val span = (to - from).coerceAtLeast(1L)
    val count = if (span <= 31 * day) ((span + day - 1) / day).toInt() else 12
    val step = (span + count - 1) / count
    val fmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.US)
    return (0 until count).map { i ->
        val a = from + i * step
        val b = minOf(a + step, to)
        val total = if (a < b) fetch(a, b).sumOf { it.millis } else 0L
        fmt.format(java.util.Date(a)) to total
    }
}

/** Ring showing the apps-vs-browsers share of the total. */
@Composable
fun DonutSplit(apps: Long, browsers: Long, modifier: Modifier = Modifier) {
    val total = (apps + browsers).coerceAtLeast(1L)
    val appSweep = 360f * apps / total
    val browserSweep = 360f * browsers / total
    Canvas(modifier) {
        val stroke = size.minDimension * 0.16f
        val arc = Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset((size.width - arc.width) / 2f, (size.height - arc.height) / 2f)
        drawArc(
            color = UsageColors.Track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arc, style = Stroke(stroke)
        )
        drawArc(
            color = UsageColors.Apps, startAngle = -90f, sweepAngle = appSweep, useCenter = false,
            topLeft = topLeft, size = arc, style = Stroke(stroke)
        )
        drawArc(
            color = UsageColors.Browsers, startAngle = -90f + appSweep, sweepAngle = browserSweep,
            useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke)
        )
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Vertical bars. Only the first and last labels are printed, to keep the chart quiet. */
@Composable
fun BarChart(
    values: List<Long>,
    firstLabel: String,
    lastLabel: String,
    color: Color = UsageColors.Apps
) {
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        values.forEach { v ->
            val frac = (v.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(if (v > 0) frac.coerceAtLeast(0.03f) else 0.02f)
                        .background(if (v > 0) color else UsageColors.Track)
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(firstLabel, fontSize = 10.sp, color = Color.Gray)
        Text(lastLabel, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun OverTimeCard(buckets: List<Pair<String, Long>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Over time", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            BarChart(
                values = buckets.map { it.second },
                firstLabel = buckets.first().first,
                lastLabel = buckets.last().first,
                color = UsageColors.Apps
            )
        }
    }
}

/** A labelled horizontal bar: name, duration on the right, bar underneath. */
@Composable
fun UsageBarRow(label: String, millis: Long, max: Long, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(millis), fontSize = 15.sp)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (millis.toFloat() / max.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = UsageColors.Track
        )
    }
}

@Composable
fun AppUsageItem(
    app: AppUsage,
    max: Long,
    color: Color,
    open: Boolean,
    onToggle: () -> Unit,
    screens: List<ScreenTime>,
    range: Range,
    prefs: FocusPrefs
) {
    Column(Modifier.fillMaxWidth().clickable { onToggle() }) {
        UsageBarRow(app.label, app.millis, max, color)
        if (open) AppDetails(app, screens, range, prefs)
    }
}

@Composable
fun SectionTitle(title: String, total: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
        Text(formatDuration(total), fontSize = 13.sp, color = Color.Gray)
    }
}
