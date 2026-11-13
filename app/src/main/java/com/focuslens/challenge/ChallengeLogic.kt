package com.focuslens.challenge

/**
 * Two optional, opt-in screen-break challenges.
 *
 * NO_BLUE_20H: a strict challenge. Every app is paused except the essential apps the user keeps.
 * SEMI_BLUE_DAY: a flexible day. Only the social apps the user picks are paused; essential apps stay open.
 *
 * Pure Kotlin, no Android imports, so the rules can be unit-tested on the JVM.
 * There is no streak, no score and no failure state. Ending early is always allowed and is not a penalty.
 */
enum class ChallengeMode { NO_BLUE_20H, SEMI_BLUE_DAY }

data class ChallengeSettings(
    val mode: ChallengeMode,
    /** How long the challenge runs, in milliseconds (20 hours, or one day). */
    val durationMs: Long,
    /** Semi-Blue only: the social app packages the user chose to pause. */
    val socialApps: Set<String>,
    /** Calls, transport, messaging and similar apps that stay open in both modes. */
    val essentialApps: Set<String>,
)

data class ChallengeState(
    val settings: ChallengeSettings,
    val startedAt: Long,
    /** Total time spent paused, excluding any pause still in progress. */
    val pausedMs: Long = 0L,
    /** Set while the challenge is paused; null when running. */
    val pausedAt: Long? = null,
    /** Set when the user ends the challenge early; null while it has not been ended. */
    val endedAt: Long? = null,
    /** While this is in the future, blocking is off (the emergency or essential-use override). */
    val emergencyUntil: Long = 0L,
)

object ChallengeLogic {
    /** Length of one emergency or essential-use override. */
    const val EMERGENCY_WINDOW_MS: Long = 5L * 60_000L

    /** Apps that FocusLens never blocks: its own package. */
    const val OWN_PACKAGE: String = "com.focuslens"

    const val NO_BLUE_MS: Long = 20L * 60L * 60_000L
    const val SEMI_BLUE_MS: Long = 24L * 60L * 60_000L

    fun settingsFor(mode: ChallengeMode, socialApps: Set<String>, essentialApps: Set<String>): ChallengeSettings =
        ChallengeSettings(
            mode = mode,
            durationMs = if (mode == ChallengeMode.NO_BLUE_20H) NO_BLUE_MS else SEMI_BLUE_MS,
            // In NoBlue every app is paused, so the social list is not used.
            socialApps = if (mode == ChallengeMode.SEMI_BLUE_DAY) socialApps else emptySet(),
            essentialApps = essentialApps,
        )

    fun start(settings: ChallengeSettings, now: Long): ChallengeState =
        ChallengeState(settings = settings, startedAt = now)

    /** Time the challenge has run, not counting paused time. */
    fun elapsedMs(state: ChallengeState, now: Long): Long {
        val end = state.endedAt ?: now
        val openPause = state.pausedAt?.let { (end - it).coerceAtLeast(0L) } ?: 0L
        return (end - state.startedAt - state.pausedMs - openPause).coerceAtLeast(0L)
    }

    fun remainingMs(state: ChallengeState, now: Long): Long =
        (state.settings.durationMs - elapsedMs(state, now)).coerceAtLeast(0L)

    /** Finishing the full time counts as completing the challenge. There is no failure state. */
    fun isComplete(state: ChallengeState, now: Long): Boolean =
        state.endedAt == null && remainingMs(state, now) == 0L

    fun isPaused(state: ChallengeState): Boolean = state.pausedAt != null && state.endedAt == null

    fun isRunning(state: ChallengeState, now: Long): Boolean =
        state.endedAt == null && !isPaused(state) && !isComplete(state, now)

    fun pause(state: ChallengeState, now: Long): ChallengeState =
        if (state.pausedAt != null || state.endedAt != null) state else state.copy(pausedAt = now)

    fun resume(state: ChallengeState, now: Long): ChallengeState {
        val since = state.pausedAt ?: return state
        return state.copy(
            pausedMs = state.pausedMs + (now - since).coerceAtLeast(0L),
            pausedAt = null,
        )
    }

    fun end(state: ChallengeState, now: Long): ChallengeState =
        if (state.endedAt != null) state else state.copy(endedAt = now)

    /** Emergency or essential-use override: blocking is off for [EMERGENCY_WINDOW_MS]. */
    fun useEmergencyOverride(state: ChallengeState, now: Long): ChallengeState =
        state.copy(emergencyUntil = now + EMERGENCY_WINDOW_MS)

    fun isEmergencyActive(state: ChallengeState, now: Long): Boolean = now < state.emergencyUntil

    /** Whether the challenge should pause [packageName] right now. */
    fun shouldBlock(state: ChallengeState, packageName: String, now: Long): Boolean {
        if (!isRunning(state, now)) return false
        if (isEmergencyActive(state, now)) return false
        if (packageName == OWN_PACKAGE) return false
        if (packageName in state.settings.essentialApps) return false
        return when (state.settings.mode) {
            ChallengeMode.NO_BLUE_20H -> true
            ChallengeMode.SEMI_BLUE_DAY -> packageName in state.settings.socialApps
        }
    }
}
