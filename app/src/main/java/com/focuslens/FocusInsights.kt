package com.focuslens

/**
 * Today's focus signals, read on this phone only.
 * Minutes are today since midnight. Counts for "chose not to open" are all-time.
 */
data class FocusSignals(
    val socialMinToday: Long,
    val socialMinYesterday: Long,
    val reelsToday: Int,
    val reelMinToday: Long,
    val reelLimit: Int,
    val notOpenedAll: Long,
    val resistPasses: Int,
    val resistGaveUp: Int
)

/** One part of the score, from 0 (poor) to 1 (good), with the number behind it. */
data class FocusPart(val name: String, val score: Double, val detail: String, val tag: String)

data class FocusReport(val score: Int, val parts: List<FocusPart>, val tips: List<Tip>)

/** A short, specific suggestion. [tags] say which weak part it fixes. */
data class Tip(val text: String, val tags: Set<String>)

object FocusInsights {

    /** A small library of tips. Each one is matched to a weak part of the score. */
    val tips: List<Tip> = listOf(
        Tip("Turn on Pomodoro. Social apps pause during each 25-minute focus.", setOf("social")),
        Tip("Set a daily limit on Instagram. Once you hit it, it locks for 12 hours.", setOf("social")),
        Tip("Put the social apps you open most on the Focus tab's block list.", setOf("social")),
        Tip("Pick a reel limit. Each reel counts as you swipe, so you can see the number.", setOf("reels")),
        Tip("Leave Reels alone for the first hour after you wake up. Reels can wait.", setOf("reels")),
        Tip("When the interception shows, try the breathing round before you decide to open.", setOf("resist")),
        Tip("Use 'Not now' when you don't need the app. Each one is counted here.", setOf("resist")),
        Tip("Start a study room, so you aren't alone with the feed.", setOf("social", "resist")),
        Tip("Plan one focus block tomorrow and keep the phone in another room for it.", setOf("social"))
    )

    /** Score = weighted parts. Social time 40%, reels 25%, resisting 35%. */
    fun build(s: FocusSignals): FocusReport {
        val socialMin = s.socialMinToday
        val socialPart = when {
            socialMin <= 60 -> 1.0
            socialMin >= 300 -> 0.0
            else -> (300 - socialMin) / 240.0
        }
        val reelsPart = (1.0 - s.reelsToday / 20.0).coerceIn(0.0, 1.0)
        val resistTotal = s.resistPasses + s.resistGaveUp
        val resistPart = if (resistTotal == 0) 0.5 else s.resistGaveUp.toDouble() / resistTotal

        val parts = listOf(
            FocusPart(
                "Social media",
                socialPart,
                "${fmtMin(socialMin)} today" + if (s.socialMinYesterday > 0)
                    " · yesterday ${fmtMin(s.socialMinYesterday)}" else "",
                "social"
            ),
            FocusPart(
                "Reels",
                reelsPart,
                "${s.reelsToday} reels · ${fmtMin(s.reelMinToday)} watched" +
                    if (s.reelLimit > 0) " · limit ${s.reelLimit}" else "",
                "reels"
            ),
            FocusPart(
                "Resisting",
                resistPart,
                if (resistTotal == 0) "No choices yet" else "You stepped back ${s.resistGaveUp} of $resistTotal times",
                "resist"
            )
        )
        val weights = listOf(0.40, 0.25, 0.35)
        val score = parts.zip(weights).sumOf { (p, w) -> p.score * w } * 100
        val weak = parts.filter { it.score < 0.6 }.map { it.tag }.toSet()
        return FocusReport(score.toInt().coerceIn(0, 100), parts, retrieve(weak))
    }

    /** Matches tips to the weak parts. Each tip is shown once, and at most three in total. */
    fun retrieve(weak: Set<String>): List<Tip> {
        if (weak.isEmpty()) return emptyList()
        return tips.filter { t -> t.tags.any { it in weak } }
            .sortedByDescending { t -> t.tags.count { it in weak } }
            .take(3)
    }

    private fun fmtMin(minutes: Long): String =
        if (minutes < 60) "${minutes} min" else "${minutes / 60} h ${"%02d".format(minutes % 60)} min"
}
