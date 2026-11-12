package com.focuslens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lockdown settings: the daily limit, the peak-hour limit, and which apps stay open. Off until you turn it on. */
@Composable
fun LockdownSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(LockdownPrefs.enabled(context)) }
    var limit by remember { mutableIntStateOf(LockdownPrefs.dailyLimitMin(context)) }
    var peakOn by remember { mutableStateOf(LockdownPrefs.peakGuard(context)) }
    var peakLimit by remember { mutableIntStateOf(LockdownPrefs.peakLimitMin(context)) }
    var allowed by remember { mutableStateOf(LockdownPrefs.allowed(context)) }
    var todayMin by remember { mutableLongStateOf(0L) }
    var peakText by remember { mutableStateOf<String?>(null) }
    val apps = remember { loadLaunchableApps(context) }

    LaunchedEffect(enabled) {
        val now = System.currentTimeMillis()
        val repo = UsageRepository(context)
        if (!repo.hasPermission()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            todayMin = repo.loadCustom(FocusPrefs.limitDayStartMs(now), now, withLaunches = false).sumOf { it.millis } / 60_000L
            val history = UsageHistoryRepository(context, repo)
            val recent = history.hourlyTotalsRecent(7)
            val peak = LockdownChecker.peakHour(recent)
            peakText = if (peak == null) "No peak hour yet. It appears after a few days of use."
            else "Your peak hour is ${peak}:00 to ${(peak + 1) % 24}:00, from the last 7 days."
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Lockdown", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "When you go past your phone limit, only the apps you allow stay open. Settings, the dialler and FocusLens " +
                "always stay open so you cannot get stuck. Unlocking takes the quiz.",
            fontSize = 12.sp, color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (LockdownState.locked) {
                    Text("Locked now", fontWeight = FontWeight.Medium)
                    Text(LockdownState.reason, fontSize = 12.sp, color = Color.Gray)
                } else {
                    Text(if (enabled) "Not locked right now" else "Lockdown is off", fontWeight = FontWeight.Medium)
                    Text("Today: $todayMin min of $limit min limit", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (!enabled) {
            Text(
                "Lockdown needs Accessibility and usage access, which you set up in Settings. Turn it on only if you want the lock.",
                fontSize = 12.sp, color = Color.Gray
            )
            Button(onClick = {
                LockdownPrefs.setEnabled(context, true)
                enabled = true
            }) { Text("Turn on lockdown") }
            return@Column
        }
        TextButton(onClick = {
            LockdownPrefs.setEnabled(context, false)
            enabled = false
        }) { Text("Turn lockdown off", color = Color.Gray) }

        Spacer(Modifier.height(8.dp))
        Text("Daily phone limit", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                limit = (limit - 30).coerceAtLeast(30)
                LockdownPrefs.setDailyLimitMin(context, limit)
            }) { Text("−30") }
            Text("${limit / 60}h ${limit % 60}m a day", fontSize = 15.sp)
            OutlinedButton(onClick = {
                limit = (limit + 30).coerceAtMost(720)
                LockdownPrefs.setDailyLimitMin(context, limit)
            }) { Text("+30") }
        }

        Spacer(Modifier.height(12.dp))
        Text("Peak hour", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        peakText?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = peakOn, onCheckedChange = {
                peakOn = it
                LockdownPrefs.setPeakGuard(context, it)
            })
            Text("Also lock during my peak hour", fontSize = 14.sp)
        }
        if (peakOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    peakLimit = (peakLimit - 5).coerceAtLeast(5)
                    LockdownPrefs.setPeakLimitMin(context, peakLimit)
                }) { Text("−5") }
                Text("$peakLimit min in that hour", fontSize = 14.sp)
                OutlinedButton(onClick = {
                    peakLimit = (peakLimit + 5).coerceAtMost(60)
                    LockdownPrefs.setPeakLimitMin(context, peakLimit)
                }) { Text("+5") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Apps that stay open", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text("Tick the apps you want during a lockdown.", fontSize = 12.sp, color = Color.Gray)
        apps.forEach { app ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    val next = if (app.pkg in allowed) allowed - app.pkg else allowed + app.pkg
                    allowed = next
                    LockdownPrefs.setAllowed(context, next)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = app.pkg in allowed, onCheckedChange = null)
                Spacer(Modifier.width(4.dp))
                Text(app.label, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
