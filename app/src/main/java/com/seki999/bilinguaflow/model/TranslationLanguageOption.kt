package com.seki999.bilinguaflow.model

/** One selectable translation target language: a human-readable [displayName] and its [tag]. */
data class TranslationLanguageOption(val displayName: String, val tag: String)

/**
 * Target languages offered for translation. Unlike [LanguageOptions] (used for speech
 * recognition), these are plain language tags without a region — the on-device translator cares
 * about the language, not the accent/dialect region that recognition needs.
 */
object TranslationLanguageOptions {

    val ALL = listOf(
        TranslationLanguageOption("English", "en"),
        TranslationLanguageOption("Japanese", "ja"),
        TranslationLanguageOption("Chinese (Simplified)", "zh"),
        TranslationLanguageOption("Korean", "ko"),
        TranslationLanguageOption("French", "fr"),
        TranslationLanguageOption("German", "de"),
        TranslationLanguageOption("Spanish", "es")
    )

    val DEFAULT = ALL.first()

    /** Looks up an option by its language [tag], falling back to [DEFAULT] when unknown or null. */
    fun byTag(tag: String?): TranslationLanguageOption = ALL.firstOrNull { it.tag == tag } ?: DEFAULT
}
