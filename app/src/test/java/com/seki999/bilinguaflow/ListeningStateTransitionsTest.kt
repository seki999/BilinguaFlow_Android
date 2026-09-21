package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.model.ListeningEvent
import com.seki999.bilinguaflow.model.ListeningState
import com.seki999.bilinguaflow.model.ListeningStateTransitions
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningStateTransitionsTest {

    @Test
    fun `start moves any state to listening`() {
        for (state in ListeningState.entries) {
            assertEquals(ListeningState.LISTENING, ListeningStateTransitions.next(state, ListeningEvent.START))
        }
    }

    @Test
    fun `pause only takes effect while listening`() {
        assertEquals(
            ListeningState.PAUSED,
            ListeningStateTransitions.next(ListeningState.LISTENING, ListeningEvent.PAUSE)
        )
        assertEquals(
            ListeningState.IDLE,
            ListeningStateTransitions.next(ListeningState.IDLE, ListeningEvent.PAUSE)
        )
        assertEquals(
            ListeningState.STOPPED,
            ListeningStateTransitions.next(ListeningState.STOPPED, ListeningEvent.PAUSE)
        )
    }

    @Test
    fun `resume only takes effect while paused`() {
        assertEquals(
            ListeningState.LISTENING,
            ListeningStateTransitions.next(ListeningState.PAUSED, ListeningEvent.RESUME)
        )
        assertEquals(
            ListeningState.IDLE,
            ListeningStateTransitions.next(ListeningState.IDLE, ListeningEvent.RESUME)
        )
    }

    @Test
    fun `stop moves any state to stopped`() {
        for (state in ListeningState.entries) {
            assertEquals(ListeningState.STOPPED, ListeningStateTransitions.next(state, ListeningEvent.STOP))
        }
    }

    @Test
    fun `inactivity timeout only fires while listening`() {
        assertEquals(
            ListeningState.STOPPED,
            ListeningStateTransitions.next(ListeningState.LISTENING, ListeningEvent.INACTIVITY_TIMEOUT)
        )
        assertEquals(
            ListeningState.PAUSED,
            ListeningStateTransitions.next(ListeningState.PAUSED, ListeningEvent.INACTIVITY_TIMEOUT)
        )
    }

    @Test
    fun `fatal error always moves to error state`() {
        for (state in ListeningState.entries) {
            assertEquals(ListeningState.ERROR, ListeningStateTransitions.next(state, ListeningEvent.FATAL_ERROR))
        }
    }

    @Test
    fun `recoverable error never changes state`() {
        for (state in ListeningState.entries) {
            assertEquals(state, ListeningStateTransitions.next(state, ListeningEvent.RECOVERABLE_ERROR))
        }
    }
}
