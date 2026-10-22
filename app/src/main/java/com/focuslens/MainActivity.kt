@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.focuslens

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFFF7F5F0),
                    surface = Color(0xFFF7F5F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FocusLensApp(UsageRepository(applicationContext))
                }
            }
        }
    }
}

@Composable
fun FocusLensApp(repo: UsageRepository) {
    val context = LocalContext.current
    val db = remember { com.focuslens.data.AppDatabase.get(context) }
    var profile by remember { mutableStateOf<com.focuslens.data.ProfileEntity?>(null) }
    var profileChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profile = db.profileDao().get()
        profileChecked = true
    }

    if (!profileChecked) return

    // Sign-in comes first, before the setup quiz: it's the very first thing a new install
    // shows, not something buried after onboarding or behind a tab.
    val authPrompt = remember { AuthPromptPrefs(context) }
    var authPromptSeen by remember { mutableStateOf(authPrompt.isSeen()) }

    if (!authPromptSeen) {
        WelcomeAuthScreen(onContinue = {
            authPrompt.setSeen()
            authPromptSeen = true
        })
        return
    }

    if (profile == null) {
        OnboardingScreen(onDone = { newProfile ->
            scope.launch {
                db.profileDao().insert(newProfile)
                profile = newProfile
            }
        })
        return
    }

    FocusLensMain(repo)
}

/**
 * The very first screen on a fresh install, before the setup quiz: sign in with Google or
 * email, or skip and use the app without an account (everything works locally either way —
 * this is just the one place that was missing, since AccountScreen itself was previously
 * reachable only via the last tab).
 */
@Composable
fun WelcomeAuthScreen(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AccountScreen(onSignedIn = onContinue)
        }
        OutlinedButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Text("Skip for now — I'll sign in later")
        }
    }
}

@Composable
fun FocusLensMain(repo: UsageRepository) {
    var hasPermission by remember { mutableStateOf(repo.hasPermission()) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // Re-check permission and reload data whenever the user returns to the app,
    // for example after granting Usage access in system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = repo.hasPermission()
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasPermission) {
        var tab by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Usage") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Focus") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Study") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("To-do") })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Tracking") })
                Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("Journal") })
                Tab(selected = tab == 6, onClick = { tab = 6 }, text = { Text("Mental health") })
                Tab(selected = tab == 7, onClick = { tab = 7 }, text = { Text("Learn") })
                Tab(selected = tab == 8, onClick = { tab = 8 }, text = { Text("Explore") })
                Tab(selected = tab == 9, onClick = { tab = 9 }, text = { Text("Focus Score") })
                Tab(selected = tab == 10, onClick = { tab = 10 }, text = { Text("Playlists") })
                Tab(selected = tab == 11, onClick = { tab = 11 }, text = { Text("Account") })
                Tab(selected = tab == 12, onClick = { tab = 12 }, text = { Text("Birthdays") })
                Tab(selected = tab == 13, onClick = { tab = 13 }, text = { Text("Settings") })
                Tab(selected = tab == 14, onClick = { tab = 14 }, text = { Text("Guide") })
                Tab(selected = tab == 15, onClick = { tab = 15 }, text = { Text("Alarms") })
                Tab(selected = tab == 16, onClick = { tab = 16 }, text = { Text("Schedules") })
                Tab(selected = tab == 17, onClick = { tab = 17 }, text = { Text("Watch & vitals") })
                Tab(selected = tab == 18, onClick = { tab = 18 }, text = { Text("Coach") })
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> Dashboard(repo, refreshTick)
                    1 -> FocusScreen(repo, refreshTick)
                    2 -> StudyModeScreen()
                    3 -> TaskScreen()
                    4 -> CycleScreen()
                    5 -> JournalScreen()
                    6 -> MentalHealthScreen()
                    7 -> LearnScreen()
                    8 -> MediaSuggestionsScreen()
                    9 -> FocusScoreScreen()
                    10 -> FocusPlaylistsScreen()
                    11 -> AccountScreen()
                    12 -> BirthdaysScreen()
                    13 -> SettingsScreen()
                    14 -> GuideScreen()
                    15 -> AlarmScreen()
                    16 -> BlockSchedulesScreen()
                    17 -> VitalsScreen()
                    else -> CoachScreen()
                }
            }
        }
    } else {
        PermissionScreen()
    }
}

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("FocusLens", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "To show your screen time, FocusLens needs Usage access. " +
                "This is the same system data Digital Wellbeing uses. " +
                "It never leaves your phone.",
            fontSize = 16.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }) {
            Text("Open Usage access settings")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Find FocusLens in the list and switch on \"Permit usage access\", then come back.",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun Dashboard(repo: UsageRepository, refreshTick: Int) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(Range.TODAY) }
    var rows by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var breakdown by remember { mutableStateOf<Map<String, List<ScreenTime>>>(emptyMap()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val prefs = remember { FocusPrefs(context) }
    val history = remember { UsageHistoryRepository(context, repo) }
    val scope = rememberCoroutineScope()

    var hourlyToday by remember { mutableStateOf<List<com.focuslens.data.HourTotal>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            history.catchUp()
            history.catchUpHourly()
        }
        hourlyToday = withContext(Dispatchers.IO) { history.hourlyTotalsForToday() }
    }

    var customStart by remember { mutableStateOf<Long?>(null) }
    var customEnd by remember { mutableStateOf<Long?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    // Lets the user pick where to save the file (for example the Downloads folder).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            status = "Exporting, please wait..."
            scope.launch {
                status = try {
                    val r = withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)!!.use {
                            DataExporter(context).writeZip(it)
                        }
                    }
                    "Saved. ${r.events} events, ${r.sessions} app sessions, " +
                        "${r.days} days, ${r.apps} apps."
                } catch (e: Exception) {
                    "Export failed: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(range, refreshTick, customStart, customEnd) {
        if (range == Range.CUSTOM && (customStart == null || customEnd == null)) return@LaunchedEffect
        loading = true
        rows = withContext(Dispatchers.IO) {
            val (from, to) = when (range) {
                Range.CUSTOM -> customStart!! to customEnd!!
                Range.MONTH -> (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
                Range.YEAR -> (System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
                else -> null to null
            }
            val exact = if (from != null && to != null) history.tryExact(from, to) else null
            exact ?: if (range == Range.CUSTOM) repo.loadCustom(customStart!!, customEnd!!) else repo.load(range)
        }
        breakdown = withContext(Dispatchers.IO) {
            try {
                if (range == Range.CUSTOM) emptyMap() else repo.screenBreakdown(range)
            } catch (e: Exception) {
                emptyMap()
            }
        }
        if (range == Range.TODAY) {
            hourlyToday = withContext(Dispatchers.IO) { history.hourlyTotalsForToday() }
        }
        loading = false
    }

    val total = rows.sumOf { it.millis }
    val max = rows.maxOfOrNull { it.millis } ?: 1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text("FocusLens", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Text(formatDuration(total), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text("time in apps, ${range.title.lowercase()}", fontSize = 14.sp, color = Color.Gray)

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Range.values().forEach { r ->
                FilterChip(
                    selected = r == range,
                    onClick = { range = r },
                    label = { Text(r.title) }
                )
            }
        }
        if (range == Range.CUSTOM) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickingStart = true }) {
                    Text(customStart?.let { formatPickedDate(it) } ?: "From")
                }
                OutlinedButton(onClick = { pickingEnd = true }) {
                    Text(customEnd?.let { formatPickedDate(it) } ?: "To")
                }
            }
            if (pickingStart) {
                DatePickerDialogPicker(
                    initial = customStart,
                    onPicked = { customStart = it; pickingStart = false },
                    onDismiss = { pickingStart = false }
                )
            }
            if (pickingEnd) {
                DatePickerDialogPicker(
                    initial = customEnd,
                    onPicked = { customEnd = it; pickingEnd = false },
                    onDismiss = { pickingEnd = false }
                )
            }
        }
        if (range.approximate) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Older history is only kept by Android as monthly or yearly totals, " +
                    "so this view is approximate and has no open counts.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Spacer(Modifier.height(16.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Reading usage data...", color = Color.Gray)
            }
        } else if (rows.isEmpty()) {
            Text("No usage recorded for this period yet.", color = Color.Gray)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (range == Range.TODAY && hourlyToday.any { it.millis > 0 }) {
                    item { HourlyUsageCard(hourlyToday) }
                }
                item { WeeklyReportCard(repo, history) }
                item { InsightsCard(rows, breakdown, range) }
                items(rows, key = { it.packageName }) { app ->
                    Column(
                        Modifier.clickable {
                            expanded = if (expanded == app.packageName) null else app.packageName
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Text(formatDuration(app.millis), fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { app.millis.toFloat() / max.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFFE3E0D8)
                        )
                        if (app.launches > 0) {
                            Text(
                                "${app.launches} opens",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        if (expanded != app.packageName) {
                            Text("Tap for details", fontSize = 11.sp, color = Color.LightGray)
                        } else {
                            AppDetails(app, breakdown[app.packageName].orEmpty(), range, prefs)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (status != null) {
            Text(status!!, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
        }
        OutlinedButton(
            onClick = {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                exportLauncher.launch("focuslens_export_$stamp.zip")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export everything (ZIP of CSV files)")
        }
    }
}

@Composable
fun DatePickerDialogPicker(initial: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { state.selectedDateMillis?.let(onPicked) ?: onDismiss() }) {
                Text("OK")
            }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state)
    }
}

fun formatPickedDate(millis: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(millis))
