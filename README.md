# BilinguaFlow

A simple, lightweight Android app that continuously converts spoken language into text using the
device's built-in speech recognizer, with an optional live translation panel powered by Android's
on-device translation framework. No cloud AI APIs, no API keys, no external ML SDKs.

## What it does

1. Pick a recognition language from the dropdown (English, Japanese, Chinese, Korean, French,
   German, Spanish, with region variants where relevant).
2. Tap **Start**. The app listens continuously — it restarts the recognizer automatically after
   every pause or silence, so you don't have to keep tapping a mic button.
3. Recognized speech accumulates in **Original Text** as you talk.
4. Optionally pick a target language under **Translate to** — each finished sentence is
   automatically translated and appended to **Translated Text**, using the phone's own on-device
   translation service (Android 12+), not a network call.

## Tech stack

- Kotlin, Jetpack Compose, MVVM
- `android.speech.SpeechRecognizer` for continuous on-device/system speech recognition
- `android.view.translation.TranslationManager` for on-device translation (API 31+, degrades
  gracefully — the feature is simply unavailable on older devices or devices without a system
  translation provider)
- Jetpack DataStore Preferences for persisted settings (selected languages, last transcript)
- Kotlin Coroutines throughout

Deliberately **not** used: Whisper, cloud speech/translation APIs, ML Kit, login, ads, or any
third-party network dependency.

## Project structure

```
app/src/main/java/com/seki999/bilinguaflow/
├─ model/            LanguageOption, TranslationLanguageOption, ListeningState (+ transitions)
├─ service/           AndroidSpeechRecognitionService, RecognitionCandidateSelector,
│                     TranscriptManager, InactivityTracker, OnDeviceTranslationService
├─ repository/        PreferencesRepository (DataStore)
├─ viewmodel/         SpeechViewModel, SpeechUiState
├─ ui/                SpeechScreen + components/ (selectors, status indicator, text panels)
├─ MainActivity.kt
└─ BilinguaFlowApplication.kt
```

## How continuous recognition works

`android.speech.SpeechRecognizer` normally stops after one utterance. `AndroidSpeechRecognitionService`
reuses a single recognizer instance and calls `startListening()` again after every result or
recoverable error, so listening continues without visible gaps. Every restart is tagged with a
monotonically increasing "generation" number — `start()`/`stop()` bump it — so a stray callback
from an old, already-stopped session can never resurrect a restart loop after the user hits Stop.

Recoverable errors (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_RECOGNIZER_BUSY`, `ERROR_CLIENT`,
network/audio/server errors) restart automatically with backoff where appropriate;
`ERROR_INSUFFICIENT_PERMISSIONS` and "recognition unavailable" stop the session and surface a
message.

Up to 5 recognition candidates are requested per utterance; `RecognitionCandidateSelector` picks
the best one using confidence scores when available, falling back to the engine's own top guess
otherwise. `TranscriptManager` then commits it — a live "draft" (from partial results) is only
cleared once a committed result fully covers it, so speech that was already shown on screen is
never silently dropped even if a later final result comes back shorter.

## Building

```bash
./gradlew assembleDebug
```

Requires the Android SDK (`local.properties` with `sdk.dir=...`) and JDK 17. Debug APK lands at
`app/build/outputs/apk/debug/app-debug.apk`.

## Testing

```bash
./gradlew testDebugUnitTest lintDebug
```

Unit tests cover the pure-logic pieces (candidate selection, transcript accumulation,
inactivity timeout, language options, listening-state transitions) without needing a device or
emulator. There's also an opt-in instrumented test
(`app/src/androidTest/.../RecognitionAudioTest.kt`) that feeds a recorded PCM sample directly into
`SpeechRecognizer` via `EXTRA_AUDIO_SOURCE` to verify recognition end-to-end without a live
microphone — pass the sample with `-e audioFile /data/local/tmp/recognition-sample.pcm`.

## Known limitations

- On-device translation requires Android 12+ *and* a system translation provider (commonly
  Google's, on devices with Google apps) with the relevant language pack downloaded. When
  unavailable, the Translated Text panel says so instead of staying silently empty.
- Translation runs per committed sentence, not per keystroke of partial text, so it trails
  slightly behind the live Original Text.
