package com.focuslens

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Works out which part of a social app is on screen (for example Reels or Feed in
 * Instagram, Shorts in YouTube) by looking at the app's own screen element names and
 * which bottom tab is selected.
 *
 * Privacy: it never keeps text from the screen. It only keeps the app's internal element
 * names (like "clips_tab") and a short fixed list of tab words (like "reels" or "home").
 *
 * This depends on how Instagram and YouTube build their screens, so an app update can
 * break it. When it cannot tell, it reports "Other" and saves the element names it saw
 * (see FocusPrefs.debugIds) so the matching rules can be corrected.
 */
object SectionDetector {

    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    val tracked = setOf(INSTAGRAM, YOUTUBE)

    // Longest first so "search and explore" wins over "search".
    private val tabWords = listOf(
        "search and explore", "subscriptions", "notifications", "messages", "profile",
        "explore", "library", "direct", "create", "search", "shorts", "reels", "home", "you"
    )

    fun detect(pkg: String, root: AccessibilityNodeInfo, prefs: FocusPrefs): String {
        val ids = HashSet<String>()
        val selectedIds = ArrayList<String>()
        val words = ArrayList<String>()

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < 700) {
            val (node, depth) = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName?.substringAfter(":id/")?.lowercase() ?: ""
            if (id.isNotEmpty()) ids.add(id)
            if (node.isSelected) {
                if (id.isNotEmpty()) selectedIds.add(id)
                val d = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                if (d.isNotEmpty() && d.length <= 40) {
                    // Match "reels" or "reels, tab 2 of 5", but not words that merely start the same.
                    tabWords.firstOrNull {
                        d == it || d.startsWith("$it,") || d.startsWith("$it ") || d.startsWith("$it:")
                    }?.let { words.add(it) }
                }
            }
            if (depth < 25) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it to depth + 1) }
                }
            }
        }

        val result = when (pkg) {
            INSTAGRAM -> instagram(ids, selectedIds, words)
            YOUTUBE -> youtube(ids, words)
            else -> "Other"
        }

        // Keep a small sample of element names for tuning the rules later.
        selectedIds.forEach { prefs.addDebugId(pkg, "selected:$it") }
        if (result == "Other") ids.take(40).forEach { prefs.addDebugId(pkg, it) }
        return result
    }

    private fun instagram(ids: Set<String>, selIds: List<String>, words: List<String>): String {
        fun has(vararg k: String) = ids.any { id -> k.any { id.contains(it) } }
        fun sel(vararg k: String) = selIds.any { id -> k.any { id.contains(it) } }
        return when {
            sel("clips_tab") || "reels" in words || has("clips_viewer", "clips_video", "clips_swipe") -> "Reels"
            has("reel_viewer", "story_viewer", "stories_viewer") -> "Stories"
            sel("direct_tab") || "messages" in words || "direct" in words ||
                has("direct_thread", "direct_inbox", "row_thread") -> "Messages"
            sel("feed_tab") || "home" in words -> "Feed"
            sel("search_tab") || "search" in words || "search and explore" in words || "explore" in words ->
                "Explore and search"
            sel("profile_tab") || "profile" in words -> "Profile"
            sel("creation_tab") || "create" in words -> "Creating a post"
            else -> "Other"
        }
    }

    private fun youtube(ids: Set<String>, words: List<String>): String {
        fun has(vararg k: String) = ids.any { id -> k.any { id.contains(it) } }
        return when {
            has("reel_player", "reel_recycler", "shorts_") || "shorts" in words -> "Shorts"
            has("watch_while", "watch_player", "player_view") -> "Watching a video"
            "subscriptions" in words -> "Subscriptions"
            "home" in words -> "Home feed"
            "you" in words || "library" in words -> "Library"
            else -> "Other"
        }
    }
}
