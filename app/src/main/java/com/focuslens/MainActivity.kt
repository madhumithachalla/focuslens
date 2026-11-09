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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.IconButton
import androidx.compose.foundation.verticalScroll

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                typography = AppTypography,
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1F3A34),
                    background = Color(0xFFF7F5F0),
                    surface = Color(0xFFF7F5F0)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // targetSdk 36 means the system draws edge-to-edge by default and no longer
                    // reserves space for the status/navigation bars on its own — without this,
                    // the top tab row sits flush against the status bar/clock and bottom content
                    // sits under the gesture bar. This is the one place that needs to know about
                    // it; everything below keeps assuming a normal, inset-free canvas.
                    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        FocusLensApp(UsageRepository(applicationContext))
                    }
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
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.padding(horizontal = 28.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_focuslens_symbol), contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Image(painterResource(R.drawable.ic_focuslens_wordmark), contentDescription = "FocusLens", modifier = Modifier.height(22.dp))
                    }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DRAWER_GROUPS.forEach { group ->
                            Text(
                                group.first.uppercase(),
                                modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray
                            )
                            group.second.forEach { item ->
                                NavigationDrawerItem(
                                    label = { Text(item.second) },
                                    selected = tab == item.first,
                                    onClick = {
                                        tab = item.first
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top bar: the menu button opens the drawer with every page.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Text("☰", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(4.dp))
                    Image(painterResource(R.drawable.ic_focuslens_symbol), contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Image(painterResource(R.drawable.ic_focuslens_wordmark), contentDescription = "FocusLens", modifier = Modifier.height(20.dp))
                    Spacer(Modifier.weight(1f))
                    Text(TAB_LABELS.getOrElse(tab) { "" }, fontSize = 14.sp, color = Color.Gray)
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
                    18 -> CoachScreen()
                    19 -> MedsScreen()
                    20 -> QuizSettingsScreen()
                    21 -> RewardsScreen()
                    22 -> AchievementsScreen()
                    23 -> AppUpdatesScreen()
                    24 -> DiaryScreen()
                    else -> QuizSettingsScreen()
                }
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
        Text("FocusLens", fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
        Spacer(Modifier.height(20.dp))
        Text(
            "Got \"App was denied access\" instead of the switch? That's Android blocking a " +
                "sensitive permission for apps installed outside the Play Store (this debug build is " +
                "sideloaded) — it isn't anything wrong with FocusLens itself, and takes one extra step:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Settings > Apps > FocusLens > menu (three dots, top right) > \"Allow restricted settings\" " +
                "— then come back here and open Usage access settings again.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                )
            )
        }) {
            Text("Open FocusLens app info")
        }
    }
}

@Composable
fun Dashboard(repo: UsageRepository, refreshTick: Int) {
    val context = LocalContext.current
    var range by remember { mutableStateOf(Range.TODAY) }
    var wbRows by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var flRows by remember { mutableStateOf<List<AppUsage>?>(null) }
    var flCover by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var buckets by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var source by remember { mutableStateOf(0) } // 0 = Digital Wellbeing, 1 = FocusLens history
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

    // Saves the plain HTML report (both sources side by side) to a file the person picks.
    val reportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) {
            status = "Making the report..."
            scope.launch {
                status = try {
                    val html = SimpleReport.html(context)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)!!.use { it.write(html.toByteArray()) }
                    }
                    "Report saved. Open it in a browser, or print it to PDF."
                } catch (e: Exception) {
                    "Report failed: ${e.message}"
                }
            }
        }
    }

    LaunchedEffect(range, refreshTick, customStart, customEnd, source) {
        if (range == Range.CUSTOM && (customStart == null || customEnd == null)) return@LaunchedEffect
        loading = true
        var bk: List<Pair<String, Long>> = emptyList()
        val result = withContext(Dispatchers.IO) {
            val (from, to) = when (range) {
                Range.CUSTOM -> customStart!! to customEnd!!
                Range.MONTH -> (System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
                Range.YEAR -> (System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000) to System.currentTimeMillis()
                else -> null to null
            }
            val wellbeing = if (range == Range.CUSTOM) repo.loadCustom(customStart!!, customEnd!!) else repo.load(range)
            val exact = if (from != null && to != null) history.tryExact(from, to) else null
            val cover = if (from != null && to != null) history.coverage(from, to) else null

            // Over-time bars use the same source as the totals, one bar per day (or per month for long ranges).
            val now = System.currentTimeMillis()
            val window: Pair<Long, Long>? = when (range) {
                Range.WEEK -> (now - 7L * 24 * 60 * 60 * 1000) to now
                Range.MONTH -> (now - 30L * 24 * 60 * 60 * 1000) to now
                Range.YEAR -> (now - 365L * 24 * 60 * 60 * 1000) to now
                Range.CUSTOM -> customStart!! to customEnd!!
                else -> null
            }
            bk = window?.let { (a, b) ->
                bucketTotals(a, b) { x, y ->
                    if (source == 1) history.tryExact(x, y) ?: emptyList() else repo.loadCustom(x, y)
                }
            } ?: emptyList()

            Triple(wellbeing, exact, cover)
        }
        wbRows = result.first
        flRows = result.second
        flCover = result.third
        buckets = bk
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

    val rows = if (source == 1 && flRows != null) flRows!! else wbRows
    val appRows = rows.filter { !BrowserApps.isBrowser(it.packageName) }
    val browserRows = rows.filter { BrowserApps.isBrowser(it.packageName) }
    val appTotal = appRows.sumOf { it.millis }
    val browserTotal = browserRows.sumOf { it.millis }
    val total = appTotal + browserTotal
    val max = rows.maxOfOrNull { it.millis } ?: 1L

    // The whole page scrolls, top to bottom, and uses the full screen height.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(formatDuration(total), fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text(range.title, fontSize = 13.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        UpdateCard()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            DonutSplit(appTotal, browserTotal, Modifier.size(96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendRow(UsageColors.Apps, "Apps", formatDuration(appTotal))
                LegendRow(UsageColors.Browsers, "Browsers", formatDuration(browserTotal))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Digital Wellbeing", "FocusLens").forEachIndexed { i, label ->
                FilterChip(selected = source == i, onClick = { source = i }, label = { Text(label) })
            }
        }
        flCover?.takeIf { source == 1 }?.let { (have, expected) ->
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { have.toFloat() / expected.coerceAtLeast(1).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = UsageColors.Apps,
                trackColor = UsageColors.Track
            )
            Text("$have of $expected days stored", fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(14.dp))
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
            Text("Older data is approximate", fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(14.dp))
        if (loading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Loading…", color = Color.Gray)
            }
        } else if (rows.isEmpty()) {
            Text("No usage yet.", color = Color.Gray)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (range == Range.TODAY && hourlyToday.any { it.millis > 0 }) {
                    HourlyUsageCard(hourlyToday)
                }
                if (buckets.isNotEmpty()) {
                    OverTimeCard(buckets)
                }
                WeeklyReportCard(repo, history)
                InsightsCard(rows, breakdown, range)

                SectionTitle("Apps", appTotal)
                appRows.forEach { app ->
                    AppUsageItem(
                        app = app,
                        max = max,
                        color = UsageColors.Apps,
                        open = expanded == app.packageName,
                        onToggle = {
                            expanded = if (expanded == app.packageName) null else app.packageName
                        },
                        screens = breakdown[app.packageName].orEmpty(),
                        range = range,
                        prefs = prefs
                    )
                }

                if (browserRows.isNotEmpty()) {
                    SectionTitle("Browsers", browserTotal)
                    browserRows.forEach { app ->
                        AppUsageItem(
                            app = app,
                            max = max,
                            color = UsageColors.Browsers,
                            open = expanded == app.packageName,
                            onToggle = {
                                expanded = if (expanded == app.packageName) null else app.packageName
                            },
                            screens = breakdown[app.packageName].orEmpty(),
                            range = range,
                            prefs = prefs
                        )
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
                    .format(java.util.Date())
                reportLauncher.launch("focuslens_report_$stamp.html")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Download simple report (HTML, both sources)")
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


private val TAB_LABELS = listOf(
    "Usage", "Focus", "Study", "To-do", "Tracking", "Journal", "Mental health", "Learn", "Explore",
    "Focus Score", "Playlists", "Account", "Birthdays", "Settings", "Guide", "Alarms", "Schedules",
    "Watch & vitals", "Coach", "Meds", "Quiz", "Rewards", "Achievements", "App updates", "Diary"
)

/** Every page, grouped for the menu. The numbers are the tab indexes used by the screen switch. */
private val DRAWER_GROUPS: List<Pair<String, List<Pair<Int, String>>>> = listOf(
    "Overview" to listOf(0 to "Usage", 9 to "Focus Score", 18 to "Coach", 17 to "Watch & vitals", 22 to "Achievements"),
    "Focus" to listOf(1 to "Focus", 2 to "Study", 15 to "Alarms", 16 to "Schedules", 20 to "Quiz"),
    "Body & mind" to listOf(4 to "Tracking", 5 to "Journal", 6 to "Mental health", 19 to "Meds", 10 to "Playlists", 24 to "Diary"),
    "Learn" to listOf(7 to "Learn", 8 to "Explore", 14 to "Guide", 21 to "Rewards"),
    "Me" to listOf(3 to "To-do", 11 to "Account", 12 to "Birthdays", 13 to "Settings", 23 to "App updates")
)
