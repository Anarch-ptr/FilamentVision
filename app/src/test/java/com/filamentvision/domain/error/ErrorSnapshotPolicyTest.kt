package com.filamentvision.domain.error

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorSnapshotPolicyTest {
    @Test
    fun newEpisodeCapturesImmediately() {
        assertTrue(ErrorSnapshotPolicy.shouldCapture(isNewEpisode = true, lastSnapshotAt = null, now = 1_000L))
    }

    @Test
    fun activeEpisodeWaitsForFiveMinuteCooldown() {
        val first = 1_000L
        assertFalse(ErrorSnapshotPolicy.shouldCapture(false, first, first + 60_000L))
        assertFalse(ErrorSnapshotPolicy.shouldCapture(false, first, first + ErrorSnapshotPolicy.COOLDOWN_MS - 1L))
        assertTrue(ErrorSnapshotPolicy.shouldCapture(false, first, first + ErrorSnapshotPolicy.COOLDOWN_MS))
    }

    @Test
    fun newEpisodeAfterResolutionDoesNotInheritOldCooldown() {
        assertTrue(ErrorSnapshotPolicy.shouldCapture(true, 120_000L, 180_000L))
    }
}

