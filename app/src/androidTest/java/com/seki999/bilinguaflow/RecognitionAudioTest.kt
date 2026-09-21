package com.seki999.bilinguaflow

import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.seki999.bilinguaflow.service.TranscriptManager
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in real-provider test: pass -e audioFile /data/local/tmp/recognition-sample.pcm. */
class RecognitionAudioTest {
    @Test
    fun recognizesPcmAudio() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 33)
        val args = InstrumentationRegistry.getArguments()
        val path = args.getString("audioFile")
        assumeTrue("Requires 16 kHz mono signed 16-bit PCM fixture", path != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        val transcript = TranscriptManager()
        var error: Int? = null
        lateinit var recognizer: SpeechRecognizer
        val pipe = ParcelFileDescriptor.createPipe()
        val source = pipe[0]
        instrumentation.runOnMainSync {
            recognizer = SpeechRecognizer.createSpeechRecognizer(instrumentation.targetContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(value: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(type: Int, params: Bundle?) = Unit
                override fun onError(code: Int) { error = code; done.countDown() }
                override fun onPartialResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript.updatePartial(text)
                    Log.i("RecognitionAudioTest", "Partial: $text")
                }
                override fun onSegmentResults(results: Bundle) {
                    val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript.appendFinalResult(text)
                    Log.i("RecognitionAudioTest", "Segment: $text")
                }
                override fun onEndOfSegmentedSession() { done.countDown() }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    transcript.appendFinalResult(text)
                    Log.i("RecognitionAudioTest", "Transcript: ${transcript.fullText}")
                    done.countDown()
                }
            })
            recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
            })
        }
        val feeder = Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    val audio = ByteArray(16000) + File(path!!).readBytes() + ByteArray(64000)
                    var offset = 0
                    while (offset < audio.size) {
                        val count = minOf(640, audio.size - offset)
                        output.write(audio, offset, count)
                        offset += count
                        Thread.sleep(20)
                    }
                }
            }
        }.apply { start() }
        try {
            assertTrue("No final result in 30 seconds", done.await(30, TimeUnit.SECONDS))
            assertTrue("Recognition failed with code $error", error == null)
            for (phrase in listOf("hello everyone", "continuous speech recognition", "in the transcript")) {
                assertTrue("Missing $phrase in: ${transcript.fullText}", transcript.fullText.contains(phrase, ignoreCase = true))
            }
        } finally {
            instrumentation.runOnMainSync { recognizer.destroy() }
            source.close()
            feeder.interrupt()
            feeder.join(1000)
        }
    }
}
