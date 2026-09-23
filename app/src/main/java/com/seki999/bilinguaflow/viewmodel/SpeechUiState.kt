package com.seki999.bilinguaflow.viewmodel

import com.seki999.bilinguaflow.model.LanguageOption
import com.seki999.bilinguaflow.model.LanguageOptions
import com.seki999.bilinguaflow.model.ListeningState
import com.seki999.bilinguaflow.model.TranslationLanguageOption
import com.seki999.bilinguaflow.model.TranslationLanguageOptions

/** Everything [com.seki999.bilinguaflow.ui.SpeechScreen] needs to render. */
data class SpeechUiState(
    val listeningState: ListeningState = ListeningState.IDLE,
    val selectedLanguage: LanguageOption = LanguageOptions.DEFAULT,
    val partialText: String = "",
    val transcript: String = "",
    val translationSupported: Boolean = false,
    val selectedTranslationLanguage: TranslationLanguageOption = TranslationLanguageOptions.DEFAULT,
    val translatedText: String = "",
    val translationMessage: String? = null
)

/** One-shot UI events (snackbars) that must not re-fire on recomposition/rotation. */
sealed interface SpeechUiEvent {
    data object AutoStoppedInactivity : SpeechUiEvent
    data class FatalError(val message: String) : SpeechUiEvent
}
