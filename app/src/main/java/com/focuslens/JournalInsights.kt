package com.focuslens

import android.content.Context

/**
 * Journal prompts generated from the device's own real usage data — not a fixed list, and
 * not AI-written. Two plain, inspectable rules: what app actually took the most time this
 * week, and what hour of day the phone actually gets picked up most. Both numbers are the
 * same ones already shown on the Focus and Usage tabs; this just turns them into a prompt
 * to write about, instead of leaving the data sitting unused on another screen. Never reads
 * what's inside any app — same screen-name-only boundary the rest of the usage features keep.
 */
suspend fun usageAwareJournalPrompts(context: Context): List<Pair<String, String>> {
    val repo = UsageRepository(context)
    if (!repo.hasPermission()) return emptyList()
    val history = UsageHistoryRepository(context, repo)

    val prompts = mutableListOf<Pair<String, String>>()

    val apps = repo.load(Range.WEEK).filter {
        val pkg = it.packageName
        !(pkg.contains("launcher") || pkg.contains("systemui") || pkg == "android" ||
            pkg.contains("wellbeing") || pkg.contains("inputmethod") || pkg == context.packageName)
    }
    val top = apps.maxByOrNull { it.millis }
    if (top != null && top.millis > 0) {
        val duration = formatDuration(top.millis)
        val prompt = if (top.packageName in SectionDetector.tracked) {
            "You've spent about $duration on ${top.label} this week. What's it actually giving you right now — and is there something else that'd give you the same thing?"
        } else {
            "${top.label} took up the most time on your phone this week (about $duration). Worth a minute on what that time actually went toward?"
        }
        prompts.add("📱" to prompt)
    }

    val hours = history.hourlyTotalsRecent(7)
    val peakHour = hours.entries.maxByOrNull { it.value }
    if (peakHour != null && peakHour.value > 0) {
        prompts.add("🕒" to "You tend to be on your phone most around ${formatHourLabel(peakHour.key)}. What's usually going on for you around then?")
    }

    val lastCheckIn = com.focuslens.data.AppDatabase.get(context).weeklyCheckInDao().latest()
    if (lastCheckIn != null) {
        when {
            lastCheckIn.stressScore >= 4 -> prompts.add(
                "😮‍💨" to "Your last check-in rated stress ${lastCheckIn.stressScore}/5" +
                    (lastCheckIn.topTimeSink?.let { ", mostly around \"$it\"" } ?: "") +
                    ". What's actually in your control here, and what isn't?"
            )
            !lastCheckIn.topTimeSink.isNullOrBlank() -> prompts.add(
                "🔍" to "You said \"${lastCheckIn.topTimeSink}\" has been taking up your time/energy lately. How do you actually feel about that, underneath it?"
            )
        }
    }

    return prompts
}
