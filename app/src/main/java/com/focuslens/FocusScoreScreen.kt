package com.focuslens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Focus score for today, from this phone's usage only: social media time against yesterday,
 * reels watched, and how often you stepped back. It shows the number behind each part, and
 * the tips that match the weakest part. It is not a grade or a productivity measure.
 */
@Composable
fun FocusScoreScreen() {
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }
    var report by remember { mutableStateOf<FocusReport?>(null) }

    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val todayStart = cal.timeInMillis
            val yesterdayStart = todayStart - 86_400_000L
            val repo = UsageRepository(context)
            val social: (List<AppUsage>) -> Long = { rows ->
                rows.filter { it.packageName in SectionDetector.tracked }.sumOf { it.millis } / 60_000L
            }
            val (passes, gaveUp) = prefs.resistStatsLast(30)
            FocusInsights.build(
                FocusSignals(
                    socialMinToday = social(repo.loadCustom(todayStart, now)),
                    socialMinYesterday = social(repo.loadCustom(yesterdayStart, todayStart)),
                    reelsToday = prefs.reelsToday(),
                    reelMinToday = prefs.reelWatchMsToday() / 60_000L,
                    reelLimit = prefs.reelLimit(),
                    notOpenedAll = prefs.notOpenedTotal(SectionDetector.INSTAGRAM),
                    resistPasses = passes,
                    resistGaveUp = gaveUp
                )
            )
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Focus score", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        val r = report
        if (r == null) {
            Text("Working it out…", color = Color.Gray)
            return@Column
        }

        // The score, and what colour it is.
        val tone = when {
            r.score >= 70 -> Color(0xFF9CC4A8)
            r.score >= 40 -> Color(0xFFE0B25C)
            else -> Color(0xFFD98C8C)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("${r.score}", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = tone)
                Text("out of 100 today", fontSize = 13.sp, color = Color.Gray)
            }
        }

        // One bar per part, with the number behind it.
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                r.parts.forEach { p ->
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(p.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Text("${(p.score * 100).toInt()}", fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFF3A3D47), RoundedCornerShape(4.dp))) {
                            Box(
                                Modifier.fillMaxWidth(p.score.toFloat().coerceIn(0.02f, 1f))
                                    .height(8.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                            )
                        }
                        Text(p.detail, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // Tips that match the weakest parts. Nothing is shown when every part is good.
        if (r.tips.isNotEmpty()) {
            Text("Do next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            r.tips.forEach { t ->
                Card(Modifier.fillMaxWidth()) {
                    Text(t.text, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Text(
            "Based only on this phone's usage. Not a grade or productivity measure.",
            fontSize = 11.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(24.dp))
    }
}
