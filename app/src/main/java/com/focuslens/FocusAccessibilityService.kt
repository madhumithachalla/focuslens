package com.focuslens

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Watches which app comes to the front. By default it only reads the app's package name,
 * never what is on the screen. Two opt-in settings change that, and only while turned on:
 * the Chrome social-media block reads the address bar, and YouTube's academic/podcast-only
 * mode reads on-screen text for a keyword check — both held in memory for one comparison
 * and discarded. When a blocked app, feature, or site opens, it shows the matching block
 * screen.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val prefs by lazy { FocusPrefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastLaunch = 0L

    // Doom-scroll tracking: counts TYPE_VIEW_SCROLLED events in one continuous streak within
    // a tracked social app. A gap of more than SCROLL_IDLE_GAP_MS resets the streak — this is
    // meant to catch continuous scrolling, not ordinary on-and-off browsing across a session.
    private var scrollCount = 0
    private var scrollStreakPkg: String? = null
    private var lastScrollAt = 0L

    companion object {
        private const val SCROLL_IDLE_GAP_MS = 15_000L
    }

    // Windows that should never change which app we think is open.
    private val ignored = setOf(
        "com.android.systemui", "android", "com.google.android.apps.wellbeing"
    )

    // Re-checks every 10 seconds so a 10 minute pass cannot last forever.
    private val ticker = object : Runnable {
        override fun run() {
            current?.let { check(it) }
            handler.postDelayed(this, 10_000L)
        }
    }

    // Every 4 seconds, while a tracked social app (SectionDetector.tracked) is in front and
    // the screen is on, note which section is showing and add the elapsed time to it.
    private var lastSampleT = 0L
    private val sampler = object : Runnable {
        override fun run() {
            try {
                sample()
            } catch (e: Exception) {
                lastSampleT = 0L
            }
            handler.postDelayed(this, 4_000L)
        }
    }

    private fun sample() {
        val pkg = current
        if (pkg == null) {
            lastSampleT = 0L
            return
        }
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!power.isInteractive) {
            lastSampleT = 0L
            return
        }
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        when {
            pkg in SectionDetector.tracked -> sampleSocialApp(pkg, root)
            pkg == BrowserDetector.CHROME -> sampleBrowser(root)
            else -> lastSampleT = 0L
        }
    }

    /** Logs time-in-section as before, plus the two feature-level block rules (Reels/Shorts, academic-only YouTube). */
    private fun sampleSocialApp(pkg: String, root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        val section = SectionDetector.detect(pkg, root, prefs)
        val delta = if (lastSampleT == 0L) 0L else minOf(now - lastSampleT, 8_000L)
        lastSampleT = now
        if (delta > 0) prefs.addSection(pkg, section, delta)

        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing ||
            SchedulePauseActivity.showing
        ) return
        if (now - lastLaunch < 3_000L) return

        val feedFeature = SectionDetector.feedFeature[pkg]
        if (feedFeature != null && section == feedFeature && prefs.isSectionBlocked(pkg, feedFeature)) {
            val appName = SectionDetector.displayName[pkg] ?: pkg
            launchFeatureBlock(
                "$feedFeature is blocked",
                "You've turned off $feedFeature specifically — the rest of $appName still works."
            )
            return
        }

        if (pkg == SectionDetector.YOUTUBE && prefs.youtubeAcademicOnly() &&
            (section == "Watching a video" || section == "Shorts")
        ) {
            val text = YoutubeContent.visibleText(root)
            if (!YoutubeContent.looksAcademic(text)) {
                launchFeatureBlock(
                    "Entertainment is off",
                    "Academic/podcast-only mode is on for YouTube, and this doesn't look like either. " +
                        "Best-effort keyword check, not a real content check — turn it off in Focus settings if it's wrong."
                )
            }
        }
    }

    /** Chrome's own address bar, read only while the social-media browser block is on. */
    private fun sampleBrowser(root: AccessibilityNodeInfo) {
        lastSampleT = 0L
        if (!prefs.socialBrowserBlockEnabled()) return
        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing) return
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 3_000L) return
        val urlText = BrowserDetector.currentUrl(root) ?: return
        val host = BrowserDetector.hostOf(urlText)
        if (BrowserDetector.isSocial(host) && !BrowserDetector.isAcademic(host, prefs.customAcademicAllowlist())) {
            launchFeatureBlock(
                "Blocked in Chrome",
                "$host is on your social media block list. Academic sites are never blocked by this rule."
            )
        }
    }

    private fun launchFeatureBlock(title: String, message: String) {
        lastLaunch = System.currentTimeMillis()
        startActivity(
            Intent(this, FeatureBlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra("title", title)
                putExtra("message", message)
            }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sampler)
        handler.post(ticker)
        handler.post(sampler)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            trackScroll(event.packageName?.toString())
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg in ignored || pkg.contains("inputmethod")) return
        if (pkg.contains("launcher")) {
            current = null
            return
        }
        current = pkg
        check(pkg)
    }

    /** A nudge, not a block — see ScrollReminderActivity. Only tracked social apps (the ones
     *  actually built for an endless scroll) count; a long list in a shopping or news app
     *  doesn't trip this. */
    private fun trackScroll(pkg: String?) {
        if (pkg == null || pkg !in SectionDetector.tracked) return
        if (!prefs.doomScrollReminderEnabled()) return
        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing || ScrollReminderActivity.showing) return

        val now = System.currentTimeMillis()
        if (pkg != scrollStreakPkg || now - lastScrollAt > SCROLL_IDLE_GAP_MS) {
            scrollStreakPkg = pkg
            scrollCount = 0
        }
        scrollCount++
        lastScrollAt = now

        if (scrollCount >= prefs.doomScrollThreshold() && now - lastLaunch > 3_000L) {
            lastLaunch = now
            val appName = SectionDetector.displayName[pkg] ?: pkg
            startActivity(
                Intent(this, ScrollReminderActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("label", appName)
                    putExtra("scrollCount", scrollCount)
                }
            )
            scrollCount = 0
        }
    }

    private fun defaultDialerPackage(): String? =
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
            ?.defaultDialerPackage

    private fun check(pkg: String) {
        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing) return
        val now = System.currentTimeMillis()

        if (prefs.isHyperFocusActive()) {
            val allowed = pkg in prefs.hyperFocusAllowlist() || pkg == defaultDialerPackage()
            if (!allowed) {
                if (now - lastLaunch < 1_500L) return
                lastLaunch = now
                startActivity(
                    Intent(this, HyperFocusBlockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        putExtra("pkg", pkg)
                    }
                )
            }
            return
        }

        if (!prefs.enabled || !prefs.isBlocked(pkg)) return
        if (now < prefs.allowedUntil(pkg)) return
        // Block schedules: a blocked app opened inside an active schedule window gets the pause screen.
        val schedule = BlockSchedulePrefs(this).activeSchedule(now)
        if (schedule != null) {
            if (now - lastLaunch < 1_500L) return
            lastLaunch = now
            startActivity(
                Intent(this, SchedulePauseActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("pkg", pkg)
                    putExtra("name", schedule.name)
                    putExtra("end", schedule.endMinutes)
                    putExtra("strict", schedule.strict)
                }
            )
            return
        }
        if (now - lastLaunch < 1_500L) return
        lastLaunch = now
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("pkg", pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sampler)
        super.onDestroy()
    }
}
