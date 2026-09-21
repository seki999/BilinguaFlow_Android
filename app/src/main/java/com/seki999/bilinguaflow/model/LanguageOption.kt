package com.seki999.bilinguaflow.model

/** One selectable recognition language: a human-readable [displayName] and its BCP-47 [tag]. */
data class LanguageOption(val displayName: String, val tag: String)

/** The fixed set of recognition languages BilinguaFlow supports in its first version. */
object LanguageOptions {

    val ALL = listOf(
        LanguageOption("English (US)", "en-US"),
        LanguageOption("English (UK)", "en-GB"),
        LanguageOption("Japanese", "ja-JP"),
        LanguageOption("Chinese (Simplified)", "zh-CN"),
        LanguageOption("Chinese (Traditional)", "zh-TW"),
        LanguageOption("Korean", "ko-KR"),
        LanguageOption("French", "fr-FR"),
        LanguageOption("German", "de-DE"),
        LanguageOption("Spanish", "es-ES")
    )

    val DEFAULT = ALL.first()

    /** Looks up an option by its BCP-47 [tag], falling back to [DEFAULT] when unknown or null. */
    fun byTag(tag: String?): LanguageOption = ALL.firstOrNull { it.tag == tag } ?: DEFAULT
}
