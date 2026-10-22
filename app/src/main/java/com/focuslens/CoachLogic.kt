package com.focuslens

import java.util.Calendar

// Explainable "Coach": transparent rules + simple statistics on the user's own logged data.
// Pure Kotlin (no Android types) so it can be unit tested. Port of docs/coachlogic.js for the
// data this app stores: tasks, task check-ins and journal moods. Focus-session and sleep
// insights from the web version are intentionally omitted because the Android app does not
// store that data. Every output carries a human-readable `why`.

private const val MIN_MS = 60_000L
private const val HOUR_MS = 3_600_000L

val COACH_MOOD_SCORE: Map<String, Int> = mapOf("low" to 1, "flat" to 2, "okay" to 3, "good" to 4, "great" to 5)

data class CoachTask(
    val id: Long,
    val title: String,
    val type: String,
    val scheduledAt: Long,
    val completedAt: Long?
)

data class CoachCheckIn(val taskId: Long, val completed: Boolean, val reason: String?)

/** [date] is yyyyMMdd, [mood] one of low/flat/okay/good/great (or null). */
data class CoachMoodEntry(val date: String, val mood: String?)

data class MissProfile(
    val total: Int,
    val missed: Int,
    val overall: Double,
    val bucket: Map<String, Double>,
    val topReason: String?
)

enum class RiskLevel { HIGH, MEDIUM, LOW }

data class TaskRisk(val risk: Int, val level: RiskLevel, val why: List<String>)

data class RiskedTask(val task: CoachTask, val risk: TaskRisk)

data class MoodForecast(
    val enough: Boolean,
    val points: Int,
    val slope: Double,
    val direction: String,
    val average: Double,
    val recent: Double,
    val next: List<Double>,
    val why: String
)

data class PlanBlock(
    val kind: String, // "focus" | "break"
    val start: Long,
    val end: Long,
    val title: String,
    val taskId: Long?,
    val why: String
)

data class DayPlan(val blocks: List<PlanBlock>, val overflow: List<RiskedTask>)

data class CoachInsight(val tone: String, val head: String, val why: String)

fun coachBucketOf(ms: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = ms
    val h = cal.get(Calendar.HOUR_OF_DAY)
    return if (h < 12) "morning" else if (h < 17) "afternoon" else "evening"
}

/** Smoothed miss rates (K = 5 pseudo-observations at a 0.3 prior): overall, per time-of-day, top reason. */
fun missProfile(tasks: List<CoachTask>, checkIns: List<CoachCheckIn>): MissProfile {
    val byId = tasks.associateBy { it.id }
    val missedBy = mutableMapOf("morning" to 0, "afternoon" to 0, "evening" to 0)
    val totalBy = mutableMapOf("morning" to 0, "afternoon" to 0, "evening" to 0)
    val reasons = mutableMapOf<String, Int>()
    var missed = 0
    var total = 0
    for (c in checkIns) {
        val t = byId[c.taskId] ?: continue
        total++
        val b = coachBucketOf(t.scheduledAt)
        totalBy[b] = (totalBy[b] ?: 0) + 1
        if (!c.completed) {
            missed++
            missedBy[b] = (missedBy[b] ?: 0) + 1
            val r = c.reason
            if (r != null) reasons[r] = (reasons[r] ?: 0) + 1
        }
    }
    val prior = 0.3
    val k = 5.0
    fun smooth(m: Int, n: Int, base: Double): Double = (m + k * base) / (n + k)
    val overall = smooth(missed, total, prior)
    val bucket = listOf("morning", "afternoon", "evening").associateWith {
        smooth(missedBy[it] ?: 0, totalBy[it] ?: 0, overall)
    }
    val topReason = reasons.entries.maxByOrNull { it.value }?.key
    return MissProfile(total, missed, overall, bucket, topReason)
}

/** Risk 0-100 that an open task is missed, with the reasons behind the number. */
fun taskRisk(task: CoachTask, profile: MissProfile, now: Long): TaskRisk {
    val why = mutableListOf<String>()
    val b = coachBucketOf(task.scheduledAt)
    var p = profile.bucket[b] ?: profile.overall
    if (profile.total >= 5) {
        why.add("You've missed about ${Math.round(p * 100)}% of $b tasks.")
    } else {
        why.add("Not enough history yet; using a general baseline.")
    }
    val hoursLeft = (task.scheduledAt - now).toDouble() / HOUR_MS
    if (hoursLeft < 0) {
        p = minOf(0.98, p + 0.25)
        why.add("It's already overdue.")
    } else if (hoursLeft < 2) {
        p = minOf(0.98, p + 0.1)
        why.add("It's due within 2 hours.")
    }
    val risk = Math.round(p * 100).toInt()
    val level = if (risk >= 60) RiskLevel.HIGH else if (risk >= 35) RiskLevel.MEDIUM else RiskLevel.LOW
    return TaskRisk(risk, level, why)
}

private fun round1(v: Double): Double = Math.round(v * 10) / 10.0
private fun round2(v: Double): Double = Math.round(v * 100) / 100.0

/** Least-squares slope over the last 14 daily mood scores; projects the next 3 days. */
fun moodForecast(entries: List<CoachMoodEntry>): MoodForecast {
    val byDate = sortedMapOf<String, Int>()
    for (e in entries) {
        val score = e.mood?.let { COACH_MOOD_SCORE[it] }
        if (score != null) byDate[e.date] = score
    }
    val ys = byDate.values.toList().takeLast(14).map { it.toDouble() }
    val n = ys.size
    if (n < 4) {
        return MoodForecast(false, n, 0.0, "steady", 0.0, 0.0, emptyList(), "Log at least 4 journal moods to see a trend.")
    }
    val xs = ys.indices.map { it.toDouble() }
    val mx = xs.sum() / n
    val my = ys.sum() / n
    var num = 0.0
    var den = 0.0
    for (i in 0 until n) {
        num += (xs[i] - mx) * (ys[i] - my)
        den += (xs[i] - mx) * (xs[i] - mx)
    }
    val slope = num / den
    fun clamp(v: Double): Double = maxOf(1.0, minOf(5.0, v))
    val next = (1..3).map { round1(clamp(my + slope * (n - 1 + it - mx))) }
    val recent = ys.takeLast(3).sum() / minOf(3, n)
    val direction = if (slope > 0.1) "improving" else if (slope < -0.1) "declining" else "steady"
    val s2 = round2(slope)
    val sign = if (slope >= 0) "+" else ""
    return MoodForecast(
        enough = true, points = n, slope = s2, direction = direction,
        average = round1(my), recent = round1(recent), next = next,
        why = "Across $n logged days your mood is $direction ($sign$s2 per day)."
    )
}

private fun startOfDay(now: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = now
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** Open tasks due today or overdue, each with its risk, highest risk first then earliest due. */
fun openTasksByRisk(tasks: List<CoachTask>, checkIns: List<CoachCheckIn>, now: Long): List<RiskedTask> {
    val profile = missProfile(tasks, checkIns)
    val endOfDay = startOfDay(now) + 24 * HOUR_MS
    return tasks
        .filter { it.completedAt == null && it.scheduledAt < endOfDay }
        .map { RiskedTask(it, taskRisk(it, profile, now)) }
        .sortedWith(compareByDescending<RiskedTask> { it.risk.risk }.thenBy { it.task.scheduledAt })
}

/**
 * Today's timeline: 25-minute focus blocks with 5-minute breaks (15 after every 4th), starting
 * at the next quarter hour (not before 07:00), nothing placed after 21:00. Blocks that don't
 * fit go to `overflow`.
 */
fun planDay(tasks: List<CoachTask>, checkIns: List<CoachCheckIn>, now: Long): DayPlan {
    val items = openTasksByRisk(tasks, checkIns, now)
    val dayStart = startOfDay(now)
    val hardStop = dayStart + 21 * HOUR_MS
    val quarter = 15 * MIN_MS
    var cursor = ((now + quarter - 1) / quarter) * quarter
    val sevenAm = dayStart + 7 * HOUR_MS
    if (cursor < sevenAm) cursor = sevenAm

    val blocks = mutableListOf<PlanBlock>()
    val overflow = mutableListOf<RiskedTask>()
    var done = 0
    for (it in items) {
        val end = cursor + 25 * MIN_MS
        if (end > hardStop) {
            overflow.add(it)
            continue
        }
        blocks.add(PlanBlock("focus", cursor, end, it.task.title, it.task.id, it.risk.why.first()))
        done++
        val longBreak = done % 4 == 0
        cursor = end + (if (longBreak) 15 else 5) * MIN_MS
        blocks.add(
            PlanBlock(
                "break", end, cursor, if (longBreak) "Longer break" else "Short break", null,
                "Rest between focus blocks."
            )
        )
    }
    if (blocks.isNotEmpty() && blocks.last().kind == "break") blocks.removeAt(blocks.size - 1)
    return DayPlan(blocks, overflow)
}

/** Plain-language findings, strongest first, each with the evidence for it. */
fun coachInsights(tasks: List<CoachTask>, checkIns: List<CoachCheckIn>, moods: List<CoachMoodEntry>): List<CoachInsight> {
    val out = mutableListOf<CoachInsight>()
    val mood = moodForecast(moods)
    if (mood.enough && mood.direction == "declining" && mood.recent < 3) {
        out.add(
            CoachInsight(
                "care", "Your mood has been trending down",
                "${mood.why} Forecast for the next 3 days: ${mood.next.joinToString(", ")} out of 5. " +
                    "Consider the calm-down exercises or talking to someone you trust."
            )
        )
    } else if (mood.enough && mood.direction == "improving") {
        out.add(CoachInsight("good", "Your mood is trending up", mood.why))
    }
    val miss = missProfile(tasks, checkIns)
    if (miss.total >= 5) {
        val worst = miss.bucket.entries.maxByOrNull { it.value }
        if (worst != null && worst.value > miss.overall + 0.1) {
            val name = worst.key.replaceFirstChar { c -> c.uppercase() }
            out.add(
                CoachInsight(
                    "warn", "$name tasks slip most often",
                    "About ${Math.round(worst.value * 100)}% missed vs ${Math.round(miss.overall * 100)}% overall. " +
                        "Move important tasks to another part of the day."
                )
            )
        }
        val top = miss.topReason
        if (top != null) {
            out.add(
                CoachInsight(
                    "info", "Top reason for missed tasks: ${top.lowercase().replace("_", " ")}",
                    "From ${miss.missed} missed check-ins."
                )
            )
        }
    }
    if (out.isEmpty()) {
        out.add(
            CoachInsight(
                "info", "Not enough data yet",
                "Check tasks off or mark them missed, and log journal moods. The coach learns from what you log."
            )
        )
    }
    return out
}
