package com.focuslens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Task categories. Stored by name, so adding one needs no database migration. */
enum class TaskType(val label: String) {
    STUDY("Study"),
    ADMIN("Email & admin"),
    WELLNESS("Health & self-care"),
    WORK("Work & jobs"),
    HOME("Home & chores"),
    SOCIAL("People")
}

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: TaskType,
    /** Epoch millis the task is scheduled for; synced from the device calendar when calendarEventId is set. */
    val scheduledAt: Long,
    val calendarEventId: Long? = null,
    /** Self-authored recall cue, e.g. "what should I remember from this" — only used for STUDY tasks. */
    val recallNote: String? = null,
    val completedAt: Long? = null
)

enum class MissReason { TIRED, DISTRACTED, TOO_BIG, FORGOT, OTHER }

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val completed: Boolean,
    /** Tap-selected reason when completed = false; always present alongside optional free text. */
    val reason: MissReason? = null,
    val note: String? = null,
    /** For STUDY tasks: did the user correctly recall the recallNote when re-asked. Null if not applicable/not yet asked. */
    val recallCorrect: Boolean? = null,
    val createdAt: Long
)

/** GENERAL_ENERGY/MOOD/SLEEP/WATER are independent trackers — a day can carry any subset of
 *  them at once (see the composite key below), never an either/or like the old single-row-
 *  per-day schema forced. */
enum class CycleMode { MENSTRUAL, GENERAL_ENERGY, MOOD, SLEEP, WATER, OFF }

/** Composite (date, mode) key — not autoGenerate id — is what lets one day hold a MENSTRUAL
 *  row and a MOOD row and a SLEEP row simultaneously, each independently upserted. */
@Entity(tableName = "cycle_logs", primaryKeys = ["date", "mode"])
data class CycleLogEntity(
    val mode: CycleMode,
    val date: String, // yyyyMMdd
    val energyLevel: Int? = null, // 1-5, meaning depends on mode (energy/mood/sleep/water level)
    val moodTags: String? = null, // comma-separated, free-form so it never forces a gendered vocabulary
    val symptomTags: String? = null,
    /** Marks the first day of a period — the only field the cycle-length prediction reads. */
    val periodStart: Boolean = false
)

enum class MindfulnessType { BREATHING, MEDITATION, STRETCH, WALK }

@Entity(tableName = "mindfulness_sessions")
data class MindfulnessSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: MindfulnessType,
    val durationSec: Int,
    /** The check-in id that suggested this session, if any — lets suggestions get scored for effectiveness. */
    val triggeredByCheckInId: Long? = null,
    val startedAt: Long
)

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyyMMdd — one entry per day, keyed by date like the cycle log
    val prompt: String?,
    val text: String,
    val mood: String?, // one of the same mood ids used elsewhere: low/flat/okay/good/great
    val createdAt: Long
)

@Entity(tableName = "wearable_samples")
data class WearableSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // "health_connect" (Android) or "healthkit" (iOS, future)
    val metric: String, // "steps" | "heart_rate" | "sleep_minutes"
    val value: Double,
    val recordedAt: Long
)

/**
 * FocusLens's own exact per-day, per-app usage snapshot — kept so history doesn't degrade
 * once Android's precise event log (about a week) ages into coarser monthly/yearly
 * buckets. Written once per finished day by UsageHistoryRepository, never edited after.
 */
@Entity(tableName = "daily_usage", primaryKeys = ["date", "pkg"])
data class DailyUsageEntity(
    val date: String, // yyyyMMdd
    val pkg: String,
    val millis: Long,
    val launches: Int
)

/**
 * Hour-of-day granularity, same "protect it before Android's raw event log ages out"
 * strategy as DailyUsageEntity — Android itself never keeps hourly detail beyond about a
 * week, so this is the only way an hour-by-hour view can exist for days further back than
 * that. A session spanning an hour boundary is split proportionally across both hours by
 * UsageRepository.hourlyBreakdown, so these numbers add up to the same daily total.
 */
@Entity(tableName = "hourly_usage", primaryKeys = ["date", "hour", "pkg"])
data class HourlyUsageEntity(
    val date: String, // yyyyMMdd
    val hour: Int, // 0-23, device local time
    val pkg: String,
    val millis: Long
)

/**
 * A friend/family birthday the user tracks. `birthday` is MMdd (month+day only, 4 chars) —
 * the year genuinely doesn't matter for a recurring birthday and storing one would wrongly
 * imply FocusLens is tracking someone's age. `source` distinguishes a manually-typed entry
 * from one pulled in via the optional, permission-gated Google Contacts import.
 */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthday: String?, // MMdd, or null if unknown (still trackable in the wish checklist)
    val source: String = "manual", // "manual" | "google_contacts"
    val createdAt: Long
)

/**
 * Per-year checklist entry: did this person wish the user on the user's own birthday this
 * year. Nothing here is detected automatically (no message/call content is ever read) — it's
 * a plain manual checklist so the user doesn't lose track of who to thank or wish back.
 */
@Entity(tableName = "birthday_wishes", primaryKeys = ["year", "personId"])
data class BirthdayWishEntity(
    val year: Int,
    val personId: Long,
    val wished: Boolean = false
)

enum class AgeBracket { UNDER_18, AGE_18_24, AGE_25_34, AGE_35_44, AGE_45_PLUS, PREFER_NOT_TO_SAY }

/**
 * Single-row profile used only to tailor copy, defaults and which Settings sections show
 * by default. Never used to gate features.
 */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    /** What the app calls the user elsewhere (Journal, celebrations) — local only, no account behind it. */
    val preferredName: String,
    val ageBracket: AgeBracket,
    /** Free text, not an enum — never force a user into a fixed gender list. */
    val genderIdentity: String?,
    /** Independent, not mutually exclusive — tracking a cycle and general energy isn't an either/or. */
    val trackMenstrualCycle: Boolean,
    val trackGeneralEnergy: Boolean,
    /** Comma-joined ids from TOPIC_OPTIONS (OnboardingScreen.kt) — free-form tags, not an enum. */
    val topics: String,
    /**
     * Comma-joined ids from NEURODIVERGENT_OPTIONS (OnboardingScreen.kt) — entirely optional,
     * never required to complete onboarding, never used to gate any feature. Only ever used
     * to surface the Neurodivergence & ADHD section of Learn more readily (see
     * MentalHealthScreen.kt). Empty string means "skipped / prefer not to say," not "none."
     */
    val neurodivergentTags: String,
    /** Comma-joined ids from MOTIVATION_OPTIONS (OnboardingScreen.kt) — why the user is here,
     *  e.g. "reduce_scrolling,focus". Empty string means skipped/prefer not to say, used to
     *  decide which Guide/Learn section to lead with, never to gate anything. */
    val usageMotivation: String = "",
    /** yyyyMMdd, optional — only used for the user's own Birthdays checklist reminder. */
    val birthday: String? = null,
    val onboardedAt: Long
)

/**
 * A real alarm, scheduled via AlarmManager (see AlarmScheduler.kt) so it fires even if the app
 * is fully closed or the phone is locked — unlike the web app's best-effort tab-open version.
 * days is a comma-joined set of 0(Sun)-6(Sat) to repeat on; empty means a one-off alarm that
 * disables itself after firing once.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val days: String = "",
    val enabled: Boolean = true
)

/**
 * A short recurring check-in — separate from the one-time Quick setup profile (ProfileEntity),
 * which only captures who the user is, not how they're doing lately. Shown again once
 * [CHECKIN_INTERVAL_DAYS] has passed since the last one; also always reachable manually from
 * Mental Health. Feeds the mood trend on Mental Health (alongside Journal/Cycle mood) and the
 * usage-aware Journal prompts (JournalInsights.kt) with a more direct, user-reported signal
 * than inferring everything from app-usage numbers alone.
 */
@Entity(tableName = "weekly_checkins")
data class WeeklyCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moodScore: Int, // 1-5, same scale as Journal/Cycle mood
    val stressScore: Int, // 1-5, 5 = most stressed
    /** Free text — "what's been taking up most of your time/energy lately". Optional. */
    val topTimeSink: String? = null,
    val note: String? = null,
    val createdAt: Long
)

const val CHECKIN_INTERVAL_DAYS = 7
