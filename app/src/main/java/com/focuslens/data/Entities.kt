package com.focuslens.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskType { STUDY, ADMIN, WELLNESS }

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

enum class CycleMode { MENSTRUAL, GENERAL_ENERGY, OFF }

@Entity(tableName = "cycle_logs")
data class CycleLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: CycleMode,
    val date: String, // yyyyMMdd
    val energyLevel: Int? = null, // 1-5, used by both modes
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
    val onboardedAt: Long
)
