package com.focuslens

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads Chrome's own address bar to get the domain of the page on screen, for the
 * social-media browser block. Only runs when that setting is turned on.
 *
 * Privacy: the extracted host (e.g. "instagram.com") is held in memory only for this one
 * comparison — never stored or sent anywhere. This depends on Chrome's current layout
 * (its address bar's element id), so a Chrome update could break it; when the address bar
 * can't be found, nothing is blocked rather than guessing.
 *
 * Scope: this covers Chrome only, and the domains below are a starting list, not an
 * exhaustive one — add more in Focus settings. youtube.com is deliberately left out here;
 * the Shorts rule only covers the native YouTube app, not the mobile site in Chrome.
 */
object BrowserDetector {
    const val CHROME = "com.android.chrome"

    private val SOCIAL_DOMAINS = setOf(
        "instagram.com", "facebook.com", "fb.com", "twitter.com", "x.com",
        "tiktok.com", "reddit.com", "snapchat.com", "pinterest.com", "threads.net"
    )

    // Always wins over the social list above, even if a domain somehow matched both.
    private val ACADEMIC_DOMAINS = setOf(
        "monash.edu", "google.com", "docs.google.com", "drive.google.com",
        "scholar.google.com", "classroom.google.com", "instructure.com", "moodle.com",
        "khanacademy.org", "coursera.org", "edx.org", "wikipedia.org", "jstor.org",
        "sciencedirect.com", "researchgate.net", "ieee.org", "springer.com",
        "wolframalpha.com", "desmos.com", "quizlet.com", "overleaf.com"
    )

    fun currentUrl(root: AccessibilityNodeInfo): String? {
        val bar = findUrlBar(root, 0) ?: return null
        return bar.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun findUrlBar(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 25) return null
        if (node.viewIdResourceName == "com.android.chrome:id/url_bar") return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findUrlBar(child, depth + 1)?.let { return it }
        }
        return null
    }

    fun hostOf(urlBarText: String): String {
        var s = urlBarText.trim().lowercase()
        s = s.removePrefix("https://").removePrefix("http://")
        s = s.substringBefore('/').substringBefore(' ')
        return s.removePrefix("www.")
    }

    fun isSocial(host: String): Boolean = SOCIAL_DOMAINS.any { host == it || host.endsWith(".$it") }

    fun isAcademic(host: String, extra: Set<String>): Boolean {
        if (host.endsWith(".edu")) return true
        return (ACADEMIC_DOMAINS + extra).any { host == it || host.endsWith(".$it") }
    }
}
