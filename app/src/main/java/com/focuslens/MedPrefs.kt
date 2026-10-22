package com.focuslens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Medication tracker data. ON DEVICE ONLY: stored in the "meds_private" SharedPreferences file,
 * never read by any Firebase/network code, and the app has allowBackup="false" so it is not in
 * cloud backup or device transfer. Everything the user types is free text copied from their own
 * label; the app never suggests doses, products or interactions.
 */
data class MedPlan(
    val id: String,
    val name: String,
    val strengthText: String = "",
    val instructions: String = "",
    /** "HH:mm" 24-hour. */
    val times: List<String> = emptyList(),
    /** ISO weekdays, 1 = Monday ... 7 = Sunday. Empty = every day. */
    val days: Set<Int> = emptySet(),
    val notes: String = ""
)

enum class DoseState { UPCOMING, DUE, TAKEN, MISSED }

data class Dose(
    val plan: MedPlan,
    val time: String,
    val state: DoseState,
    val takenAtMillis: Long?
) {
    val key: String get() = MedLogic.doseKey(plan.id, time)
}

data class LabelGuess(val name: String, val strength: String, val instructions: String)

/** Pure helpers, no Android dependencies beyond java.util, so they are unit-testable. */
object MedLogic {
    const val MISSED_AFTER_MINUTES = 120

    fun doseKey(planId: String, time: String) = "$planId@$time"

    fun isValidTime(t: String): Boolean {
        val m = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$").matchEntire(t.trim()) ?: return false
        return m.groupValues.size == 3
    }

    /** Normalises "8:05" to "08:05". Returns null if invalid. */
    fun normalizeTime(t: String): String? {
        if (!isValidTime(t)) return null
        val parts = t.trim().split(":")
        return String.format(Locale.US, "%02d:%02d", parts[0].toInt(), parts[1].toInt())
    }

    fun minutesOf(time: String): Int {
        val p = time.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    fun dateKey(cal: Calendar): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)

    fun isoDayOfWeek(cal: Calendar): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    fun appliesOn(plan: MedPlan, isoDay: Int) = plan.days.isEmpty() || plan.days.contains(isoDay)

    fun doseState(nowMinute: Int, doseMinute: Int, takenAt: Long?): DoseState = when {
        takenAt != null -> DoseState.TAKEN
        nowMinute < doseMinute -> DoseState.UPCOMING
        nowMinute - doseMinute > MISSED_AFTER_MINUTES -> DoseState.MISSED
        else -> DoseState.DUE
    }

    /** Millis of the next scheduled dose strictly after afterMs, or null if no plan has times. */
    fun nextDoseMillis(plans: List<MedPlan>, afterMs: Long): Long? {
        var best: Long? = null
        for (offset in 0..7) {
            val base = Calendar.getInstance().apply {
                timeInMillis = afterMs
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val iso = isoDayOfWeek(base)
            for (p in plans) {
                if (!appliesOn(p, iso)) continue
                for (t in p.times) {
                    if (!isValidTime(t)) continue
                    val m = minutesOf(t)
                    val c = (base.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, m / 60)
                        set(Calendar.MINUTE, m % 60)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val ms = c.timeInMillis
                    if (ms > afterMs && (best == null || ms < best!!)) best = ms
                }
            }
            if (best != null) return best
        }
        return best
    }

    /** Plans/times scheduled for exactly the minute of `atMs`. Returns (plan, "HH:mm") pairs. */
    fun dosesAt(plans: List<MedPlan>, atMs: Long): List<Pair<MedPlan, String>> {
        val cal = Calendar.getInstance().apply { timeInMillis = atMs }
        val iso = isoDayOfWeek(cal)
        val time = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        return plans.filter { appliesOn(it, iso) && it.times.mapNotNull { t -> normalizeTime(t) }.contains(time) }
            .map { it to time }
    }

    private fun hhmm(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** Plain-text update for family. Doses are today's doses as returned by dueToday. */
    fun shareSummary(dateLabel: String, doses: List<Dose>): String {
        fun join(list: List<String>) = if (list.isEmpty()) "none" else list.joinToString(", ")
        val taken = doses.filter { it.state == DoseState.TAKEN }
            .map { "${it.plan.name} ${hhmm(it.takenAtMillis ?: 0L)}" }
        val still = doses.filter { it.state == DoseState.UPCOMING || it.state == DoseState.DUE }
            .map { "${it.plan.name} ${it.time}" }
        val missed = doses.filter { it.state == DoseState.MISSED }
            .map { "${it.plan.name} ${it.time}" }
        return "Medication update for $dateLabel: Taken: ${join(taken)}. " +
            "Still to take: ${join(still)}. Missed: ${join(missed)}"
    }

    fun listSummary(plans: List<MedPlan>, includeNotes: Boolean): String {
        if (plans.isEmpty()) return "My medication list: (empty)"
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val lines = plans.map { p ->
            val sb = StringBuilder("- ${p.name}")
            if (p.strengthText.isNotBlank()) sb.append(" ${p.strengthText}")
            if (p.times.isNotEmpty()) sb.append(" at ${p.times.sorted().joinToString(", ")}")
            if (p.days.isNotEmpty()) sb.append(" (${p.days.sorted().joinToString(" ") { dayNames[it - 1] }})")
            if (includeNotes && p.notes.isNotBlank()) sb.append(". Notes: ${p.notes}")
            sb.toString()
        }
        return "My medication list:\n" + lines.joinToString("\n")
    }

    private val STRENGTH_REGEX =
        Regex("(\\d+(\\.\\d+)?)\\s?(mg|mcg|µg|g|ml|IU)\\b", RegexOption.IGNORE_CASE)
    private val INSTRUCTION_WORDS = listOf("take", "daily", "tablet", "capsule", "with food")

    /** Proposes fields from OCR lines (text, line height in px). The user must confirm/edit. */
    fun parseLabel(lines: List<Pair<String, Int>>): LabelGuess {
        val cleaned = lines.map { it.first.trim() to it.second }.filter { it.first.isNotEmpty() }
        fun isInstruction(s: String) = INSTRUCTION_WORDS.any { s.contains(it, ignoreCase = true) }
        val name = cleaned
            .filter { (t, _) ->
                val letters = t.count { it.isLetter() }
                letters >= 3 && letters * 2 >= t.length && !isInstruction(t)
            }
            .let { cands ->
                var best: Pair<String, Int>? = null
                for (c in cands) if (best == null || c.second > best.second) best = c
                best?.first ?: ""
            }
        val strength = STRENGTH_REGEX.find(cleaned.joinToString("\n") { it.first })?.value ?: ""
        val instructions = cleaned.map { it.first }.filter { isInstruction(it) }.joinToString(" ")
        return LabelGuess(name, strength, instructions)
    }
}

class MedPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun plans(): List<MedPlan> {
        val raw = sp.getString("plans", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val t = o.optJSONArray("times") ?: JSONArray()
                val d = o.optJSONArray("days") ?: JSONArray()
                MedPlan(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    strengthText = o.optString("strength", ""),
                    instructions = o.optString("instructions", ""),
                    times = (0 until t.length()).map { t.getString(it) },
                    days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                    notes = o.optString("notes", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun savePlans(list: List<MedPlan>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("strength", p.strengthText)
                    put("instructions", p.instructions)
                    put("times", JSONArray(p.times))
                    put("days", JSONArray(p.days.toList()))
                    put("notes", p.notes)
                }
            )
        }
        sp.edit().putString("plans", arr.toString()).apply()
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** Adds a plan, or replaces the one with the same id (edit). */
    fun upsert(p: MedPlan) {
        val cur = plans().toMutableList()
        val i = cur.indexOfFirst { it.id == p.id }
        if (i >= 0) cur[i] = p else cur.add(p)
        savePlans(cur)
    }

    fun delete(id: String) = savePlans(plans().filter { it.id != id })

    // ---- Dose log: dateKey -> (planId@HH:mm -> takenAtMillis) ----

    private fun readLog(): JSONObject = try {
        JSONObject(sp.getString("log", null) ?: "{}")
    } catch (e: Exception) {
        JSONObject()
    }

    private fun takenAt(log: JSONObject, dateKey: String, doseKey: String): Long? {
        val day = log.optJSONObject(dateKey) ?: return null
        return if (day.has(doseKey)) day.optLong(doseKey) else null
    }

    fun markTaken(doseKey: String, nowMs: Long = System.currentTimeMillis()) {
        val log = readLog()
        val dk = MedLogic.dateKey(Calendar.getInstance().apply { timeInMillis = nowMs })
        val day = log.optJSONObject(dk) ?: JSONObject()
        day.put(doseKey, nowMs)
        log.put(dk, day)
        // Keep the log small: drop days older than 60 days.
        val cutoff = MedLogic.dateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -60) })
        val old = log.keys().asSequence().filter { it < cutoff }.toList()
        old.forEach { log.remove(it) }
        sp.edit().putString("log", log.toString()).apply()
    }

    fun undo(doseKey: String, nowMs: Long = System.currentTimeMillis()) {
        val log = readLog()
        val dk = MedLogic.dateKey(Calendar.getInstance().apply { timeInMillis = nowMs })
        log.optJSONObject(dk)?.remove(doseKey)
        sp.edit().putString("log", log.toString()).apply()
    }

    /** Every dose scheduled today with its state, ordered by time. */
    fun dueToday(nowMs: Long = System.currentTimeMillis()): List<Dose> {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val dk = MedLogic.dateKey(cal)
        val iso = MedLogic.isoDayOfWeek(cal)
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val log = readLog()
        val out = mutableListOf<Dose>()
        for (p in plans()) {
            if (!MedLogic.appliesOn(p, iso)) continue
            for (t in p.times) {
                val time = MedLogic.normalizeTime(t) ?: continue
                val taken = takenAt(log, dk, MedLogic.doseKey(p.id, time))
                out.add(Dose(p, time, MedLogic.doseState(nowMin, MedLogic.minutesOf(time), taken), taken))
            }
        }
        return out.sortedWith(compareBy({ it.time }, { it.plan.name }))
    }

    // ---- Settings ----

    /** Default ON: notifications say "Time for a medication" instead of the name. */
    fun hideNamesInNotifications(): Boolean = sp.getBoolean("hide_names", true)

    fun setHideNamesInNotifications(v: Boolean) {
        sp.edit().putBoolean("hide_names", v).apply()
    }

    companion object {
        /** Name of the SharedPreferences file; keep out of any backup rules. */
        const val FILE = "meds_private"
    }
}
