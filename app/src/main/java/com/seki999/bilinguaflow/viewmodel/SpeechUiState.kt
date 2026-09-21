package com.seki999.bilinguaflow.viewmodel

import com.seki999.bilinguaflow.model.LanguageOption
import com.seki999.bilinguaflow.model.LanguageOptions
import com.seki999.bilinguaflow.model.ListeningState

/** Everything [com.seki999.bilinguaflow.ui.SpeechScreen] needs to render. */
data class SpeechUiState(
    val listeningState: ListeningState = ListeningState.IDLE,
    val selectedLanguage: LanguageOption = LanguageOptions.DEFAULT,
    val partialText: String = "",
    val transcript: String = ""
)

/** One-shot UI events (snackbars) that must not re-fire on recomposition/rotation. */
sealed interface SpeechUiEvent {
    data object AutoStoppedInactivity : SpeechUiEvent
    data class FatalError(val message: String) : SpeechUiEvent
}
