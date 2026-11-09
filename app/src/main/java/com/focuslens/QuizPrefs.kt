package com.focuslens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Settings and daily counters for the quiz "intercept", kept on the phone only. The pause delay
 * and the daily emergency passes are the same ones BlockSchedulePrefs already holds, so there is
 * one source of truth (this class just reads and writes through to it).
 */
class QuizPrefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("quiz", Context.MODE_PRIVATE)
    private val schedules = BlockSchedulePrefs(context)

    // ---- Categories (none enabled = quiz gate off) ----

    fun enabledCategories(): Set<String> {
        val stored = sp.getStringSet("categories", null) ?: return QUIZ_CATEGORIES.map { it.id }.toSet()
        return stored.toSet()
    }

    fun setCategoryEnabled(id: String, on: Boolean) {
        val s = enabledCategories().toMutableSet()
        if (on) s.add(id) else s.remove(id)
        sp.edit().putStringSet("categories", s).apply()
    }

    fun isEnabled(): Boolean = enabledCategories().isNotEmpty()

    // ---- Questions needed ----

    /** Fixed: the quiz is not user-configurable (3 correct answers). */
    fun questionsNeeded(): Int = QUIZ_FIXED_NEEDED

    fun setQuestionsNeeded(n: Int) {
        sp.edit().putInt("needed", n.coerceIn(1, 10)).apply()
    }

    // ---- Delay and emergency passes (shared with block schedules) ----

    /** Fixed: 10 seconds before the first question. */
    fun delaySeconds(): Int = QUIZ_FIXED_DELAY_SECONDS

    fun setDelaySeconds(s: Int) = schedules.setDelaySeconds(s.coerceIn(0, 60))

    fun passesPerDay(): Int = schedules.passesPerDay()

    // ---- Temporary unlock: a quiz (or your own question) opens a blocked app for a few minutes ----

    /** Unlocks allowed per app in any 24 hours: default 1, you can raise it to 3. */
    fun unlocksPerDayAllowed(): Int = sp.getInt("unlock_allowance", 1).coerceIn(1, 3)

    fun setUnlocksPerDayAllowed(n: Int) = sp.edit().putInt("unlock_allowance", n.coerceIn(1, 3)).apply()

    /** Minutes an unlock lasts: default 5, adjustable from 1 to 10. */
    fun unlockMinutes(): Int = sp.getInt("unlock_minutes", 5).coerceIn(1, 10)

    fun setUnlockMinutes(n: Int) = sp.edit().putInt("unlock_minutes", n.coerceIn(1, 10)).apply()

    /** Unlocks of [pkg] in the last 24 hours. */
    fun unlocksLast24h(pkg: String, now: Long = System.currentTimeMillis()): Int {
        val cutoff = now - 24L * 60 * 60 * 1000
        return (sp.getString("unlocks_$pkg", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.count { it > cutoff }
    }

    fun canUnlock(pkg: String): Boolean = unlocksLast24h(pkg) < unlocksPerDayAllowed()

    /** Records one unlock of [pkg] now. */
    fun recordUnlock(pkg: String, now: Long = System.currentTimeMillis()) {
        val cutoff = now - 24L * 60 * 60 * 1000
        val kept = (sp.getString("unlocks_$pkg", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.filter { it > cutoff }
        sp.edit().putString("unlocks_$pkg", (kept + now).joinToString(",")).apply()
    }

    fun setPassesPerDay(n: Int) = schedules.setPassesPerDay(n)

    // ---- Attempts today (resets daily) ----

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)

    fun attemptsToday(): Int = sp.getInt("attempts_${today()}", 0)

    /** Call once each time the gate is shown. */
    fun addAttempt() {
        sp.edit().putInt("attempts_${today()}", attemptsToday() + 1).apply()
    }

    // ---- Own questions ----

    fun customQuestions(): List<CustomQuestion> {
        val raw = sp.getString("custom", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val c = o.getJSONArray("choices")
                CustomQuestion(
                    prompt = o.getString("prompt"),
                    choices = (0 until c.length()).map { c.getString(it) },
                    answerIndex = o.getInt("answer"),
                    explain = o.optString("explain", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustom(list: List<CustomQuestion>) {
        val arr = JSONArray()
        for (q in list) {
            arr.put(
                JSONObject().apply {
                    put("prompt", q.prompt)
                    put("choices", JSONArray(q.choices))
                    put("answer", q.answerIndex)
                    put("explain", q.explain)
                }
            )
        }
        sp.edit().putString("custom", arr.toString()).apply()
    }

    fun addCustomQuestion(q: CustomQuestion) = saveCustom(customQuestions() + q)

    fun deleteCustomQuestion(index: Int) {
        val cur = customQuestions()
        if (index in cur.indices) saveCustom(cur.filterIndexed { i, _ -> i != index })
    }

    // ---- Per-category daily limits (0 = no limit) ----

    fun categoryCap(id: String): Int = sp.getInt("cap_$id", 0)

    fun setCategoryCap(id: String, n: Int) {
        sp.edit().putInt("cap_$id", n.coerceIn(0, 200)).apply()
    }

    // ---- Daily log of questions asked: each one is never asked again that day ----

    /** Every question ever asked on this phone, kept across days so none comes back. */
    fun askedToday(): Set<String> = sp.getStringSet("asked_log", emptySet())?.toSet() ?: emptySet()

    /** Logs one question as asked. The log is never cleared, so a question is not asked again. */
    fun recordAsked(category: String, prompt: String) {
        val cur = askedToday().toMutableSet()
        cur.add("$category|$prompt")
        sp.edit().putStringSet("asked_log", cur).apply()
    }

    fun usedTodayByCategory(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (entry in askedToday()) {
            val cat = entry.substringBefore("|")
            out[cat] = (out[cat] ?: 0) + 1
        }
        return out
    }

    /** The config for one gate, or null if the user switched every category off. Read this before [addAttempt]. */
    fun gateConfig(): QuizGateConfig? {
        if (!isEnabled()) return null
        val cats = QUIZ_CATEGORIES.map { it.id }.filter { it in enabledCategories() }
        return QuizGateConfig(
            categories = cats,
            needed = questionsNeeded(),
            delaySeconds = delaySeconds(),
            attemptsToday = attemptsToday(),
            custom = customQuestions(),
            caps = cats.associateWith { categoryCap(it) },
            usedToday = usedTodayByCategory(),
            askedKeys = askedToday()
        )
    }
}
