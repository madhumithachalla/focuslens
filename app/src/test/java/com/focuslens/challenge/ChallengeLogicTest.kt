package com.focuslens.challenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Not yet run: the Android module has no unit-test dependencies wired yet, and this
// environment has no Android SDK. Run with `./gradlew :app:testDebugUnitTest` once that is set up.
class ChallengeLogicTest {

    private val t0 = 1_000_000L
    private val hour = 60L * 60_000L
    private val whatsapp = "com.whatsapp"
    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val dialer = "com.google.android.dialer"

    private fun noBlue() = ChallengeLogic.start(
        ChallengeLogic.settingsFor(ChallengeMode.NO_BLUE_20H, emptySet(), setOf(whatsapp, dialer)),
        t0,
    )

    private fun semiBlue() = ChallengeLogic.start(
        ChallengeLogic.settingsFor(ChallengeMode.SEMI_BLUE_DAY, setOf(instagram, tiktok), setOf(whatsapp, dialer)),
        t0,
    )

    @Test
    fun noBlueDurationIsTwentyHours() {
        assertEquals(20 * hour, noBlue().settings.durationMs)
        assertEquals(20 * hour, ChallengeLogic.remainingMs(noBlue(), t0))
    }

    @Test
    fun noBlueBlocksEverythingExceptEssentialsAndFocusLens() {
        val s = noBlue()
        assertTrue(ChallengeLogic.shouldBlock(s, instagram, t0 + hour))
        assertFalse(ChallengeLogic.shouldBlock(s, whatsapp, t0 + hour))
        assertFalse(ChallengeLogic.shouldBlock(s, dialer, t0 + hour))
        assertFalse(ChallengeLogic.shouldBlock(s, ChallengeLogic.OWN_PACKAGE, t0 + hour))
    }

    @Test
    fun semiBlueBlocksOnlyChosenSocialApps() {
        val s = semiBlue()
        assertTrue(ChallengeLogic.shouldBlock(s, instagram, t0 + hour))
        assertTrue(ChallengeLogic.shouldBlock(s, tiktok, t0 + hour))
        assertFalse(ChallengeLogic.shouldBlock(s, whatsapp, t0 + hour))
        assertFalse(ChallengeLogic.shouldBlock(s, "com.example.notes", t0 + hour))
    }

    @Test
    fun semiBlueIgnoresSocialListForNoBlue() {
        val settings = ChallengeLogic.settingsFor(ChallengeMode.NO_BLUE_20H, setOf(instagram), emptySet())
        assertTrue(settings.socialApps.isEmpty())
    }

    @Test
    fun pausePreventsBlockingAndExcludesPausedTime() {
        val paused = ChallengeLogic.pause(noBlue(), t0 + hour)
        assertFalse(ChallengeLogic.shouldBlock(paused, instagram, t0 + 2 * hour))
        val resumed = ChallengeLogic.resume(paused, t0 + 3 * hour)
        // 1 hour ran before the pause, 2 hours paused, so at 3h only 1h counts.
        assertEquals(hour, ChallengeLogic.elapsedMs(resumed, t0 + 3 * hour))
        assertTrue(ChallengeLogic.shouldBlock(resumed, instagram, t0 + 3 * hour))
    }

    @Test
    fun endingEarlyStopsBlockingWithoutPenalty() {
        val ended = ChallengeLogic.end(noBlue(), t0 + hour)
        assertFalse(ChallengeLogic.shouldBlock(ended, instagram, t0 + 2 * hour))
        assertFalse(ChallengeLogic.isComplete(ended, t0 + 2 * hour))
        assertFalse(ChallengeLogic.isRunning(ended, t0 + 2 * hour))
    }

    @Test
    fun completingTheFullTimeIsCompletion() {
        val s = semiBlue()
        val done = t0 + ChallengeLogic.SEMI_BLUE_MS
        assertTrue(ChallengeLogic.isComplete(s, done))
        assertFalse(ChallengeLogic.shouldBlock(s, instagram, done))
    }

    @Test
    fun emergencyOverrideTurnsBlockingOffForFiveMinutes() {
        val s = ChallengeLogic.useEmergencyOverride(noBlue(), t0 + hour)
        assertFalse(ChallengeLogic.shouldBlock(s, instagram, t0 + hour + 60_000L))
        assertTrue(ChallengeLogic.shouldBlock(s, instagram, t0 + hour + ChallengeLogic.EMERGENCY_WINDOW_MS))
    }

    @Test
    fun pauseAndEndAreIdempotent() {
        val s = noBlue()
        val ended = ChallengeLogic.end(s, t0 + hour)
        assertEquals(ended, ChallengeLogic.end(ended, t0 + 2 * hour))
        val p = ChallengeLogic.pause(s, t0 + hour)
        assertEquals(p, ChallengeLogic.pause(p, t0 + 2 * hour))
    }
}
