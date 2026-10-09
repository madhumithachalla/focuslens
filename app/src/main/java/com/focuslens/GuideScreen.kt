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
private const val APK_URL = "https://github.com/madhumithachalla/focuslens/releases/latest/download/focuslens-debug.apk"

private data class GuideSection(val emoji: String, val title: String, val body: String)

private val SECTIONS = listOf(
    GuideSection("📊", "Usage", "Real screen time from Android's own Usage Access data. Today, week, month, year, all-time, or pick a custom date range. Tap an app to see which section inside it (Reels, Shorts) took the time. FocusLens also snapshots each finished day into its own storage, so Month/Year views stay exact once enough days are snapshotted, instead of degrading into Android's coarser monthly buckets after about a week — this only protects data going forward, it can't recover days already gone."),
    GuideSection("📈", "Focus Score", "A composite of how consistently you resist opening blocked apps and keep up your journal — reflects app habits only, explicitly not a grade or productivity prediction."),
    GuideSection("🛡️", "Focus — per-app blocking", "Toggle an app on to set a daily minute limit. Opening it shows a short challenge (breathing, math, a typed phrase) that gets harder the more times you open it that day."),
    GuideSection("🔒", "Hard daily locks", "Two independent stops, 3 skips/day by default: hit your skip count OR your daily minute limit and that app locks completely until 6am the next morning — no further challenge to beat, same as Minimalist Focus Launcher's time-based app locks. These counts live in FocusLens's own storage, keyed by the app's package name — uninstalling and reinstalling Instagram or YouTube doesn't reset them; only uninstalling FocusLens itself would."),
    GuideSection(
        "📉",
        "Reducing doom-scrolling",
        "If that's why you installed FocusLens (it's one of the options in setup), these are the pieces that " +
            "actually do it, stacked loosest to strictest: \"Block just Reels or Shorts\" below leaves the rest of " +
            "the app open; \"Block social media in Chrome\" closes the browser loophole those same apps/sites use; " +
            "a daily minute limit in Focus plus Hard daily locks caps the whole app, not just one section; Hyper " +
            "Focus is the full device-wide stop for when you need one hard switch instead of several small ones. " +
            "Mix and match — none of these require each other."
    ),
    GuideSection("🎬", "Block just Reels or Shorts", "In Focus, toggle \"Block Reels only\" or \"Block Shorts only\" on Instagram/YouTube's row — the rest of the app keeps working, just that one section is off-limits, no challenge to get past it while the toggle is on."),
    GuideSection("🌐", "Block social media in Chrome", "Blocks whole sites (Instagram, Facebook, X/Twitter, TikTok, Reddit, Snapchat, Pinterest, Threads) in Chrome, not just the apps — reads the address bar only while this is switched on. Academic sites (.edu, Google Docs/Drive/Classroom, common LMS/journal sites, plus anything you add) are never blocked by this rule."),
    GuideSection("🎓", "YouTube academic/podcast only", "When switched on, blocks anything in YouTube that doesn't look academic or like a podcast. This is a best-effort keyword check against the on-screen title/channel text, not real content classification — it will misjudge some videos in both directions. It's the one deliberate exception to \"never reads screen text,\" and only while this setting is on."),
    GuideSection("📚", "Study Mode", "A Minimalist-style black, text-only setup screen: pick a duration and a sound (a default lo-fi playlist, any Spotify playlist, your own local music app, or silence), then it blocks everything else device-wide for the session with a calm on-screen timer."),
    GuideSection("🧘", "Hyper Focus", "Blocks every app on the phone at once, not just the ones you toggled — for when you need a hard stop, not a per-app one. No challenge during it (that defeats the point), but it's never a true lockout: typing a confirmation phrase always ends it early."),
    GuideSection("🩸", "Tracking", "If you get periods, that's tracked directly: log a start today or any past date (useful if you forgot, or want to backfill history for a steadier average faster). Once you've logged 2+ starts, it shows a rolling-average estimate of your next cycle, current phase (follicular/fertile window/ovulation/luteal), an estimated fertile window, and commonly-cited \"what people often do in this phase\" guidance — each phase gets its own color, always shown with its name as text. Explicitly an estimate and widely-circulated wellness framing, not a clinical prediction, a diagnosis, a contraception method, or a rule to follow over how you actually feel. \"Import from watch\" reads period-start dates your watch or a health app (Samsung Health, Fitbit, Flo, etc.) may have already saved to Android's Health Connect. Everyone else (or anyone, alongside cycle tracking) can turn on Energy, Mood, Sleep quality and Water intake — four independent 1-5 trackers, log any subset on any day, nothing required."),
    GuideSection("🎂", "Birthdays", "Add friends/family with their birthday (or without one, just to start a wish checklist) and see who's coming up, soonest first. \"Import from Contacts\" reads names and birthdays already in your phone's Contacts app — this covers any Google account's contacts synced to the device — only if you tap it and grant the permission; otherwise add people by hand. Each year also gets its own checklist to tick off who's wished you on your birthday, so you don't lose track of who to thank or wish back — nothing reads your messages or calls automatically, you check it off yourself."),
    GuideSection("📓", "Journal", "Daily prompts or free writing, one entry per day, with a mood tag. Builds a real streak — hits a celebration moment at 3/7/14/30 days."),
    GuideSection("💭", "Mental health", "A 14-day mood trend built from your own Journal and Cycle entries — a mirror on your own pattern, not a diagnosis."),
    GuideSection("📰", "Learn", "Newsletters, articles and research hubs across focus, sleep, mental health, habits, study skills, sex education, LGBTQ+ support, and neurodivergence/ADHD — a few honest starting points, not a feed. Before \"Mark as read\" sticks, you answer a short self-reflection prompt (more for a dense research hub than a short newsletter) — not graded, just enough to make sure it wasn't a blind tap. Answers are discarded immediately, never stored."),
    GuideSection("🧩", "Disability & neurodivergence (optional)", "During setup, an entirely optional, skippable section lets you note if ADHD, autism, dyslexia, or similar is part of your experience — self-identified or diagnosed, whatever applies. It never gates any feature; it only helps more readily surface the Neurodivergence & ADHD section of Learn, including a real symptom screener designed by Harvard Medical School and the WHO (the ASRS), plus CHADD, ADDA, and other real professional support orgs."),
    GuideSection("🧭", "Suggested for you", "On Mental health: a pick from Learn based on simple, visible rules — a low mood/energy trend points at Mental Health resources, \"tired\" as your top missed-task reason points at Sleep, \"distracted\" points at Focus & Productivity, and if you flagged studying, reading or writing during setup, it falls back to Study Skills. Plainly rule-based, not AI, not a diagnosis."),
    GuideSection("🎬", "Explore", "A short curated list of real books, movies and series, filterable by interest. Not a live catalog and not linked to a specific streaming service — availability changes too often and by region to point at honestly."),
    GuideSection("🎁", "Reward suggestions", "After you resist opening a blocked app, or finish a Study Mode session, FocusLens offers one small, real-world thing to do with the moment — a stretch, a message to a friend, a glass of water. Plain curated list, no points or levels to chase."),
    GuideSection("🎧", "Playlists", "Connects to your own installed Spotify app (not a separate login) and plays curated focus playlists — lo-fi, deep focus, rain."),
    GuideSection("👤", "Account", "Optional sign-in with Google or email, same Firebase project as the web app — one account either way. Doesn't sync Journal/Cycle/To-do data yet (still local to this device); it's the foundation the community, achievement-sharing, and accountability-partner features will build on.")
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
                        "Not on the Play Store yet, but every push to main auto-builds a real, " +
                            "installable APK via GitHub Actions — this is an actual download, not a store listing.",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(APK_URL)))
                    }) {
                        Text("Download latest APK (Android)")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    }) {
                        Text("View source on GitHub")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "iOS: genuinely not possible to add here. This is a native Kotlin/Android app — " +
                            "an iOS version needs a separate Swift/SwiftUI codebase, a Mac with Xcode to build it, " +
                            "and a paid Apple Developer account to sign and distribute it, even just via TestFlight. " +
                            "None of that exists for this project yet, and I can't fabricate a download link for " +
                            "something that hasn't been built.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
