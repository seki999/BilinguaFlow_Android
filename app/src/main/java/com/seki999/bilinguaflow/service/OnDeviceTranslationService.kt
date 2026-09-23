package com.seki999.bilinguaflow.service

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.seki999.bilinguaflow.util.Logger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Wraps Google's ML Kit Translate (`com.google.mlkit:translate`) — a bundled on-device translation
 * SDK that downloads its own language models via Google Play services on first use, then translates
 * fully offline. Unlike Android's system `TranslationManager` API, this doesn't depend on the OEM
 * having shipped a system translation provider (most non-Pixel devices/ROMs never do), only on
 * Google Play services being present — true for the vast majority of Android devices.
 */
class OnDeviceTranslationService(context: Context) {

    private val appContext = context.applicationContext

    private var cachedTranslator: Translator? = null
    private var cachedSourceCode: String? = null
    private var cachedTargetCode: String? = null

    init {
        Logger.i("OnDeviceTranslationService init: playServicesAvailable=${isSupported()}")
    }

    fun isSupported(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext) == ConnectionResult.SUCCESS

    /** Translates [text] from [sourceLanguageTag] to [targetLanguageTag] (both BCP-47 tags). */
    suspend fun translate(text: String, sourceLanguageTag: String, targetLanguageTag: String): Result<String> {
        Logger.i("translate() source=$sourceLanguageTag target=$targetLanguageTag textLength=${text.length}")
        if (text.isBlank()) return Result.success("")
        if (!isSupported()) {
            return Result.failure(IllegalStateException("Google Play services is required for on-device translation."))
        }

        val sourceCode = toTranslateLanguageCode(sourceLanguageTag)
        val targetCode = toTranslateLanguageCode(targetLanguageTag)
        if (sourceCode == null || targetCode == null) {
            return Result.failure(
                IllegalArgumentException("Unsupported language pair for translation: $sourceLanguageTag -> $targetLanguageTag")
            )
        }

        val translator = runCatching { getOrCreateTranslator(sourceCode, targetCode) }
            .onFailure { Logger.w("getOrCreateTranslator failed", it) }
            .getOrElse {
                return Result.failure(
                    IllegalStateException(
                        "Couldn't prepare the on-device translation model ($sourceLanguageTag -> $targetLanguageTag). " +
                            "Check your network connection so the language pack can download."
                    )
                )
            }

        val translated = withTimeoutOrNull(TRANSLATE_TIMEOUT_MS) {
            runCatching { translator.translate(text).await() }
        }

        return when {
            translated == null -> Result.failure(
                IllegalStateException("Translation timed out — the on-device translator didn't respond.")
            )
            translated.isFailure -> Result.failure(
                translated.exceptionOrNull() ?: IllegalStateException("Translation failed.")
            )
            else -> {
                val result = translated.getOrThrow()
                Logger.i("translate() result=\"$result\"")
                Result.success(result)
            }
        }
    }

    /**
     * [TranslateLanguage.fromLanguageTag] only recognizes bare language subtags (e.g. "en", "zh"),
     * not full BCP-47 tags with a region ("en-US", "zh-CN") — [LanguageOptions][com.seki999.bilinguaflow.model.LanguageOptions]
     * carries the region since speech recognition needs it, so it's stripped here before lookup.
     */
    private fun toTranslateLanguageCode(bcp47Tag: String): String? =
        TranslateLanguage.fromLanguageTag(Locale.forLanguageTag(bcp47Tag).language)

    private suspend fun getOrCreateTranslator(sourceCode: String, targetCode: String): Translator {
        val cached = cachedTranslator
        if (cached != null && cachedSourceCode == sourceCode && cachedTargetCode == targetCode) {
            return cached
        }
        cachedTranslator?.close()
        cachedTranslator = null

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceCode)
            .setTargetLanguage(targetCode)
            .build()
        val translator = Translation.getClient(options)

        val downloadConditions = DownloadConditions.Builder().build()
        val downloaded = withTimeoutOrNull(DOWNLOAD_MODEL_TIMEOUT_MS) {
            runCatching { translator.downloadModelIfNeeded(downloadConditions).await() }
        }
        if (downloaded == null || downloaded.isFailure) {
            translator.close()
            throw downloaded?.exceptionOrNull()
                ?: IllegalStateException("Downloading the translation model timed out.")
        }

        cachedTranslator = translator
        cachedSourceCode = sourceCode
        cachedTargetCode = targetCode
        return translator
    }

    fun destroy() {
        cachedTranslator?.close()
        cachedTranslator = null
        cachedSourceCode = null
        cachedTargetCode = null
    }

    companion object {
        private const val DOWNLOAD_MODEL_TIMEOUT_MS = 60_000L
        private const val TRANSLATE_TIMEOUT_MS = 10_000L
    }
}
