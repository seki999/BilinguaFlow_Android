package com.seki999.bilinguaflow.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener as AndroidRecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.seki999.bilinguaflow.R
import com.seki999.bilinguaflow.util.Logger

/**
 * [SpeechRecognitionService] backed by the on-device [android.speech.SpeechRecognizer].
 *
 * [android.speech.SpeechRecognizer] normally stops listening after a single utterance, so this
 * class drives its own restart loop: every [onResults]/[onError] callback schedules a fresh
 * `startListening()` call a short delay later (via a main-thread [Handler]), until [stop] is
 * called. Only one [SpeechRecognizer] instance is ever alive at a time, and any pending restart
 * is always cancelled before a new one is scheduled, which keeps the loop from racing itself into
 * `ERROR_RECOGNIZER_BUSY` or duplicate recognizers.
 *
 * Must be driven from the main thread (the same requirement [android.speech.SpeechRecognizer]
 * itself has).
 */
class AndroidSpeechRecognitionService(
    context: Context,
    private val candidateSelector: RecognitionCandidateSelector = RecognitionCandidateSelector()
) : SpeechRecognitionService {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechRecognitionListener? = null
    private var languageTag: String = "en-US"

    /** True between [start] and [stop]/[destroy] — gates whether a restart is ever scheduled. */
    private var sessionActive = false
    private var pendingRestart: Runnable? = null
    private var consecutiveBusyErrors = 0
    private var lastFinalResult: String? = null
    private var destroyed = false

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    override fun setListener(listener: SpeechRecognitionListener?) {
        this.listener = listener
    }

    override fun start(languageTag: String) {
        if (destroyed) return
        this.languageTag = languageTag
        sessionActive = true
        consecutiveBusyErrors = 0
        lastFinalResult = null
        cancelPendingRestart()
        startListeningInternal()
    }

    override fun stop() {
        sessionActive = false
        cancelPendingRestart()
        releaseRecognizer()
    }

    override fun destroy() {
        stop()
        listener = null
        destroyed = true
    }

    private fun releaseRecognizer() {
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.stopListening() }
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    private fun startListeningInternal() {
        if (!sessionActive) return

        if (!isAvailable()) {
            Logger.w("Speech recognition unavailable on this device")
            sessionActive = false
            listener?.onFatalError(appContext.getString(R.string.msg_stt_unavailable))
            return
        }

        releaseRecognizer()

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(createAndroidListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

        runCatching { newRecognizer.startListening(intent) }
            .onFailure { error ->
                Logger.w("startListening() failed, scheduling retry", error)
                scheduleRestart(BUSY_RESTART_DELAY_MS)
            }
    }

    private fun createAndroidListener(): AndroidRecognitionListener = object : AndroidRecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
            if (!text.isNullOrEmpty()) {
                listener?.onPartialResult(text)
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveBusyErrors = 0
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val text = candidateSelector.selectBest(candidates, confidences, lastFinalResult)

            if (text.isNotEmpty()) {
                lastFinalResult = text
                Logger.i("Final result selected from ${candidates.size} candidate(s))")
                listener?.onFinalResult(text)
            }

            scheduleRestart(RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    Logger.w("Speech recognition error: insufficient permissions")
                    sessionActive = false
                    listener?.onFatalError(appContext.getString(R.string.msg_mic_permission_required))
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    consecutiveBusyErrors = (consecutiveBusyErrors + 1).coerceAtMost(MAX_BUSY_BACKOFF_MULTIPLIER)
                    Logger.w("Speech recognizer busy, backing off (x$consecutiveBusyErrors)")
                    listener?.onRecoverableError(error)
                    scheduleRestart(BUSY_RESTART_DELAY_MS * consecutiveBusyErrors)
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Normal silence — do not surface as an error, just keep listening.
                    consecutiveBusyErrors = 0
                    scheduleRestart(RESTART_DELAY_MS)
                }

                SpeechRecognizer.ERROR_CLIENT -> {
                    // Usually a benign race from cancel()/destroy() during a restart.
                    consecutiveBusyErrors = 0
                    scheduleRestart(RESTART_DELAY_MS)
                }

                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    Logger.w("Recoverable speech recognition error: $error")
                    consecutiveBusyErrors = 0
                    listener?.onRecoverableError(error)
                    scheduleRestart(RESTART_DELAY_MS)
                }

                else -> {
                    Logger.w("Unhandled speech recognition error: $error")
                    listener?.onRecoverableError(error)
                    scheduleRestart(RESTART_DELAY_MS)
                }
            }
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        cancelPendingRestart()
        if (!sessionActive) return
        val runnable = Runnable { startListeningInternal() }
        pendingRestart = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    companion object {
        private const val MAX_RESULTS = 5
        private const val RESTART_DELAY_MS = 300L
        private const val BUSY_RESTART_DELAY_MS = 800L
        private const val MAX_BUSY_BACKOFF_MULTIPLIER = 5
    }
}
