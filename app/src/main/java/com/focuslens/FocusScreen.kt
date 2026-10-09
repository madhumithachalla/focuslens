package com.focuslens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** True when the user has switched on the FocusLens accessibility service. */
fun isAccessibilityOn(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val cls = FocusAccessibilityService::class.java.simpleName
    return enabled.split(':').any { it.contains(context.packageName) && it.contains(cls) }
}

private fun isSystemish(pkg: String) =
    pkg.contains("launcher") || pkg.contains("systemui") || pkg == "android" ||
        pkg.contains("wellbeing") || pkg.contains("inputmethod")

@Composable
fun FocusScreen(repo: UsageRepository, tick: Int) {
    val context = LocalContext.current
    val prefs = remember { FocusPrefs(context) }

    var rev by remember { mutableIntStateOf(0) }          // bump to re-read saved settings
    var a11y by remember { mutableStateOf(isAccessibilityOn(context)) }
    var apps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }

    LaunchedEffect(tick) {
        a11y = isAccessibilityOn(context)
        apps = withContext(Dispatchers.IO) {
            repo.load(Range.WEEK).filter { !isSystemish(it.packageName) }.take(40)
        }
    }

    // Reading rev here makes the list refresh after every change below.
    val blocked = remember(rev) { prefs.blocked() }
    val enabled = remember(rev) { prefs.enabled }
    val resisted = remember(rev, tick) { prefs.totalGaveUpToday() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Focus", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Pick the apps that pull you in. When you open one, you get a short challenge first.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (a11y) Color(0xFFE3EFE8) else Color(0xFFF6E3DF)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (a11y) {
                        Text("Blocking is active", fontWeight = FontWeight.Medium)
                        Text(
                            "FocusLens only reads which app is open. It does not read your screen.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        Text("Blocking is off", fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "To show the challenge when you open an app, turn on FocusLens in " +
                                "Accessibility settings (look under Installed apps or Downloaded apps). " +
                                "FocusLens only reads which app is open, not what is on screen.",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Open Accessibility settings")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "If the switch is greyed out: Settings > Apps > FocusLens > menu (three dots) > " +
                                "Allow restricted settings.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        item {
            val hyperActive = remember(rev, tick) { prefs.isHyperFocusActive() }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F3A34))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Hyper Focus", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (hyperActive) "Active — blocking every app except calls."
                        else "Blocks every app on the phone, not just the ones below — for when you need a hard stop.",
                        color = Color(0xFFBFD3C9),
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    if (hyperActive) {
                        Button(onClick = {
                            prefs.endHyperFocus()
                            rev++
                        }) { Text("End early") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25, 45, 60).forEach { mins ->
                                Button(onClick = {
                                    prefs.startHyperFocus(mins)
                                    rev++
                                }) { Text("${mins}m") }
                            }
                        }
                    }
                }
            }
        }

        item {
            val socialBrowserBlock = remember(rev) { prefs.socialBrowserBlockEnabled() }
            val academicOnly = remember(rev) { prefs.youtubeAcademicOnly() }
            val allowlist = remember(rev) { prefs.customAcademicAllowlist() }
            var newDomain by remember { mutableStateOf("") }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Browser & YouTube content rules", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Block social media in Chrome", fontSize = 14.sp)
                            Text(
                                "Blocks the whole site (Instagram, Facebook, X/Twitter, TikTok, Reddit, Snapchat, " +
                                    "Pinterest, Threads) in Chrome, not just an app. Academic sites (.edu, Google " +
                                    "Docs/Drive/Classroom, Moodle-style LMS, library/journal sites) are never blocked. " +
                                    "Reads the address bar only while this is on.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = socialBrowserBlock, onCheckedChange = {
                            prefs.setSocialBrowserBlockEnabled(it)
                            rev++
                        })
                    }

                    if (socialBrowserBlock) {
                        Spacer(Modifier.height(8.dp))
                        Text("Also allow (e.g. a uni portal not on the default list):", fontSize = 12.sp, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newDomain,
                                onValueChange = { newDomain = it },
                                singleLine = true,
                                placeholder = { Text("example.edu.au", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                if (newDomain.isNotBlank()) {
                                    prefs.addAcademicAllow(newDomain)
                                    newDomain = ""
                                    rev++
                                }
                            }) { Text("Add") }
                        }
                        allowlist.forEach { d ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(d, fontSize = 13.sp)
                                OutlinedButton(onClick = { prefs.removeAcademicAllow(d); rev++ }) { Text("Remove") }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("YouTube: academic/podcast only", fontSize = 14.sp)
                            Text(
                                "Blocks anything in YouTube that doesn't look academic or like a podcast " +
                                    "(a keyword check on the title/channel text on screen — best-effort, not a real " +
                                    "content check, so it will get some wrong in both directions).",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = academicOnly, onCheckedChange = {
                            prefs.setYoutubeAcademicOnly(it)
                            rev++
                        })
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Focus mode", fontWeight = FontWeight.Medium)
                    Text(
                        if (resisted > 0) "You chose not to open an app $resisted time${if (resisted == 1) "" else "s"} today"
                        else "Turn off to pause all blocking",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Switch(checked = enabled, onCheckedChange = {
                    prefs.enabled = it
                    rev++
                })
            }
        }

        item {
            Text(
                "Your most used apps (last 7 days)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        items(apps, key = { it.packageName }) { app ->
            val isBlocked = app.packageName in blocked
            val avgMin = (app.millis / 7 / 60_000L).toInt()
            val limit = remember(rev, app.packageName) { prefs.limitMin(app.packageName) }

            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("about $avgMin min a day", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = isBlocked, onCheckedChange = { on ->
                        if (on) {
                            // Start at half of the current daily average, in steps of 5 minutes.
                            prefs.setLimitMin(app.packageName, ((avgMin / 2) / 5 * 5).coerceIn(5, 120))
                        }
                        prefs.setBlocked(app.packageName, on)
                        rev++
                    })
                }
                if (isBlocked) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Daily limit", fontSize = 13.sp, color = Color.Gray)
                        OutlinedButton(onClick = {
                            prefs.setLimitMin(app.packageName, limit - 5)
                            rev++
                        }) { Text("-5") }
                        Text(if (limit == 0) "0 min" else "$limit min", fontWeight = FontWeight.Medium)
                        OutlinedButton(onClick = {
                            prefs.setLimitMin(app.packageName, limit + 5)
                            rev++
                        }) { Text("+5") }
                    }
                    Text(
                        "Under the limit: short challenge, then 10 minutes. Over it: three challenges, then 5 minutes.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                val featureName = when (app.packageName) {
                    SectionDetector.INSTAGRAM -> "Reels"
                    SectionDetector.YOUTUBE -> "Shorts"
                    else -> null
                }
                if (featureName != null) {
                    val featureBlocked = remember(rev, app.packageName) {
                        prefs.isSectionBlocked(app.packageName, featureName)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Block $featureName only (rest of ${app.label} still works)", fontSize = 12.sp, color = Color.Gray)
                        Switch(checked = featureBlocked, onCheckedChange = {
                            prefs.setSectionBlocked(app.packageName, featureName, it)
                            rev++
                        })
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
