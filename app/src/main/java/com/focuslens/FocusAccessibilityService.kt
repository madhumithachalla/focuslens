package com.focuslens

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import kotlinx.coroutines.runBlocking
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
    private var streakStart = 0L
    private var lastReminderAt = 0L
    private var scrollStreakPkg: String? = null
    private var lastScrollAt = 0L
    private var lastSwipeCountAt = 0L

    companion object {
        private const val SCROLL_IDLE_GAP_MS = 120_000L

        /** True only while Android has this service connected (diagnostic, read by the Focus tab). */
        @Volatile var connected = false

        /** Last time the service's timer ran (epoch millis). A stale value means Android paused it. */
        @Volatile var lastHeartbeat = 0L

        /** The running service, so the Skip reel button can ask it to move on. */
        @Volatile var instance: FocusAccessibilityService? = null

        /** Filter with: adb logcat -s FocusLens */
        const val TAG = "FocusLens"
    }

    // Windows that should never change which app we think is open.
    private val ignored = setOf(
        "com.android.systemui", "android", "com.google.android.apps.wellbeing"
    )

    // Re-checks every 10 seconds so a 10 minute pass cannot last forever.
    private var ticks = 0

    private val ticker = object : Runnable {
        override fun run() {
            // Heartbeat: lets the Focus tab tell when the service has stopped running.
            lastHeartbeat = System.currentTimeMillis()
            current?.let { check(it) }
            // Every 30 minutes, save any finished days of usage history in the background, so no
            // day is missed when the app isn't opened for a while.
            if (++ticks % 180 == 0) saveHistoryInBackground()
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
        blockPkg = pkg
        val now = System.currentTimeMillis()
        val section = SectionDetector.detect(pkg, root, prefs)
        val delta = if (lastSampleT == 0L) 0L else minOf(now - lastSampleT, 8_000L)
        lastSampleT = now
        if (delta > 0) prefs.addSection(pkg, section, delta)
        prefs.setLastSeen(pkg, section, SectionDetector.lastScanCapped)
        if (pkg == SectionDetector.INSTAGRAM || pkg == SectionDetector.YOUTUBE) {
            android.util.Log.d(TAG, "$pkg section=$section capped=${SectionDetector.lastScanCapped} " +
                "feature=${SectionDetector.feedFeature[pkg]} blocked=${prefs.isSectionBlocked(pkg, SectionDetector.feedFeature[pkg] ?: "")} " +
                "connected=$connected")
        }
        // Count each visit to Reels (not every refresh), and enforce the daily Reels limit.
        if (pkg == SectionDetector.INSTAGRAM && section == "Reels" && lastReelsSection != "Reels") {
            prefs.addReelToday()
        }
        if (pkg == SectionDetector.INSTAGRAM) lastReelsSection = section
        // The Reels switch is checked first, by its own rule, so no other rule can skip it.
        if (pkg == SectionDetector.INSTAGRAM && checkReelsBlock(section)) return
        // Skip reel: shown while you are in the feed, stories or Reels, when turned on.
        // Shown only when the screen has reels on it (the reel symbol, banner or player), not everywhere.
        if (pkg == SectionDetector.INSTAGRAM && prefs.skipReelEnabled() &&
            section == "Reels" && android.provider.Settings.canDrawOverlays(this)
        ) {
            SkipReelOverlay.show(this) { skipReel() }
        } else if (pkg == SectionDetector.INSTAGRAM) {
            SkipReelOverlay.hide(this)
        }
        val reelCap = prefs.reelLimit()
        if (pkg == SectionDetector.INSTAGRAM && section == "Reels" && reelCap > 0 && prefs.reelsToday() > reelCap) {
            kickHome("reels limit")
            launchFeatureBlock("Reels limit reached", "You've opened Reels $reelCap times today, your limit. Back tomorrow after 6am.")
            return
        }

        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing ||
            SchedulePauseActivity.showing
        ) return
        if (now - lastLaunch < 3_000L) return

        // On a break, or locked for the day: sent out even if the app is already open.
        if (now - lastLaunch > 1_500L && (now < prefs.dayLockUntil(pkg) || now < prefs.breakUntil(pkg))) {
            lastLaunch = now
            kickHome("break or day lock while open")
            val appName = SectionDetector.displayName[pkg] ?: pkg
            if (now < prefs.dayLockUntil(pkg)) {
                launchFeatureBlock("$appName is locked", "You've used up today's breaks from $appName. It unlocks at 6am.")
            } else {
                launchFeatureBlock("On a break from $appName", "Back in ${Clock.untilHms(prefs.breakUntil(pkg), now)}.")
            }
            return
        }

        // Daily screen-time limit reached while using the app: locked until 6am, no challenge.
        if (prefs.enabled && prefs.isBlocked(pkg) && now >= prefs.allowedUntil(pkg) &&
            usedMinToday(pkg) >= prefs.limitMin(pkg)
        ) {
            lastLaunch = now
            prefs.bump(pkg, "blocked_attempts")
            kickHome("daily limit while open")
            startActivity(
                Intent(this, BlockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("pkg", pkg)
                    putExtra("usedMin", usedMinForBlock(pkg).toLong())
                }
            )
            return
        }

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
            val kind = YoutubeContent.classify(text)
            if (kind == YoutubeContent.Kind.ENTERTAINMENT || kind == YoutubeContent.Kind.UNRECOGNISED) {
                launchFeatureBlock(
                    "This video is off",
                    "Academic and podcast mode is on. This looks ${kind.label}. " +
                        "It is a keyword check on the title and channel text, so it can be wrong."
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
        android.util.Log.d(TAG, "block shown: $title")
        prefs.bump(blockPkg, "blocked_attempts")
        prefs.bump("all", "feature: $title")
        try {
            startActivity(
                Intent(this, FeatureBlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("title", title)
                    putExtra("message", message)
                    putExtra("pkg", blockPkg)
                }
            )
        } catch (e: Exception) {
            // Logged so a block that could not open is visible, not silently lost.
            android.util.Log.d(TAG, "block screen failed to open: ${e.message}")
        }
    }

    /** Moves Instagram to the next reel or post: scrolls the first scrollable area forward. */
    fun skipReel() {
        // In Reels: leave Reels altogether and go to the home feed, instead of one reel at a time.
        if (lastReelsSection == "Reels" && openHomeFeed()) return
        // A real swipe up through the middle of the screen, the same as the person would do.
        // It avoids the Skip button in the bottom-right corner.
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val path = android.graphics.Path().apply {
            moveTo(x, dm.heightPixels * 0.78f)
            lineTo(x, dm.heightPixels * 0.22f)
        }
        val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0L, 250L)
        val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
        val sent = dispatchGesture(gesture, null, null)
        if (!sent) {
            // Fall back to asking the scrollable area to scroll forward.
            rootInActiveWindow?.let { findScrollable(it, 0)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
        }
    }

    /** Taps Instagram's Home tab, if it is on screen. Returns true when it did. */
    private fun openHomeFeed(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = root.findAccessibilityNodeInfosByText("Home") ?: emptyList()
        val tab = candidates.firstOrNull { it.isClickable } ?: candidates.firstOrNull()
        val clickable = tab?.let { n ->
            var cur: AccessibilityNodeInfo? = n
            while (cur != null && !cur.isClickable) cur = cur.parent
            cur
        }
        return clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun findScrollable(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 25) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child, depth + 1)?.let { return it }
        }
        return null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
        android.util.Log.d(TAG, "service connected")
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sampler)
        handler.post(ticker)
        saveHistoryInBackground()
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
        // The Skip reel button only belongs to Instagram.
        if (pkg != SectionDetector.INSTAGRAM) SkipReelOverlay.hide(this)
        current = pkg
        check(pkg)
    }

    /** A nudge, not a block — see ScrollReminderActivity. Only tracked social apps (the ones
     *  actually built for an endless scroll) count; a long list in a shopping or news app
     *  doesn't trip this. */
    private fun trackScroll(pkg: String?) {
        if (pkg == null || pkg !in SectionDetector.tracked) return
        blockPkg = pkg
        // One "swipe" per burst of scrolling, not one per scroll event.
        val tNow = System.currentTimeMillis()
        // Quick swipes count too: a swipe is one burst of scroll events, about a second or less.
        if (tNow - lastSwipeCountAt > 900L) {
            prefs.bump(pkg, "swipes")
            lastSwipeCountAt = tNow
            // In Reels, each swipe moves to the next reel, so each one counts toward the limit.
            if (pkg == SectionDetector.INSTAGRAM && lastReelsSection == "Reels") {
                prefs.addReelToday()
                val cap = prefs.reelLimit()
                android.util.Log.d(TAG, "reel counted ${prefs.reelsToday()} of $cap")
                if (cap > 0 && prefs.reelsToday() > cap && System.currentTimeMillis() - lastLaunch > 1_500L) {
                    lastLaunch = System.currentTimeMillis()
                    kickHome("reels limit (swipe)")
                    launchFeatureBlock("Reels limit reached", "You've watched $cap Reels today, your limit. Back tomorrow after 6am.")
                }
            }
        }
        if (!prefs.doomScrollReminderEnabled()) return
        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing || ScrollReminderActivity.showing) return

        val now = System.currentTimeMillis()
        // A run of scrolling starts fresh after a couple of minutes without any.
        if (pkg != scrollStreakPkg || now - lastScrollAt > SCROLL_IDLE_GAP_MS) {
            scrollStreakPkg = pkg
            streakStart = now
            lastReminderAt = now
        }
        lastScrollAt = now

        val runMinutes = (now - streakStart) / 60_000L
        if (runMinutes >= prefs.scrollBreakAfterMinutes()) {
            startBreakFor(pkg, now)
            streakStart = now
            lastReminderAt = now
            return
        }
        val sinceReminderMinutes = (now - lastReminderAt) / 60_000L
        if (sinceReminderMinutes >= prefs.scrollReminderMinutes() && now - lastLaunch > 3_000L) {
            lastReminderAt = now
            lastLaunch = now
            prefs.bump(pkg, "scroll_reminders")
            val appName = SectionDetector.displayName[pkg] ?: pkg
            startActivity(
                Intent(this, ScrollReminderActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("label", appName)
                    putExtra("scrollMinutes", runMinutes.toInt())
                }
            )
        }
    }

    /** Scrolled too long: a break from the app, or a lock for the rest of the day after too many breaks. */
    private fun startBreakFor(pkg: String, now: Long) {
        val appName = SectionDetector.displayName[pkg] ?: pkg
        val limit = prefs.breaksPerDayLimit()
        prefs.addBreak(pkg)
        val taken = prefs.breaksToday(pkg)
        kickHome("scroll break")
        if (taken > limit) {
            prefs.setDayLockUntil(pkg, prefs.nextResetAt())
            launchFeatureBlock(
                "$appName locked for today",
                "You've taken $limit breaks from $appName today, so it's locked until 6am."
            )
        } else {
            val minutes = prefs.breakMinutes()
            prefs.startBreak(pkg, now + minutes * 60_000L)
            launchFeatureBlock(
                "Break from $appName",
                "You've scrolled $appName for ${prefs.scrollBreakAfterMinutes()} minutes. " +
                    "Take $minutes minutes away. Break $taken of $limit today."
            )
        }
    }

    /**
     * The Reels block, on its own: if the Reels switch is on and Instagram shows Reels,
     * send the person home with the reason. Every decision is logged as "reels check",
     * so a block that does not fire can be traced to the exact condition that failed.
     */
    private fun checkReelsBlock(section: String): Boolean {
        val now = System.currentTimeMillis()
        val switchOn = prefs.isSectionBlocked(SectionDetector.INSTAGRAM, "Reels")
        val busy = BlockActivity.showing || FeatureBlockedActivity.showing ||
            HyperFocusBlockActivity.showing || SchedulePauseActivity.showing
        val fire = switchOn && section == "Reels" && !busy && now - lastLaunch >= 1_500L
        android.util.Log.d(TAG, "reels check: section=$section switchOn=$switchOn busy=$busy fire=$fire")
        if (!fire) return false
        lastLaunch = now
        kickHome("reels block")
        launchFeatureBlock(
            "Reels is blocked",
            "You turned Reels off for Instagram in FocusLens. The rest of Instagram still works. " +
                "Turn it back on under Focus → Feature blocking."
        )
        return true
    }

    /** Sends the person home, and logs why, so the cause is visible in logcat. */
    private fun kickHome(why: String) {
        // No home-screen action any more: sending home first raced the block screen, and it
        // sometimes failed to appear. The block screen now covers the app on its own.
        android.util.Log.d(TAG, "block over $blockPkg: $why")
    }

    /** The app whose rule is being checked right now, so each block is counted against it. */
    private var blockPkg: String = "all"

    private fun defaultDialerPackage(): String? =
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
            ?.defaultDialerPackage

    private fun check(pkg: String) {
        blockPkg = pkg
        if (BlockActivity.showing || HyperFocusBlockActivity.showing || FeatureBlockedActivity.showing) return
        val now = System.currentTimeMillis()

        // On a break, or locked for the day after too many breaks.
        if (pkg in SectionDetector.tracked && now - lastLaunch > 1_500L) {
            val lockedUntil = prefs.dayLockUntil(pkg)
            val breakUntil = prefs.breakUntil(pkg)
            if (now < lockedUntil || now < breakUntil) {
                lastLaunch = now
                kickHome("break or day lock on open")
                val appName = SectionDetector.displayName[pkg] ?: pkg
                if (now < lockedUntil) {
                    launchFeatureBlock("$appName is locked", "You've used up today's breaks from $appName. It unlocks at 6am.")
                } else {
                    launchFeatureBlock("On a break from $appName", "Back in ${Clock.untilHms(prefs.breakUntil(pkg), now)}.")
                }
                return
            }
        }

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
        prefs.bump(pkg, "blocked_attempts")
        // Screen time used up: say why, send you to the home screen, then show the lock.
        val usedToday = usedMinToday(pkg)
        val limitToday = prefs.limitMin(pkg)
        if (usedToday >= limitToday) {
            val appName = SectionDetector.displayName[pkg] ?: pkg
            Toast.makeText(
                this,
                "$appName daily limit reached: $usedToday of $limitToday min used today. It unlocks at 6am.",
                Toast.LENGTH_LONG
            ).show()
            kickHome("daily limit (toast)")
        }
        val intent = Intent(this, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra("pkg", pkg)
            putExtra("usedMin", usedMinForBlock(pkg).toLong())
        }
        startActivity(intent)
    }

    private var lastReelsSection = ""
    private val usageCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Int>>()

    /** Minutes used today on [pkg] since the 6am limit day, cached for a minute. */
    private fun saveHistoryInBackground() {
        Thread {
            try {
                runBlocking {
                    val history = UsageHistoryRepository(this@FocusAccessibilityService, UsageRepository(this@FocusAccessibilityService))
                    history.catchUp()
                    history.catchUpHourly()
                }
            } catch (e: Exception) {
                // Saving history is best-effort; the next attempt tries again.
            }
        }.start()
    }

    /** Minutes used today, read from a background cache. Never queries on the service thread,
     *  which is what made Instagram hang while the limit was being checked. */
    private fun usedMinToday(pkg: String): Int {
        val now = System.currentTimeMillis()
        val cached = usageCache[pkg]
        if (cached == null || now - cached.first > 30_000L) refreshUsage(pkg)
        return cached?.second ?: 0
    }

    /** Minutes used today for the block screen: waits briefly for a fresh figure, so it is not 0. */
    private fun usedMinForBlock(pkg: String): Int {
        val t = refreshUsage(pkg)
        t.join(700L)
        return usageCache[pkg]?.second ?: 0
    }

    private fun refreshUsage(pkg: String): Thread {
        val now = System.currentTimeMillis()
        return Thread {
            val ms = try {
                UsageRepository(this).loadCustom(FocusPrefs.limitDayStartMs(now), now)
                    .firstOrNull { it.packageName == pkg }?.millis ?: 0L
            } catch (e: Exception) {
                0L
            }
            usageCache[pkg] = System.currentTimeMillis() to (ms / 60_000L).toInt()
        }.also { it.start() }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connected = false
        instance = null
        SkipReelOverlay.hide(this)
        android.util.Log.d(TAG, "service unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connected = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sampler)
        super.onDestroy()
    }
}
