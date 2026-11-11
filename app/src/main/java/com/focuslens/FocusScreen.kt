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
    val history = remember { UsageHistoryRepository(context, repo) }

    var rev by remember { mutableIntStateOf(0) }          // bump to re-read saved settings
    var a11y by remember { mutableStateOf(isAccessibilityOn(context)) }
    var apps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var peakHours by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }

    LaunchedEffect(tick) {
        a11y = isAccessibilityOn(context)
        apps = withContext(Dispatchers.IO) {
            val used = repo.load(Range.WEEK).filter { !isSystemish(it.packageName) }.take(40)
            // Always offer the feature-block apps (Instagram, YouTube, ...) when they're installed,
            // even with no recent usage recorded, so their Reels/Shorts switch is never missing.
            val usedPkgs = used.map { it.packageName }.toSet()
            val extra = SectionDetector.feedFeature.keys
                .filter { it !in usedPkgs && isInstalled(context, it) }
                .map { AppUsage(it, repo.labelFor(it), 0L, 0) }
            used + extra
        }
        peakHours = withContext(Dispatchers.IO) { history.hourlyTotalsRecent(7) }
    }

    // Reading rev here makes the list refresh after every change below.
    val blocked = remember(rev) { prefs.blocked() }
    val enabled = remember(rev) { prefs.enabled }
    val resisted = remember(rev, tick) { prefs.totalGaveUpToday() }

    var showAllApps by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Focus", fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Still won't turn on after that? Some Android builds block it from the switch " +
                                "entirely for sideloaded apps, no matter what. With a computer and a USB cable, " +
                                "turn on Settings > About phone > tap \"Build number\" 7 times > Developer " +
                                "options > USB debugging, then run this once from the computer: " +
                                "adb shell appops set --uid com.focuslens ACCESS_RESTRICTED_SETTINGS allow " +
                                "— then the Accessibility switch above should work normally.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        item {
            val seen = remember(tick) { prefs.lastSeen() }
            val ago = seen?.let { (System.currentTimeMillis() - it.third) / 1000 }
            val reelsOn = remember(rev) { prefs.isSectionBlocked(SectionDetector.INSTAGRAM, "Reels") }
            val beat = FocusAccessibilityService.lastHeartbeat
            val beatAgo = if (beat == 0L) -1L else (System.currentTimeMillis() - beat) / 1000L
            if (a11y && (beatAgo < 0L || beatAgo > 90L)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("FocusLens may be paused by your phone", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(
                            "Blocks stop working when Android pauses the app to save battery. Turn off battery " +
                                "restrictions for FocusLens to keep blocks working.",
                            fontSize = 12.sp, color = Color.Gray
                        )
                        OutlinedButton(onClick = {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Open battery settings") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            val capped = remember(tick) { prefs.lastSeenCapped() }
            Text(
                "Reels check: Accessibility ${if (a11y) "on" else "OFF"} · " +
                    "service connected ${if (FocusAccessibilityService.connected) "yes" else "NO"} · " +
                    "Reels switch ${if (reelsOn) "on" else "off"}" +
                    (if (seen != null) " · last seen in ${SectionDetector.displayName[seen.first] ?: seen.first}: " +
                        "${seen.second} (${ago}s ago)" + (if (capped) " · scan hit limit" else "")
                    else " · no app screen seen yet"),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        item {
            var skipOn by remember(rev) { mutableStateOf(prefs.skipReelEnabled()) }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Skip reel button on Instagram", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Text(
                            "A Skip reel button while you scroll the feed, stories or Reels. Needs \"Display over other apps\".",
                            fontSize = 12.sp, color = Color.Gray
                        )
                    }
                    Switch(checked = skipOn, onCheckedChange = { on ->
                        if (on && !android.provider.Settings.canDrawOverlays(context)) {
                            context.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:" + context.packageName)
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            prefs.setSkipReelEnabled(on)
                            skipOn = on
                        }
                    })
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
            val doomScrollOn = remember(rev) { prefs.doomScrollReminderEnabled() }
            val doomScrollThreshold = remember(rev) { prefs.doomScrollThreshold() }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Doom-scroll reminders", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Text(
                                "A nudge, not a block: after a continuous streak of scrolling in Instagram, " +
                                    "TikTok, Snapchat and the other tracked apps, a quick \"still scrolling?\" check-in " +
                                    "with the choice to keep going or take a break.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = doomScrollOn, onCheckedChange = {
                            prefs.setDoomScrollReminderEnabled(it)
                            rev++
                        })
                    }
                    if (doomScrollOn) {
                        Spacer(Modifier.height(10.dp))
                        MinuteRow("Remind every (minutes, 0 = off)", prefs.scrollReminderMinutes(), 0, 60) {
                            prefs.setScrollReminderMinutes(it); rev++
                        }
                        MinuteRow("Break after (minutes of scrolling, 0 = off)", prefs.scrollBreakAfterMinutes(), 0, 180) {
                            prefs.setScrollBreakAfterMinutes(it); rev++
                        }
                        MinuteRow("Break length (minutes, 0 = none)", prefs.breakMinutes(), 0, 120) {
                            prefs.setBreakMinutes(it); rev++
                        }
                        MinuteRow("Breaks per day before locked until 6am (0 = never)", prefs.breaksPerDayLimit(), 0, 10) {
                            prefs.setBreaksPerDayLimit(it); rev++
                        }
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

        if (resisted > 0) {
            item { RewardSuggestionCard(title = "You chose not to open a blocked app today — nice") }
        }

        if (peakHours.values.any { it > 0 }) {
            item {
                val sorted = peakHours.entries.sortedByDescending { it.value }
                val top = sorted.take(2).filter { it.value > 0 }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("When you actually pick up your phone", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (top.size == 1)
                                "Over the last 7 days, you're most often on your phone around " +
                                    "${formatHourLabel(top[0].key)}–${formatHourLabel((top[0].key + 1) % 24)} " +
                                    "(${formatDurationHMS(top[0].value)} total in that hour across the week)."
                            else
                                "Over the last 7 days, your two busiest hours are around " +
                                    "${formatHourLabel(top[0].key)} and ${formatHourLabel(top[1].key)} " +
                                    "(${formatDurationHMS(top[0].value)} and ${formatDurationHMS(top[1].value)} " +
                                    "total across the week, in those hours).",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // ---- Section: Feature blocking — just the one addictive tab in an otherwise-allowed app ----
        val featureApps = apps.filter { SectionDetector.feedFeature.containsKey(it.packageName) }
        if (featureApps.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Feature blocking",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Block just the one addictive tab, keep the rest of the app usable — independent of " +
                        "whether the app itself is blocked above.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            items(featureApps, key = { "feature-${it.packageName}" }) { app ->
                val featureName = SectionDetector.feedFeature.getValue(app.packageName)
                val featureBlocked = remember(rev, app.packageName) {
                    prefs.isSectionBlocked(app.packageName, featureName)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Block $featureName only — rest of ${app.label} still works", fontSize = 12.sp, color = Color.Gray)
                    }
                    Switch(checked = featureBlocked, onCheckedChange = {
                        prefs.setSectionBlocked(app.packageName, featureName, it)
                        rev++
                    })
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "Apps (last 7 days)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            if (!a11y) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "Switches below save instantly, but nothing actually blocks until Accessibility " +
                        "is on (see the card above) — turning a switch on here just queues it up.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        // ---- Section: Apps — whole-app block, on/off, with a daily limit once blocked ----
        item {
            var reelRev by remember { mutableStateOf(0) }
            val reelCap = remember(reelRev) { prefs.reelLimit() }
            Column(Modifier.fillMaxWidth()) {
                Text("Reels per day", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "How many times you can open Instagram Reels each day. " +
                        "Used today: ${prefs.reelsToday()}. 0 means no limit.",
                    fontSize = 12.sp, color = Color.Gray
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { prefs.setReelLimit(reelCap - 1); reelRev++ }) { Text("-1") }
                    Text(if (reelCap == 0) "No limit" else "$reelCap a day", fontWeight = FontWeight.Medium)
                    OutlinedButton(onClick = { prefs.setReelLimit(reelCap + 1); reelRev++ }) { Text("+1") }
                }
            }
        }
        items(if (showAllApps) apps else apps.take(6), key = { "app-${it.packageName}" }) { app ->
            val isBlocked = app.packageName in blocked
            val avgMillisPerDay = app.millis / 7
            val avgMin = (avgMillisPerDay / 60_000L).toInt()
            val limit = remember(rev, app.packageName) { prefs.limitMin(app.packageName) }

            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("about ${formatDurationHMS(avgMillisPerDay)} a day", fontSize = 12.sp, color = Color.Gray)
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
                    Spacer(Modifier.height(6.dp))
                    Text("Daily limit — ${formatMinutesHM(limit)}", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    DurationPickerRow(
                        selectedMinutes = limit.coerceAtLeast(5),
                        onSelect = { prefs.setLimitMin(app.packageName, it); rev++ },
                        maxMinutes = 480
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Under the limit: short challenge, then 10 minutes. Over it: three challenges, then 5 minutes.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (apps.size > 6) {
            item {
                OutlinedButton(onClick = { showAllApps = !showAllApps }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAllApps) "Show top 6 only" else "Show all ${apps.size} apps")
                }
            }
        }



        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun isInstalled(context: android.content.Context, pkg: String): Boolean = try {
    context.packageManager.getPackageInfo(pkg, 0)
    true
} catch (e: android.content.pm.PackageManager.NameNotFoundException) {
    false
}

/** A label with − and + buttons for a whole number between [min] and [max]. */
@Composable
private fun MinuteRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { if (value > min) onChange(value - 1) }) { Text("-") }
        Text("$value", fontWeight = FontWeight.Medium)
        OutlinedButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}
