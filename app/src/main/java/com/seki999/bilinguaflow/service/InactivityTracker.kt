package com.seki999.bilinguaflow.service

import android.os.SystemClock

/**
 * Tracks how long the app has gone without a valid (non-empty) recognized final result while
 * actively listening, so [com.seki999.bilinguaflow.viewmodel.SpeechViewModel] can auto-stop after
 * [timeoutMillis] of silence.
 *
 * Only [recordValidSpeech] resets the clock — silence-related SpeechRecognizer events
 * (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, empty partial/final results, recognizer restarts)
 * must NOT call it.
 *
 * [elapsedRealtime] defaults to [SystemClock.elapsedRealtime] but is injectable so this class is
 * unit-testable on the plain JVM without Robolectric.
 */
class InactivityTracker(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime
) {
    private var lastSpeechInputTime: Long? = null

    /** Starts (or restarts) the timeout window from now. */
    fun start() {
        lastSpeechInputTime = elapsedRealtime()
    }

    /** Stops tracking; [hasTimedOut] returns false until [start] is called again. */
    fun stop() {
        lastSpeechInputTime = null
    }

    fun isRunning(): Boolean = lastSpeechInputTime != null

    /** Call only for a genuine non-empty recognized final result. Resets the timeout window. */
    fun recordValidSpeech() {
        if (lastSpeechInputTime != null) {
            lastSpeechInputTime = elapsedRealtime()
        }
    }

    fun hasTimedOut(): Boolean {
        val last = lastSpeechInputTime ?: return false
        return elapsedRealtime() - last >= timeoutMillis
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5 * 60 * 1000L
    }
}
