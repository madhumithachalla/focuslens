package com.focuslens

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A minimal home screen: time and date, extra time zones, apps as plain text, favourites, and
 * gestures (swipe up to search, swipe down for notifications). Opt-in: it only becomes the home
 * screen if the person chooses it in Android's home app settings. Nothing is sent anywhere.
 */
class MinimalHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MinimalHomeScreen() } }
    }
}

data class LaunchableApp(val pkg: String, val label: String)

/** Time zones offered for the home screen. Shown with a short city name. */
val HOME_TIMEZONES: List<Pair<String, String>> = listOf(
    "Australia/Melbourne" to "Melbourne",
    "Australia/Sydney" to "Sydney",
    "Pacific/Auckland" to "Auckland",
    "Asia/Singapore" to "Singapore",
    "Asia/Kolkata" to "India",
    "Asia/Dubai" to "Dubai",
    "Europe/London" to "London",
    "Europe/Paris" to "Paris",
    "Africa/Lagos" to "Lagos",
    "America/New_York" to "New York",
    "America/Los_Angeles" to "Los Angeles",
    "America/Sao_Paulo" to "São Paulo"
)

/** The minimal home screen's choices: favourites and extra time zones, kept on the phone. */
object HomePrefs {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("minimal_home", Context.MODE_PRIVATE)

    fun favorites(c: Context): List<String> = list(c, "favorites")
    fun setFavorites(c: Context, v: List<String>) = sp(c).edit().putString("favorites", v.joinToString(",")).apply()

    fun zones(c: Context): List<String> = list(c, "zones")
    fun setZones(c: Context, v: List<String>) = sp(c).edit().putString("zones", v.joinToString(",")).apply()

    private fun list(c: Context, key: String): List<String> =
        sp(c).getString(key, "").orEmpty().split(",").filter { it.isNotBlank() }
}

/** Apps that can be opened from the home screen. */
private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

/** Minutes of foreground time today, per app. Empty if usage access is not granted. */
private fun todayMillisByApp(context: Context): Map<String, Long> {
    val repo = UsageRepository(context)
    if (!repo.hasPermission()) return emptyMap()
    return repo.load(Range.TODAY).associate { it.packageName to it.millis }
}

@Composable
fun MinimalHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fg = Color(0xFFF2F2F2)
    val dim = Color(0xFF9A9A9A)

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val apps = remember { loadLaunchableApps(context) }
    var favorites by remember { mutableStateOf(HomePrefs.favorites(context)) }
    var zones by remember { mutableStateOf(HomePrefs.zones(context)) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var todayMillis by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var warnFor by remember { mutableStateOf<LaunchableApp?>(null) }
    var addingZone by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        todayMillis = withContext(Dispatchers.IO) { todayMillisByApp(context) }
    }

    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }

    fun open(pkg: String) {
        context.packageManager.getLaunchIntentForPackage(pkg)?.let { context.startActivity(it) }
    }

    fun toggleFavorite(app: LaunchableApp) {
        if (app.pkg in favorites) {
            favorites = favorites - app.pkg
            HomePrefs.setFavorites(context, favorites)
            return
        }
        val used = todayMillis[app.pkg] ?: 0L
        if (used >= 30 * 60_000L) {
            warnFor = app
        } else {
            favorites = favorites + app.pkg
            HomePrefs.setFavorites(context, favorites)
        }
    }

    fun showNotifications() {
        val service = FocusAccessibilityService.instance
        note = if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            null
        } else {
            "Swipe down needs FocusLens switched on in Accessibility settings. Turn it on there, then try again."
        }
    }

    val shown = if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    val favoriteApps = favorites.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        when {
                            dragTotal < -120f -> searching = true
                            dragTotal > 120f -> showNotifications()
                        }
                        dragTotal = 0f
                    },
                    onVerticalDrag = { _, amount -> dragTotal += amount }
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp)
        ) {
            Text(timeFmt.format(Date(now)), fontSize = 64.sp, fontWeight = FontWeight.Light, color = fg)
            Text(dateFmt.format(Date(now)), fontSize = 16.sp, color = dim)

            // Other time zones the person wants to see.
            zones.forEach { id ->
                val label = HOME_TIMEZONES.firstOrNull { it.first == id }?.second ?: id
                val zoneFmt = remember(id) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(id) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$label  ${zoneFmt.format(Date(now))}", fontSize = 18.sp, color = fg)
                    TextButton(onClick = {
                        zones = zones - id
                        HomePrefs.setZones(context, zones)
                    }) { Text("Remove", color = dim) }
                }
            }
            TextButton(onClick = { addingZone = true }) { Text("+ Add a time zone", color = dim) }

            if (searching) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { searching = false; query = "" }) { Text("Close search", color = dim) }
            }

            if (favoriteApps.isNotEmpty() && !searching) {
                Spacer(Modifier.height(24.dp))
                Text("Favourites", fontSize = 12.sp, color = dim)
                favoriteApps.forEach { app ->
                    AppLine(app, isFavorite = true, fg = fg, onOpen = { open(app.pkg) }, onStar = { toggleFavorite(app) })
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(if (searching) "Results" else "All apps", fontSize = 12.sp, color = dim)
            shown.forEach { app ->
                AppLine(
                    app,
                    isFavorite = app.pkg in favorites,
                    fg = fg,
                    onOpen = { open(app.pkg) },
                    onStar = { toggleFavorite(app) }
                )
            }

            note?.let { Spacer(Modifier.height(12.dp)); Text(it, fontSize = 12.sp, color = dim) }

            Spacer(Modifier.height(32.dp))
            Text("Swipe up to search · swipe down for notifications", fontSize = 11.sp, color = dim)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Set as my home screen", color = fg) }
        }
    }

    if (addingZone) {
        AlertDialog(
            onDismissRequest = { addingZone = false },
            title = { Text("Add a time zone") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HOME_TIMEZONES.filter { it.first !in zones }.forEach { (id, label) ->
                        TextButton(onClick = {
                            zones = zones + id
                            HomePrefs.setZones(context, zones)
                            addingZone = false
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addingZone = false }) { Text("Close") } }
        )
    }

    warnFor?.let { app ->
        val mins = (todayMillis[app.pkg] ?: 0L) / 60_000L
        AlertDialog(
            onDismissRequest = { warnFor = null },
            title = { Text("Favourite ${app.label}?") },
            text = {
                Text(
                    "You have used ${app.label} for $mins minutes today. Favourites are for apps you open on purpose, " +
                        "not the ones you use most. Add it anyway?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    favorites = favorites + app.pkg
                    HomePrefs.setFavorites(context, favorites)
                    warnFor = null
                }) { Text("Add anyway") }
            },
            dismissButton = { TextButton(onClick = { warnFor = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AppLine(app: LaunchableApp, isFavorite: Boolean, fg: Color, onOpen: () -> Unit, onStar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            app.label,
            fontSize = 20.sp,
            color = fg,
            modifier = Modifier.weight(1f).clickable(onClick = onOpen)
        )
        TextButton(onClick = onStar) { Text(if (isFavorite) "★" else "☆", color = fg) }
    }
}
