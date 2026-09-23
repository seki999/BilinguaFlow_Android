package com.seki999.bilinguaflow.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
 * called.
 *
 * The same [SpeechRecognizer] instance is reused across restarts within one continuous session —
 * calling `startListening()` again after `onResults`/`onError` on the same instance is the
 * documented way to keep listening, and destroying+recreating it every cycle (as an earlier
 * version of this class did) both adds needless service-rebind latency and risks tearing down a
 * connection mid-bind, which showed up as spurious `SpeechRecognizer: not connected to the
 * recognition service` failures. The instance is only torn down and rebuilt on [stop]/[destroy],
 * or as a recovery step after [SpeechRecognizer.ERROR_CLIENT] / repeated
 * [SpeechRecognizer.ERROR_RECOGNIZER_BUSY], where the client-side state is no longer trustworthy.
 *
 * Every restart chain (the scheduled [Runnable] and the [AndroidRecognitionListener] callbacks
 * tied to it) is tagged with the [generation] active when [start] was called; both [start] and
 * [stop] bump [generation], which makes any still in-flight callback or timer from an earlier
 * chain inert instead of letting it schedule its own independent restart.
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
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechRecognitionListener? = null
    private var languageTag: String = "en-US"

    /** True between [start] and [stop]/[destroy] — gates whether a restart is ever scheduled. */
    private var sessionActive = false

    /** Bumped on every [start]/[stop]; invalidates any earlier, still in-flight restart chain. */
    private var generation = 0
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
        Logger.i("start() language=$languageTag")
        if (sessionActive) stop()
        this.languageTag = languageTag
        sessionActive = true
        generation++
        consecutiveBusyErrors = 0
        lastFinalResult = null
        cancelPendingRestart()
        muteRecognitionSounds()
        createRecognizerIfNeeded(generation)
        startListeningInternal(generation)
    }

    override fun stop() {
        Logger.i("stop()")
        sessionActive = false
        generation++
        cancelPendingRestart()
        teardownRecognizer()
        unmuteRecognitionSounds()
    }

    /**
     * The recognizer plays a start/end beep (via [AudioManager.STREAM_MUSIC]) on every
     * `startListening()` call — with the restart loop calling it after every utterance, that's a
     * near-constant beeping during continuous dictation. Muting that stream for the whole listening
     * session (restored on [stop]) is the standard workaround, since there's no public API to
     * disable the recognizer's own sound effects.
     */
    private fun muteRecognitionSounds() {
        runCatching { audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
    }

    private fun unmuteRecognitionSounds() {
        runCatching { audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0) }
    }

    override fun destroy() {
        Logger.i("destroy()")
        stop()
        listener = null
        destroyed = true
    }

    private fun teardownRecognizer() {
        val r = recognizer ?: return
        recognizer = null
        Logger.d("Destroying SpeechRecognizer instance")
        runCatching { r.stopListening() }
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    private fun createRecognizerIfNeeded(myGeneration: Int) {
        if (recognizer != null) return

        if (!isAvailable()) {
            Logger.w("Speech recognition unavailable on this device")
            sessionActive = false
            listener?.onFatalError(appContext.getString(R.string.msg_stt_unavailable))
            return
        }

        Logger.d("Creating SpeechRecognizer instance (generation=$myGeneration)")
        val r = SpeechRecognizer.createSpeechRecognizer(appContext)
        r.setRecognitionListener(createAndroidListener(myGeneration))
        recognizer = r
    }

    private fun recreateRecognizer(myGeneration: Int) {
        teardownRecognizer()
        createRecognizerIfNeeded(myGeneration)
    }

    private fun startListeningInternal(myGeneration: Int) {
        if (!sessionActive || myGeneration != generation) return

        createRecognizerIfNeeded(myGeneration)
        val active = recognizer ?: return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // A brief mid-sentence pause otherwise trips the recognizer's endpointer, splitting one
            // utterance into two final results (e.g. "can you hear" / "me" as separate entries).
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_LENGTH_MS)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                COMPLETE_SILENCE_LENGTH_MS
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_LENGTH_MS)
        }

        Logger.d("startListening() language=$languageTag generation=$myGeneration")
        runCatching { active.startListening(intent) }
            .onFailure { error ->
                Logger.w("startListening() threw, recreating recognizer and retrying", error)
                recreateRecognizer(myGeneration)
                scheduleRestart(myGeneration, BUSY_RESTART_DELAY_MS)
            }
    }

    private fun createAndroidListener(myGeneration: Int): AndroidRecognitionListener = object : AndroidRecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Logger.d("onReadyForSpeech generation=$myGeneration")
        }

        override fun onBeginningOfSpeech() {
            Logger.d("onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Logger.d("onEndOfSpeech")
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            if (myGeneration != generation) return
            val candidates = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
            Logger.d("onPartialResults candidates=$candidates")
            val text = candidates.firstOrNull { it.isNotBlank() }?.trim()
            if (!text.isNullOrEmpty()) {
                listener?.onPartialResult(text)
            }
        }

        override fun onResults(results: Bundle?) {
            if (myGeneration != generation) return
            consecutiveBusyErrors = 0
            val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            Logger.i(
                "onResults candidates=$candidates " +
                    "confidences=${confidences?.joinToString(prefix = "[", postfix = "]") ?: "null"}"
            )
            val text = candidateSelector.selectBest(candidates, confidences, lastFinalResult)

            if (text.isNotEmpty()) {
                lastFinalResult = text
                Logger.i("Final result selected: \"$text\"")
                listener?.onFinalResult(text)
            } else {
                listener?.onFinalResult("")
            }

            scheduleRestart(myGeneration, RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            if (myGeneration != generation) return
            Logger.w("onError code=$error (${errorName(error)})")
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    sessionActive = false
                    listener?.onFatalError(appContext.getString(R.string.msg_mic_permission_required))
                }

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    consecutiveBusyErrors = (consecutiveBusyErrors + 1).coerceAtMost(MAX_BUSY_BACKOFF_MULTIPLIER)
                    listener?.onRecoverableError(error)
                    // A busy recognizer's client-side state is often stuck; a fresh instance recovers more reliably.
                    recreateRecognizer(myGeneration)
                    scheduleRestart(myGeneration, BUSY_RESTART_DELAY_MS * consecutiveBusyErrors)
                }

                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    listener?.onRecoverableError(error)
                    // Keep listening on the same instance after silence.
                    consecutiveBusyErrors = 0
                    scheduleRestart(myGeneration, RESTART_DELAY_MS)
                }

                SpeechRecognizer.ERROR_CLIENT -> {
                    listener?.onRecoverableError(error)
                    // The client-side recognizer state is unreliable after this error; recreate it.
                    consecutiveBusyErrors = 0
                    recreateRecognizer(myGeneration)
                    scheduleRestart(myGeneration, RESTART_DELAY_MS)
                }

                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    consecutiveBusyErrors = 0
                    listener?.onRecoverableError(error)
                    scheduleRestart(myGeneration, RESTART_DELAY_MS)
                }

                else -> {
                    listener?.onRecoverableError(error)
                    scheduleRestart(myGeneration, RESTART_DELAY_MS)
                }
            }
        }
    }

    private fun scheduleRestart(myGeneration: Int, delayMs: Long) {
        if (myGeneration != generation) return
        cancelPendingRestart()
        if (!sessionActive) return
        val runnable = Runnable { startListeningInternal(myGeneration) }
        pendingRestart = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        else -> "UNKNOWN_ERROR_$error"
    }

    companion object {
        private const val MAX_RESULTS = 5
        private const val RESTART_DELAY_MS = 80L
        private const val BUSY_RESTART_DELAY_MS = 800L
        private const val MAX_BUSY_BACKOFF_MULTIPLIER = 5
        private const val COMPLETE_SILENCE_LENGTH_MS = 3_000L
        private const val MINIMUM_LENGTH_MS = 15_000L
    }
}
