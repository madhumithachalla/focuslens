package com.focuslens

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Water and reading records, kept as files in the app's private folder (not the main database),
 * so a database change can never delete them.
 */
object TrackersStore {
    private fun file(c: Context, name: String) = File(c.filesDir, name)
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    /** The yyyyMMdd key for the local day that [millis] falls on. */
    fun dayKey(millis: Long): String = dayFmt.format(millis)

    /** Start of the local day [back] days before today. */
    fun dayStart(back: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -back)
        return c.timeInMillis
    }

    // ---------------- Water ----------------

    data class Drink(val at: Long, val ml: Int)

    fun drinks(c: Context): List<Drink> = try {
        val f = file(c, "hydration.json")
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Drink(it.getLong("at"), it.getInt("ml")) } }
        }
    } catch (e: Exception) { emptyList() }

    private fun saveDrinks(c: Context, list: List<Drink>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("at", it.at).put("ml", it.ml)) }
        file(c, "hydration.json").writeText(arr.toString())
    }

    fun addDrink(c: Context, ml: Int) = saveDrinks(c, drinks(c) + Drink(System.currentTimeMillis(), ml))

    fun undoLastDrink(c: Context) {
        val list = drinks(c)
        if (list.isNotEmpty()) saveDrinks(c, list.dropLast(1))
    }

    /** Millilitres drunk per day, for the last [days] days (index 0 = today). */
    fun waterByDay(c: Context, days: Int): List<Int> {
        val all = drinks(c)
        return (0 until days).map { back ->
            val key = dayKey(dayStart(back) + 1000L)
            all.filter { dayKey(it.at) == key }.sumOf { it.ml }
        }
    }

    fun waterGoalMl(c: Context): Int = c.getSharedPreferences("trackers", Context.MODE_PRIVATE).getInt("water_goal", 2000)

    fun setWaterGoalMl(c: Context, ml: Int) =
        c.getSharedPreferences("trackers", Context.MODE_PRIVATE).edit().putInt("water_goal", ml.coerceIn(500, 6000)).apply()

    // ---------------- Reading ----------------

    data class Session(val at: Long, val pages: Int)
    data class Book(val id: Long, val title: String, val totalPages: Int, val sessions: List<Session>) {
        val pagesRead: Int get() = sessions.sumOf { it.pages }
    }

    fun books(c: Context): List<Book> = try {
        val f = file(c, "reading.json")
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ss = o.getJSONArray("sessions")
                Book(
                    id = o.getLong("id"),
                    title = o.getString("title"),
                    totalPages = o.getInt("totalPages"),
                    sessions = (0 until ss.length()).map { j ->
                        ss.getJSONObject(j).let { Session(it.getLong("at"), it.getInt("pages")) }
                    }
                )
            }
        }
    } catch (e: Exception) { emptyList() }

    fun saveBooks(c: Context, list: List<Book>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().put("id", b.id).put("title", b.title).put("totalPages", b.totalPages)
                    .put("sessions", JSONArray().apply { b.sessions.forEach { put(JSONObject().put("at", it.at).put("pages", it.pages)) } })
            )
        }
        file(c, "reading.json").writeText(arr.toString())
    }

    /** Pages read per day, for the last [days] days (index 0 = today). */
    fun pagesByDay(c: Context, days: Int): List<Int> {
        val all = books(c).flatMap { it.sessions }
        return (0 until days).map { back ->
            val key = dayKey(dayStart(back) + 1000L)
            all.filter { dayKey(it.at) == key }.sumOf { it.pages }
        }
    }
}
