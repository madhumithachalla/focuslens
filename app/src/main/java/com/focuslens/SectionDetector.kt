package com.focuslens

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Works out which part of a social app is on screen (for example Reels or Feed in
 * Instagram, Shorts in YouTube, For You in TikTok) by looking at the app's own screen
 * element names and which bottom/top tab is selected.
 *
 * Privacy: it never keeps text from the screen. It only keeps the app's internal element
 * names (like "clips_tab") and a short fixed list of tab words (like "reels" or "home").
 *
 * Instagram and YouTube's detection also checks internal view-ID patterns, found by
 * sampling real screens; the other apps below rely on tab content-description words alone
 * (the words Android accessibility services see on nav tabs), since that's the one signal
 * that reliably survives an app update where internal IDs get renamed or obfuscated — less
 * precise, but less brittle. Any of this can break when an app changes its screens; when it
 * can't tell, it reports "Other" and saves the element names it saw (see FocusPrefs.debugIds)
 * so the matching rules can be corrected.
 */
object SectionDetector {

    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val SNAPCHAT = "com.snapchat.android"
    const val FACEBOOK = "com.facebook.katana"
    const val TWITTER = "com.twitter.android"
    const val REDDIT = "com.reddit.frontpage"
    const val PINTEREST = "com.pinterest"
    const val THREADS = "com.instagram.barcelona"

    val tracked = setOf(INSTAGRAM, YOUTUBE, TIKTOK, SNAPCHAT, FACEBOOK, TWITTER, REDDIT, PINTEREST, THREADS)

    val displayName: Map<String, String> = mapOf(
        INSTAGRAM to "Instagram", YOUTUBE to "YouTube", TIKTOK to "TikTok", SNAPCHAT to "Snapchat",
        FACEBOOK to "Facebook", TWITTER to "X (Twitter)", REDDIT to "Reddit", PINTEREST to "Pinterest",
        THREADS to "Threads"
    )

    /** The one endless-scroll feed worth a dedicated "block just this" toggle, per app —
     *  not every tracked app has an obvious single one (Pinterest's whole point is browsing;
     *  Reddit's front page isn't a single named tab the same way), so those are left out. */
    val feedFeature: Map<String, String> = mapOf(
        INSTAGRAM to "Reels", YOUTUBE to "Shorts", TIKTOK to "For You", SNAPCHAT to "Spotlight",
        FACEBOOK to "Reels", TWITTER to "For You", THREADS to "For You"
    )

    // Longest first so "search and explore" wins over "search".
    private val tabWords = listOf(
        "search and explore", "subscriptions", "notifications", "messages", "profile",
        "explore", "library", "direct", "create", "search", "shorts", "reels", "home", "you",
        "for you", "following", "friends", "chat", "spotlight", "stories", "map", "watch",
        "marketplace", "groups", "popular", "today", "updates", "saved", "activity", "discover",
        "inbox", "camera", "lenses"
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
            TIKTOK -> tiktok(words)
            SNAPCHAT -> snapchat(words)
            FACEBOOK -> facebook(ids, words)
            TWITTER -> twitter(words)
            REDDIT -> reddit(words)
            PINTEREST -> pinterest(words)
            THREADS -> threads(words)
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
            // Checked first and on view-ID evidence alone: Instagram's Stories viewer uses ids
            // literally named "reel_viewer" internally (Stories predates, and still shares code
            // with, the separate Reels short-video feature) — that id previously lost to the
            // "reels" in words check below whenever some unrelated selected element elsewhere on
            // a Stories screen happened to carry "reels" as its content description, which is
            // what made blocking Reels also block Stories. ID evidence here is the stronger,
            // narrower signal, so it now wins regardless of what order the tab words came in.
            has("reel_viewer", "story_viewer", "stories_viewer") -> "Stories"
            sel("clips_tab") || has("clips_viewer", "clips_video", "clips_swipe") || "reels" in words -> "Reels"
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

    // The apps below are tab-word-only (no view-ID sampling behind them yet) — less precise
    // than Instagram/YouTube's detection, best-effort like the rest of this file.

    private fun tiktok(words: List<String>): String = when {
        "for you" in words -> "For You"
        "following" in words -> "Following"
        "friends" in words -> "Friends"
        "inbox" in words || "messages" in words -> "Inbox"
        "profile" in words || "you" in words -> "Profile"
        "create" in words -> "Creating a post"
        else -> "Other"
    }

    private fun snapchat(words: List<String>): String = when {
        "spotlight" in words -> "Spotlight"
        "stories" in words || "discover" in words -> "Stories"
        "chat" in words || "inbox" in words -> "Chat"
        "map" in words -> "Map"
        "camera" in words || "lenses" in words -> "Camera"
        "profile" in words -> "Profile"
        else -> "Other"
    }

    private fun facebook(ids: Set<String>, words: List<String>): String {
        fun has(vararg k: String) = ids.any { id -> k.any { id.contains(it) } }
        return when {
            "reels" in words || has("reels_tab", "reels_player") -> "Reels"
            "watch" in words -> "Watch"
            "marketplace" in words -> "Marketplace"
            "groups" in words -> "Groups"
            "notifications" in words -> "Notifications"
            "home" in words -> "Home feed"
            "friends" in words -> "Friends"
            else -> "Other"
        }
    }

    private fun twitter(words: List<String>): String = when {
        "for you" in words -> "For You"
        "following" in words -> "Following"
        "explore" in words || "search" in words -> "Explore and search"
        "notifications" in words -> "Notifications"
        "messages" in words || "direct" in words -> "Messages"
        "home" in words -> "Home feed"
        else -> "Other"
    }

    private fun reddit(words: List<String>): String = when {
        "popular" in words || "explore" in words -> "Popular"
        "chat" in words || "inbox" in words -> "Chat"
        "create" in words -> "Creating a post"
        "profile" in words -> "Profile"
        "home" in words -> "Home feed"
        else -> "Other"
    }

    private fun pinterest(words: List<String>): String = when {
        "today" in words -> "Today"
        "search" in words -> "Search"
        "create" in words -> "Creating a pin"
        "updates" in words || "notifications" in words -> "Updates"
        "saved" in words || "profile" in words -> "Saved"
        else -> "Other"
    }

    private fun threads(words: List<String>): String = when {
        "for you" in words -> "For You"
        "following" in words -> "Following"
        "search" in words -> "Search"
        "activity" in words || "notifications" in words -> "Activity"
        "profile" in words -> "Profile"
        else -> "Other"
    }
}
