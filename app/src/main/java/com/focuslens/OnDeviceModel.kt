package com.focuslens

import java.util.Calendar
import kotlin.math.exp

/**
 * A small learning model that runs only on this phone. It is retrained from your own task
 * history in the local database each time the Coach opens: nothing is uploaded, and no model
 * file is stored that could leave the device.
 *
 * Model: logistic regression (gradient descent) predicting the chance a task is missed, from
 * when it is scheduled (time of day, weekend), its task type, and your overall miss rate.
 * It needs at least [MIN_SAMPLES] past check-ins before it replaces the simple rules.
 */
data class LearnedModel(val weights: DoubleArray, val samples: Int, val fitAccuracy: Double)

const val MIN_SAMPLES = 20
private const val EPOCHS = 300
private const val LEARNING_RATE = 0.3
private const val L2 = 0.01
private const val FEATURE_COUNT = 7

private fun features(task: CoachTask, overallMissRate: Double): DoubleArray {
    val cal = Calendar.getInstance()
    cal.timeInMillis = task.scheduledAt
    val weekend = cal.get(Calendar.DAY_OF_WEEK).let { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
    val bucket = coachBucketOf(task.scheduledAt)
    return doubleArrayOf(
        1.0,
        if (bucket == "morning") 1.0 else 0.0,
        if (bucket == "afternoon") 1.0 else 0.0,
        if (bucket == "evening") 1.0 else 0.0,
        if (weekend) 1.0 else 0.0,
        overallMissRate,
        if (task.type == "WELLNESS") 1.0 else 0.0
    )
}

private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))

private fun dot(w: DoubleArray, x: DoubleArray): Double {
    var s = 0.0
    for (i in w.indices) s += w[i] * x[i]
    return s
}

/** Fit on the past tasks that have a check-in. Returns null when there isn't enough history yet. */
fun trainOnDevice(tasks: List<CoachTask>, checkIns: List<CoachCheckIn>): LearnedModel? {
    val byId = tasks.associateBy { it.id }
    val labelled = checkIns.mapNotNull { c -> byId[c.taskId]?.let { it to (if (c.completed) 0.0 else 1.0) } }
    if (labelled.size < MIN_SAMPLES) return null

    val overall = labelled.count { it.second == 1.0 }.toDouble() / labelled.size
    val xs = labelled.map { features(it.first, overall) }
    val ys = labelled.map { it.second }

    val w = DoubleArray(FEATURE_COUNT)
    repeat(EPOCHS) {
        val grad = DoubleArray(FEATURE_COUNT)
        for (i in xs.indices) {
            val err = sigmoid(dot(w, xs[i])) - ys[i]
            for (j in w.indices) grad[j] += err * xs[i][j]
        }
        for (j in w.indices) {
            w[j] -= LEARNING_RATE * (grad[j] / xs.size + L2 * w[j])
        }
    }
    val correct = xs.indices.count { (sigmoid(dot(w, xs[it])) >= 0.5) == (ys[it] == 1.0) }
    return LearnedModel(w, labelled.size, correct.toDouble() / labelled.size)
}

/** Learned risk for one open task, with the same deadline adjustments as the rule-based version. */
fun learnedTaskRisk(task: CoachTask, model: LearnedModel, overallMissRate: Double, now: Long): TaskRisk {
    var p = sigmoid(dot(model.weights, features(task, overallMissRate)))
    val why = mutableListOf(
        "Learned from ${model.samples} of your past tasks: about ${Math.round(p * 100)}% chance of missing this.",
        "The model matched ${Math.round(model.fitAccuracy * 100)}% of your past tasks, so treat it as a guide."
    )
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
