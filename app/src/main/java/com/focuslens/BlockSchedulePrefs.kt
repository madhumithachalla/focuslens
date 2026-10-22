package com.focuslens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Time-windowed blocking schedules, the pause delay and the daily emergency passes.
 * A schedule blocks the same apps as FocusPrefs.blocked(), but only while its window is open.
 */
class BlockSchedulePrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("block_schedules", Context.MODE_PRIVATE)

    fun schedules(): List<BlockSchedule> {
        val raw = sp.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val d = o.getJSONArray("days")
                BlockSchedule(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    startMinutes = o.getInt("start"),
                    endMinutes = o.getInt("end"),
                    days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                    strict = o.optBoolean("strict", false),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(list: List<BlockSchedule>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("start", s.startMinutes)
                    put("end", s.endMinutes)
                    put("days", JSONArray(s.days.toList()))
                    put("strict", s.strict)
                    put("enabled", s.enabled)
                }
            )
        }
        sp.edit().putString("list", arr.toString()).apply()
    }

    /** Adds a new schedule, or replaces the one with the same id. */
    fun upsert(s: BlockSchedule) {
        val cur = schedules().toMutableList()
        val i = cur.indexOfFirst { it.id == s.id }
        if (i >= 0) cur[i] = s else cur.add(s)
        save(cur)
    }

    fun delete(id: String) = save(schedules().filter { it.id != id })

    /** The enabled schedule open right now (a strict one wins over a non-strict one), or null. */
    fun activeSchedule(nowMs: Long = System.currentTimeMillis()): BlockSchedule? {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        // Calendar: 1 = Sunday ... 7 = Saturday -> ISO 1 = Monday ... 7 = Sunday
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val active = schedules().filter {
            it.enabled && isWindowActive(dow, minute, it.startMinutes, it.endMinutes, it.days)
        }
        return active.firstOrNull { it.strict } ?: active.firstOrNull()
    }

    // ---- Pause delay (seconds) ----

    fun delaySeconds(): Int = sp.getInt("delay", 5)

    fun setDelaySeconds(s: Int) {
        sp.edit().putInt("delay", s).apply()
    }

    // ---- Emergency passes, reset daily ----

    fun passesPerDay(): Int = sp.getInt("passes_per_day", 3)

    fun setPassesPerDay(n: Int) {
        sp.edit().putInt("passes_per_day", n.coerceIn(0, 20)).apply()
    }

    private fun today(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().time)

    fun passesUsedToday(): Int = sp.getInt("used_${today()}", 0)

    fun passesLeft(): Int = (passesPerDay() - passesUsedToday()).coerceAtLeast(0)

    /** Spends one pass; returns false if none are left. */
    fun usePass(): Boolean {
        if (passesLeft() <= 0) return false
        sp.edit().putInt("used_${today()}", passesUsedToday() + 1).apply()
        return true
    }
}
