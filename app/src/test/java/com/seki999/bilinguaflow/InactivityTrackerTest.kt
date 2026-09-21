package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.service.InactivityTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactivityTrackerTest {

    private class FakeClock(var now: Long = 0L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun `has not timed out before start is called`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        clock.now = 5000L
        assertFalse(tracker.hasTimedOut())
        assertFalse(tracker.isRunning())
    }

    @Test
    fun `times out once the timeout window elapses`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        tracker.start()
        clock.now += 999L
        assertFalse(tracker.hasTimedOut())
        clock.now += 1L
        assertTrue(tracker.hasTimedOut())
    }

    @Test
    fun `recording valid speech resets the timeout window`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        tracker.start()
        clock.now += 900L
        tracker.recordValidSpeech()
        clock.now += 900L
        assertFalse(tracker.hasTimedOut())
        clock.now += 200L
        assertTrue(tracker.hasTimedOut())
    }

    @Test
    fun `stop clears the running state and timeout`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        tracker.start()
        clock.now += 2000L
        assertTrue(tracker.hasTimedOut())
        tracker.stop()
        assertFalse(tracker.isRunning())
        assertFalse(tracker.hasTimedOut())
    }

    @Test
    fun `recording valid speech while not running does nothing`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        tracker.recordValidSpeech()
        assertFalse(tracker.isRunning())
    }

    @Test
    fun `restarting after stop begins a fresh window`() {
        val clock = FakeClock()
        val tracker = InactivityTracker(timeoutMillis = 1000L, elapsedRealtime = clock)
        tracker.start()
        clock.now += 1500L
        tracker.stop()
        tracker.start()
        assertFalse(tracker.hasTimedOut())
        clock.now += 999L
        assertFalse(tracker.hasTimedOut())
    }
}
