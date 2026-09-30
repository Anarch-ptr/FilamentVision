package com.filamentvision.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputStateControllerTest {
    @Test
    fun realInputLifecycleUsesOneConsistentStateAtATime() {
        val controller = InputStateController()

        assertTrue(controller.transitionTo(InputState.Connecting))
        assertTrue(controller.transitionTo(InputState.Connected))
        assertTrue(controller.transitionTo(InputState.WaitingForFrame))
        assertTrue(controller.transitionTo(InputState.Streaming))
        assertTrue(controller.transitionTo(InputState.Stopped))

        assertEquals(InputState.Stopped, controller.state.value)
    }

    @Test
    fun unconfiguredInputCannotPretendToBeStreaming() {
        val controller = InputStateController()

        assertFalse(controller.transitionTo(InputState.Streaming))

        assertEquals(InputState.Unconfigured, controller.state.value)
    }
}

