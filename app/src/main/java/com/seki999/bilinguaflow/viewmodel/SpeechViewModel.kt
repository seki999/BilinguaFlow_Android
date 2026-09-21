package com.seki999.bilinguaflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.seki999.bilinguaflow.model.LanguageOption
import com.seki999.bilinguaflow.model.LanguageOptions
import com.seki999.bilinguaflow.model.ListeningEvent
import com.seki999.bilinguaflow.model.ListeningState
import com.seki999.bilinguaflow.model.ListeningStateTransitions
import com.seki999.bilinguaflow.repository.PreferencesRepository
import com.seki999.bilinguaflow.service.AndroidSpeechRecognitionService
import com.seki999.bilinguaflow.service.InactivityTracker
import com.seki999.bilinguaflow.service.SpeechRecognitionListener
import com.seki999.bilinguaflow.service.SpeechRecognitionService
import com.seki999.bilinguaflow.service.TranscriptManager
import com.seki999.bilinguaflow.util.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the continuous listening session: forwards Start/Pause/Resume/Stop/Clear from the UI to
 * [SpeechRecognitionService], accumulates results via [TranscriptManager], and runs the 5-minute
 * [InactivityTracker] watchdog. Survives configuration changes as an Activity-scoped ViewModel;
 * [savedStateHandle] additionally restores the transcript and selected language after process
 * death.
 */
class SpeechViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val speechService: SpeechRecognitionService = AndroidSpeechRecognitionService(application)
) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository(application)
    private val transcriptManager = TranscriptManager()
    private val inactivityTracker = InactivityTracker()

    private val _uiState = MutableStateFlow(
        SpeechUiState(
            selectedLanguage = LanguageOptions.byTag(savedStateHandle[KEY_LANGUAGE_TAG]),
            transcript = savedStateHandle[KEY_TRANSCRIPT] ?: ""
        )
    )
    val uiState: StateFlow<SpeechUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SpeechUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SpeechUiEvent> = _events.asSharedFlow()

    private var inactivityJob: Job? = null

    private val recognitionListener = object : SpeechRecognitionListener {
        override fun onPartialResult(text: String) {
            if (text.isBlank()) return
            inactivityTracker.recordValidSpeech()
            transcriptManager.updatePartial(text)
            _uiState.update { it.copy(partialText = text, transcript = transcriptManager.fullText) }
        }

        override fun onFinalResult(text: String) {
            if (text.isNotBlank()) inactivityTracker.recordValidSpeech()
            if (transcriptManager.appendFinalResult(text)) {
                persistTranscript()
            }
            _uiState.update { it.copy(partialText = "", transcript = transcriptManager.fullText) }
        }

        override fun onRecoverableError(errorCode: Int) {
            Logger.d("Recoverable speech recognition error: $errorCode")
            commitPendingTranscript()
        }

        override fun onFatalError(message: String) {
            commitPendingTranscript()
            stopInactivityWatcher()
            _uiState.update {
                it.copy(
                    listeningState = ListeningStateTransitions.next(it.listeningState, ListeningEvent.FATAL_ERROR),
                    partialText = ""
                )
            }
            _events.tryEmit(SpeechUiEvent.FatalError(message))
        }
    }

    init {
        transcriptManager.restore(_uiState.value.transcript)
        speechService.setListener(recognitionListener)
        restorePersistedStateIfNeeded()
    }

    private fun restorePersistedStateIfNeeded() {
        viewModelScope.launch {
            if (savedStateHandle.get<String>(KEY_LANGUAGE_TAG) == null) {
                val tag = preferencesRepository.languageTag.first()
                _uiState.update { it.copy(selectedLanguage = LanguageOptions.byTag(tag)) }
                savedStateHandle[KEY_LANGUAGE_TAG] = tag
            }
            if (savedStateHandle.get<String>(KEY_TRANSCRIPT) == null) {
                val saved = preferencesRepository.savedTranscript.first()
                if (saved.isNotBlank() && transcriptManager.isEmpty &&
                    savedStateHandle.get<String>(KEY_TRANSCRIPT) == null) {
                    transcriptManager.restore(saved)
                    savedStateHandle[KEY_TRANSCRIPT] = saved
                    _uiState.update { it.copy(transcript = saved) }
                }
            }
        }
    }

    fun onLanguageSelected(option: LanguageOption) {
        _uiState.update { it.copy(selectedLanguage = option) }
        savedStateHandle[KEY_LANGUAGE_TAG] = option.tag
        viewModelScope.launch { preferencesRepository.saveLanguageTag(option.tag) }
    }

    fun onStartClicked() {
        val current = _uiState.value.listeningState
        if (current == ListeningState.LISTENING) return
        _uiState.update {
            it.copy(listeningState = ListeningStateTransitions.next(current, ListeningEvent.START), partialText = "")
        }
        startInactivityWatcher()
        speechService.start(_uiState.value.selectedLanguage.tag)
    }

    fun onPauseClicked() {
        val current = _uiState.value.listeningState
        if (current != ListeningState.LISTENING) return
        commitPendingTranscript()
        speechService.stop()
        stopInactivityWatcher()
        _uiState.update {
            it.copy(listeningState = ListeningStateTransitions.next(current, ListeningEvent.PAUSE), partialText = "")
        }
    }

    fun onResumeClicked() {
        val current = _uiState.value.listeningState
        if (current != ListeningState.PAUSED) return
        _uiState.update {
            it.copy(listeningState = ListeningStateTransitions.next(current, ListeningEvent.RESUME))
        }
        startInactivityWatcher()
        speechService.start(_uiState.value.selectedLanguage.tag)
    }

    fun onStopClicked() {
        val current = _uiState.value.listeningState
        if (current == ListeningState.STOPPED || current == ListeningState.IDLE) return
        commitPendingTranscript()
        speechService.stop()
        stopInactivityWatcher()
        _uiState.update {
            it.copy(listeningState = ListeningStateTransitions.next(current, ListeningEvent.STOP), partialText = "")
        }
    }

    fun onClearClicked() {
        val wasListening = _uiState.value.listeningState == ListeningState.LISTENING
        if (wasListening) speechService.stop()
        transcriptManager.clear()
        savedStateHandle[KEY_TRANSCRIPT] = ""
        _uiState.update { it.copy(transcript = "", partialText = "") }
        viewModelScope.launch { preferencesRepository.saveTranscript("") }
        if (wasListening) speechService.start(_uiState.value.selectedLanguage.tag)
    }

    private fun commitPendingTranscript() {
        if (transcriptManager.commitPending()) persistTranscript()
        _uiState.update { it.copy(partialText = "", transcript = transcriptManager.fullText) }
    }

    private fun persistTranscript() {
        val text = transcriptManager.fullText
        savedStateHandle[KEY_TRANSCRIPT] = text
        viewModelScope.launch { preferencesRepository.saveTranscript(text) }
    }

    private fun startInactivityWatcher() {
        inactivityJob?.cancel()
        inactivityTracker.start()
        inactivityJob = viewModelScope.launch {
            while (isActive) {
                delay(INACTIVITY_CHECK_INTERVAL_MS)
                if (inactivityTracker.hasTimedOut()) {
                    onInactivityTimeout()
                    break
                }
            }
        }
    }

    private fun stopInactivityWatcher() {
        inactivityJob?.cancel()
        inactivityJob = null
        inactivityTracker.stop()
    }

    private fun onInactivityTimeout() {
        val current = _uiState.value.listeningState
        commitPendingTranscript()
        speechService.stop()
        inactivityTracker.stop()
        inactivityJob = null
        _uiState.update {
            it.copy(
                listeningState = ListeningStateTransitions.next(current, ListeningEvent.INACTIVITY_TIMEOUT),
                partialText = ""
            )
        }
        _events.tryEmit(SpeechUiEvent.AutoStoppedInactivity)
    }

    override fun onCleared() {
        super.onCleared()
        inactivityJob?.cancel()
        speechService.destroy()
    }

    companion object {
        private const val KEY_LANGUAGE_TAG = "language_tag"
        private const val KEY_TRANSCRIPT = "transcript"
        private const val INACTIVITY_CHECK_INTERVAL_MS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpeechViewModel(
                    application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
