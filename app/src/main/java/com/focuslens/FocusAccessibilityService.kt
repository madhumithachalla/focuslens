package com.focuslens

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the front. It only reads the app's package name, never
 * what is on the screen. When a blocked app opens, it shows the BlockActivity.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val prefs by lazy { FocusPrefs(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastLaunch = 0L

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

    // Every 4 seconds, while Instagram or YouTube is in front and the screen is on, note
    // which section is showing and add the elapsed time to it.
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
        if (pkg == null || pkg !in SectionDetector.tracked) {
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
        val now = System.currentTimeMillis()
        val section = SectionDetector.detect(pkg, root, prefs)
        val delta = if (lastSampleT == 0L) 0L else minOf(now - lastSampleT, 8_000L)
        lastSampleT = now
        if (delta > 0) prefs.addSection(pkg, section, delta)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sampler)
        handler.post(ticker)
        handler.post(sampler)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
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

    private fun defaultDialerPackage(): String? =
        (getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
            ?.defaultDialerPackage

    private fun check(pkg: String) {
        if (BlockActivity.showing || HyperFocusBlockActivity.showing) return
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
