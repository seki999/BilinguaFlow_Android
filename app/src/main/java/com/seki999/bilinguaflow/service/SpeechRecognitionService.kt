package com.seki999.bilinguaflow.service

/**
 * Abstraction over continuous speech-to-text recognition. The first (and only) implementation,
 * [AndroidSpeechRecognitionService], wraps the on-device [android.speech.SpeechRecognizer] and
 * drives its own automatic restart loop so callers just see a stream of partial/final results
 * until [stop] is called.
 */
interface SpeechRecognitionService {

    /** Whether recognition is available on this device right now. */
    fun isAvailable(): Boolean

    /** Registers the callback that receives partial/final results and errors. Replaces any previous listener. */
    fun setListener(listener: SpeechRecognitionListener?)

    /**
     * Begins a new continuous listening session in [languageTag] (e.g. "en-US"). Internally
     * restarts the underlying recognizer after every result or recoverable error until [stop]
     * is called.
     */
    fun start(languageTag: String)

    /** Stops listening and cancels any pending automatic restart. Safe to call when already stopped. */
    fun stop()

    /** Releases all resources. The service cannot be [start]ed again after this. */
    fun destroy()
}

/** Callback for [SpeechRecognitionService]. All methods are invoked on the main thread. */
interface SpeechRecognitionListener {

    /** A temporary, in-progress recognition result. Never append this to a permanent transcript. */
    fun onPartialResult(text: String)

    /** A finished recognition result for one utterance, already chosen from up to 5 candidates. */
    fun onFinalResult(text: String)

    /** A transient error the service is already recovering from automatically (informational only). */
    fun onRecoverableError(errorCode: Int)

    /** An unrecoverable error (e.g. missing permission, recognizer unavailable). The session has stopped. */
    fun onFatalError(message: String)
}
