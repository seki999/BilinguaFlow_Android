package com.seki999.bilinguaflow

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.seki999.bilinguaflow.ui.SpeechScreen
import com.seki999.bilinguaflow.viewmodel.SpeechViewModel
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in end-to-end test; the host feeds the emulator microphone during the test. */
class RecognitionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun microphoneSpeechReachesTranscriptAndSurvivesStop() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("injectAudio") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var model: SpeechViewModel
        val store = ViewModelStore()
        compose.runOnUiThread {
            model = SpeechViewModel(context.applicationContext as Application,
                SavedStateHandle(mapOf("language_tag" to "en-US", "transcript" to "")))
            store.put("recognition-test", model)
        }
        try {
            compose.setContent { SpeechScreen(model) }
            compose.onNodeWithText("Start").performClick()
            android.util.Log.i("RecognitionScreenTest", "READY_FOR_AUDIO")
            compose.waitUntil(timeoutMillis = 30000) {
                model.uiState.value.transcript.contains("in the transcript", ignoreCase = true)
            }
            compose.onNodeWithText("Stop").performClick()
            val text = model.uiState.value.transcript
            android.util.Log.i("RecognitionScreenTest", "STOPPED_TRANSCRIPT: $text")
            assertTrue("First sentence missing: $text", text.contains("hello everyone", ignoreCase = true))
            assertTrue("Second sentence missing: $text", text.contains("continuous speech recognition", ignoreCase = true))
            assertTrue("Third sentence missing: $text", text.contains("in the transcript", ignoreCase = true))
            compose.onNodeWithText(text).assertIsDisplayed()
            File(context.cacheDir, "transcript-verified.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            compose.runOnUiThread { model.onStopClicked(); store.clear() }
        }
    }
}
