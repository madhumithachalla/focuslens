package com.focuslens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val GITHUB_URL = "https://github.com/madhumithachalla/focuslens"

private data class GuideSection(val emoji: String, val title: String, val body: String)

private val SECTIONS = listOf(
    GuideSection("📊", "Usage", "Shows real screen time read from Android's own Usage Access data — the same source Digital Wellbeing uses. Tap an app to see which section inside it (Reels, Shorts) took the time."),
    GuideSection("🛡️", "Focus — per-app blocking", "Toggle an app on to set a daily minute limit. Opening it shows a short challenge (breathing, math, a typed phrase) that gets harder the more times you open it that day."),
    GuideSection("🔒", "Hard daily locks", "Two independent stops, 3 skips/day by default: hit your skip count OR your daily minute limit and that app locks completely until midnight — no further challenge to beat, same as Minimalist Focus Launcher's time-based app locks."),
    GuideSection("🧘", "Hyper Focus", "Blocks every app on the phone at once, not just the ones you toggled — for when you need a hard stop, not a per-app one. No challenge during it (that defeats the point), but it's never a true lockout: typing a confirmation phrase always ends it early."),
    GuideSection("🩸", "Cycle & energy", "Log a period start or a general energy/mood day. Once you've logged 2+ starts, it shows a rolling-average estimate of your next cycle — explicitly your own data, not a clinical prediction."),
    GuideSection("📓", "Journal", "Daily prompts or free writing, one entry per day, with a mood tag. Builds a real streak — hits a celebration moment at 3/7/14/30 days."),
    GuideSection("💭", "Mental health", "A 14-day mood trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis."),
    GuideSection("📰", "Reading", "A short list of honest starting points from established health organizations — not a feed, just a few real links."),
    GuideSection("🎧", "Playlists", "Connects to your own installed Spotify app (not a separate login) and plays curated focus playlists — lo-fi, deep focus, rain.")
)

@Composable
fun GuideScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Spacer(Modifier.height(16.dp))
                Text("Guide", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("What each part of FocusLens actually does.", fontSize = 14.sp, color = Color.Gray)
            }
        }

        items(SECTIONS) { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(s.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(s.body, fontSize = 13.sp, color = Color.Gray)
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Get the app", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "FocusLens isn't on the Play Store or App Store yet — there's no public listing to link to. " +
                            "Android is the only platform it runs on right now (it's built natively in Kotlin, not " +
                            "cross-platform), so there's no iOS version to download either.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    }) {
                        Text("View source on GitHub (Android)")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
