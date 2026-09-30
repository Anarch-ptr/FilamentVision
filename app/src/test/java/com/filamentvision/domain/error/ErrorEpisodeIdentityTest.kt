package com.filamentvision.domain.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ErrorEpisodeIdentityTest {
    @Test
    fun identityDeduplicatesSameRootFault() {
        val first = NewErrorRecord.cameraFailure("A", sessionId = "session-1").identity
        val repeated = NewErrorRecord.cameraFailure("A", sessionId = "session-1").identity
        assertEquals(first, repeated)
    }

    @Test
    fun cameraAndSessionRemainPartOfIdentity() {
        val cameraA = NewErrorRecord.cameraFailure("A", sessionId = "session-1").identity
        val cameraB = NewErrorRecord.cameraFailure("B", sessionId = "session-1").identity
        val anotherSession = NewErrorRecord.cameraFailure("A", sessionId = "session-2").identity
        assertNotEquals(cameraA, cameraB)
        assertNotEquals(cameraA, anotherSession)
    }
}

