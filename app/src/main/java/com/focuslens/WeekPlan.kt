package com.focuslens

import android.content.Context
import android.provider.CalendarContract
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** What a day is for. Each template asks for a few fields. */
enum class DayTemplate(val label: String, val fields: List<Pair<String, String>>) {
    NONE("No template", emptyList()),
    PROFESSIONAL("Professional learning", listOf(
        "course" to "Course, module or topic",
        "goal" to "Learning goal",
        "material" to "Material (book, link, video)",
        "minutes" to "Time block (minutes)",
        "start" to "Start time (HH:mm, optional)"
    )),
    PERSONAL_PROJECT("Personal project learning", listOf(
        "project" to "Hobby or project",
        "skill" to "Skill to learn",
        "nextStep" to "Small next step",
        "minutes" to "Time block (minutes)"
    )),
    MEAL_PREP("Meal prep", listOf(
        "meals" to "Meals to plan",
        "grocery" to "Grocery list",
        "pickup" to "Pickup or delivery order",
        "minutes" to "Cooking and prep time (minutes)"
    )),
    PLANNING_REST("Planning and rest", listOf(
        "review" to "Review next week",
        "month" to "Coming month: key dates",
        "priorities" to "Top 3 priorities"
    )),
    ADMIN("Admin", listOf(
        "inbox" to "Inbox tidy (20 minutes)",
        "grocery" to "Grocery order",
        "top3" to "Three tasks for today"
    )),
    FUN_SELF_CARE("Fun and self-care", listOf(
        "choice" to "What I'll do",
        "plan" to "Plan (optional)"
    ));

    companion object {
        /** Fun / self-care choices, shown as tap-to-pick chips. */
        val FUN_CHOICES = listOf("Outing", "See a friend", "Try somewhere new", "Enjoy a hobby", "Quiet self-care day")

        /** Suggests a template from the day's theme name, using the existing theme names. */
        fun suggestFor(theme: String): DayTemplate {
            val t = theme.lowercase()
            return when {
                "career" in t || "upskill" in t -> PROFESSIONAL
                "content" in t || "personal project" in t -> PERSONAL_PROJECT
                "meal" in t -> MEAL_PREP
                "planning" in t || "rest" in t -> PLANNING_REST
                "admin" in t -> ADMIN
                "fun" in t || "self-care" in t || "self care" in t -> FUN_SELF_CARE
                else -> NONE
            }
        }
    }
}

/** One day of the week. Index 0 is Monday. */
data class WeekDay(
    val theme: String,
    val template: DayTemplate,
    val fields: Map<String, String>,
    /** Offered by the app, not entered by the person yet. They can keep or remove it. */
    val suggested: Boolean = false,
    /** Other tasks are not suggested on a protected day. */
    val protectedDay: Boolean = false
)

/** The kind of task. Consequence tasks come first, because missing them costs the most. */
enum class TaskKind(val label: String, val rank: Int, val hint: String) {
    CONSEQUENCE("Consequence", 0, "What gets worse if I miss it: bills, deadlines, medication, appointments"),
    MAINTENANCE("Maintenance", 1, "What keeps everyday life working: dishes, laundry, cleaning, groceries"),
    FUTURE_YOU("Future you", 2, "What makes tomorrow easier: pack a bag, set out clothes, plan tomorrow"),
    OTHER("Other", 3, "Anything else")
}

enum class Repeat(val label: String, val rrule: String?) {
    NONE("Once", null),
    WEEKLY("Every week", "FREQ=WEEKLY"),
    MONTHLY("Every month", "FREQ=MONTHLY")
}

data class PlanItem(
    val id: Long,
    val title: String,
    val kind: TaskKind,
    val minutes: Int,
    val repeat: Repeat,
    /** 1 = high, 2 = medium, 3 = low. */
    val priority: Int,
    /** yyyyMMdd, or blank if there is no due date. */
    val dueDay: String,
    /** HH:mm, or blank if no time was chosen. Only set when the person chooses one. */
    val startTime: String,
    val notes: String,
    val done: Boolean,
    /** "Not now" hides the item until this time. 0 = not hidden. */
    val snoozeUntil: Long
)

/** A routine repeats on one weekday (Calendar.MONDAY..SUNDAY values are used as 2..7, 1 = Sunday). */
data class Routine(val id: Long, val title: String, val weekday: Int)

data class WeekPlan(
    val days: List<WeekDay>,
    val items: List<PlanItem>,
    val routines: List<Routine>,
    /** yyyyMMdd -> routine ids done that day. */
    val routineDone: Map<String, List<Long>>
)

object WeekPlanStore {
    private const val FILE = "week_plan"

    private fun sp(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The starting week: five days for the person to name, and the two suggested additions. */
    fun defaultPlan(): WeekPlan = WeekPlan(
        days = List(5) { WeekDay("", DayTemplate.NONE, emptyMap()) } + listOf(
            WeekDay("Meal Prep", DayTemplate.MEAL_PREP, emptyMap(), suggested = true),
            WeekDay("Weekly Planning + Rest", DayTemplate.PLANNING_REST, emptyMap(), suggested = true, protectedDay = true)
        ),
        items = emptyList(),
        routines = emptyList(),
        routineDone = emptyMap()
    )

    fun load(c: Context): WeekPlan = try {
        val raw = sp(c).getString("json", null) ?: return defaultPlan()
        val o = JSONObject(raw)
        WeekPlan(
            days = o.getJSONArray("days").let { arr ->
                (0 until arr.length()).map { i ->
                    val d = arr.getJSONObject(i)
                    val f = d.getJSONObject("fields")
                    WeekDay(
                        theme = d.getString("theme"),
                        template = DayTemplate.valueOf(d.getString("template")),
                        fields = f.keys().asSequence().associateWith { f.getString(it) },
                        suggested = d.optBoolean("suggested", false),
                        protectedDay = d.optBoolean("protected", false)
                    )
                }
            },
            items = o.getJSONArray("items").let { arr ->
                (0 until arr.length()).map { i ->
                    val x = arr.getJSONObject(i)
                    PlanItem(
                        id = x.getLong("id"),
                        title = x.getString("title"),
                        kind = TaskKind.valueOf(x.getString("kind")),
                        minutes = x.getInt("minutes"),
                        repeat = Repeat.valueOf(x.getString("repeat")),
                        priority = x.getInt("priority"),
                        dueDay = x.getString("dueDay"),
                        startTime = x.getString("startTime"),
                        notes = x.getString("notes"),
                        done = x.getBoolean("done"),
                        snoozeUntil = x.getLong("snoozeUntil")
                    )
                }
            },
            routines = o.getJSONArray("routines").let { arr ->
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    Routine(r.getLong("id"), r.getString("title"), r.getInt("weekday"))
                }
            },
            routineDone = o.getJSONObject("routineDone").let { m ->
                m.keys().asSequence().associateWith { k ->
                    m.getJSONArray(k).let { a -> (0 until a.length()).map { a.getLong(it) } }
                }
            }
        )
    } catch (e: Exception) {
        // Unreadable saved data: start from the default rather than crash.
        defaultPlan()
    }

    fun save(c: Context, p: WeekPlan) {
        val o = JSONObject()
        o.put("days", JSONArray().apply {
            p.days.forEach { d ->
                put(JSONObject().apply {
                    put("theme", d.theme)
                    put("template", d.template.name)
                    put("fields", JSONObject().apply { d.fields.forEach { (k, v) -> put(k, v) } })
                    put("suggested", d.suggested)
                    put("protected", d.protectedDay)
                })
            }
        })
        o.put("items", JSONArray().apply {
            p.items.forEach { x ->
                put(JSONObject().apply {
                    put("id", x.id); put("title", x.title); put("kind", x.kind.name)
                    put("minutes", x.minutes); put("repeat", x.repeat.name); put("priority", x.priority)
                    put("dueDay", x.dueDay); put("startTime", x.startTime); put("notes", x.notes)
                    put("done", x.done); put("snoozeUntil", x.snoozeUntil)
                })
            }
        })
        o.put("routines", JSONArray().apply {
            p.routines.forEach { r ->
                put(JSONObject().apply { put("id", r.id); put("title", r.title); put("weekday", r.weekday) })
            }
        })
        o.put("routineDone", JSONObject().apply {
            p.routineDone.forEach { (k, ids) -> put(k, JSONArray().apply { ids.forEach { put(it) } }) }
        })
        sp(c).edit().putString("json", o.toString()).apply()
    }
}

object WeekLogic {
    /** Index into WeekPlan.days for a given day (Monday = 0). */
    fun dayIndex(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

    fun dayKey(millis: Long): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date(millis))

    /**
     * The one task to do next. Consequence tasks first, then the nearest due date, then priority.
     * Hidden while snoozed or done. Returns null on a protected day, so nothing else competes with rest.
     */
    fun nextSuggestion(plan: WeekPlan, now: Long): PlanItem? {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        if (plan.days.getOrNull(dayIndex(today))?.protectedDay == true) return null
        return plan.items
            .filter { !it.done && it.snoozeUntil <= now }
            .minWithOrNull(
                compareBy<PlanItem>({ it.kind.rank })
                    .thenBy { if (it.dueDay.isBlank()) "99999999" else it.dueDay }
                    .thenBy { it.priority }
            )
    }

    /** Why this task is the next one, in plain words. */
    fun reasonFor(item: PlanItem): String {
        val due = if (item.dueDay.isBlank()) "" else " It is due " + prettyDay(item.dueDay) + "."
        return when (item.kind) {
            TaskKind.CONSEQUENCE -> "Missing it has a cost.$due"
            TaskKind.MAINTENANCE -> "It keeps everyday life working.$due"
            TaskKind.FUTURE_YOU -> "It makes tomorrow easier.$due"
            TaskKind.OTHER -> due.trim().ifBlank { "Next on your list." }
        }
    }

    fun prettyDay(yyyyMMdd: String): String = try {
        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.US)
            .format(java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).parse(yyyyMMdd)!!)
    } catch (e: Exception) {
        yyyyMMdd
    }
}

/** A calendar event that takes up time. */
data class BusyBlock(val title: String, val start: Long, val end: Long, val allDay: Boolean)

/** Reads the calendars already on the phone and finds free time. Only reads; never changes events itself. */
object WeekCalendar {
    /** How many visible calendars are on the phone. -1 if calendar access is not granted. */
    fun linkedCalendarCount(c: Context): Int = try {
        c.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null, null
        )?.use { it.count } ?: 0
    } catch (e: SecurityException) {
        -1
    }

    /** Events and their repeats that overlap [start, end). */
    fun eventsBetween(c: Context, start: Long, end: Long): List<BusyBlock> = try {
        CalendarContract.Instances.query(
            c.contentResolver,
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            ),
            start, end
        )?.use { cur ->
            val out = mutableListOf<BusyBlock>()
            while (cur.moveToNext()) {
                out += BusyBlock(
                    title = cur.getString(0) ?: "(no title)",
                    start = cur.getLong(1),
                    end = cur.getLong(2),
                    allDay = cur.getInt(3) == 1
                )
            }
            out.sortedBy { it.start }
        } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }

    /** Start times of up to [count] free windows of [minutes] between [fromHour] and [toHour] on that day. */
    fun freeSlots(busy: List<BusyBlock>, dayStart: Long, minutes: Int, fromHour: Int = 8, toHour: Int = 21, count: Int = 3): List<Long> {
        val windowStart = dayStart + fromHour * 3_600_000L
        val windowEnd = dayStart + toHour * 3_600_000L
        val need = minutes * 60_000L
        val timed = busy.filter { !it.allDay && it.end > windowStart && it.start < windowEnd }.sortedBy { it.start }
        val slots = mutableListOf<Long>()
        var cursor = windowStart
        for (b in timed) {
            if (b.start - cursor >= need) slots += cursor
            cursor = maxOf(cursor, b.end)
            if (slots.size >= count) return slots
        }
        if (windowEnd - cursor >= need) slots += cursor
        return slots.take(count)
    }

    /** Events that overlap a planned block starting at [start] for [minutes]. */
    fun conflicts(busy: List<BusyBlock>, start: Long, minutes: Int): List<BusyBlock> {
        val end = start + minutes * 60_000L
        return busy.filter { !it.allDay && it.start < end && it.end > start }
    }
}
