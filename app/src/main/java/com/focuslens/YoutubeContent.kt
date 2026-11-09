package com.focuslens

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Best-effort "does this look academic or like a podcast" check for YouTube's
 * academic/podcast-only mode. There is no real content classification here — it is
 * keyword matching against whatever text is visible on screen (title, channel name,
 * description snippet). It will miss real academic videos that don't use these words,
 * and let through anything that happens to use one. Only ever active when the user turns
 * the setting on.
 *
 * Privacy: this is a deliberate exception to the rest of the app's "never reads screen
 * text" rule, and only for this one opt-in feature — the on-screen text is read into
 * memory for a single keyword check and discarded immediately, never stored, logged, or
 * sent anywhere.
 */
object YoutubeContent {

    private val KEYWORDS = listOf(
        "lecture", "tutorial", "course", "university", "college", " lab ", "assignment",
        "seminar", "exam", "textbook", "professor", "academic", "research", "thesis",
        "podcast", "interview", "documentary", "how to", "crash course", "masterclass",
        "workshop", "ted-ed", "khan academy", "lesson"
    )

    /** What a video looks like from its title, channel and on-screen text. */
    enum class Kind(val label: String) {
        ACADEMIC("academic"),
        PODCAST("podcast"),
        ENTERTAINMENT("entertainment"),
        UNRECOGNISED("not recognised as academic or a podcast")
    }

    private val PODCAST_WORDS = listOf("podcast", "episode", "interview", "talk show", "conversation with")
    private val ENTERTAINMENT_WORDS = listOf(
        "official video", "music video", "lyrics", "trailer", "vlog", "prank", "reaction",
        "gameplay", "funny", "compilation", "challenge", "meme", "#shorts", "full movie"
    )

    /** Sorts the visible text into a kind. Academic words win over entertainment words. */
    fun classify(visibleText: String): Kind {
        val t = " ${visibleText.lowercase()} "
        return when {
            KEYWORDS.any { t.contains(it) } && PODCAST_WORDS.none { t.contains(it) } -> Kind.ACADEMIC
            PODCAST_WORDS.any { t.contains(it) } -> Kind.PODCAST
            ENTERTAINMENT_WORDS.any { t.contains(it) } -> Kind.ENTERTAINMENT
            else -> Kind.UNRECOGNISED
        }
    }

    fun looksAcademic(visibleText: String): Boolean {
        val k = classify(visibleText)
        return k == Kind.ACADEMIC || k == Kind.PODCAST
    }

    fun visibleText(root: AccessibilityNodeInfo, maxChars: Int = 500): String {
        val sb = StringBuilder()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400 && sb.length < maxChars) {
            val (node, depth) = queue.removeFirst()
            visited++
            val t = node.text?.toString()?.trim()
            if (!t.isNullOrEmpty() && t.length <= 150) sb.append(t).append(' ')
            if (depth < 20) {
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it to depth + 1) }
            }
        }
        return sb.toString()
    }
}
