package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.model.TranslationLanguageOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationLanguageOptionsTest {

    @Test
    fun `contains the seven supported target languages`() {
        val tags = TranslationLanguageOptions.ALL.map { it.tag }
        assertEquals(listOf("en", "ja", "zh", "ko", "fr", "de", "es"), tags)
    }

    @Test
    fun `byTag finds the matching option`() {
        assertEquals("Japanese", TranslationLanguageOptions.byTag("ja").displayName)
    }

    @Test
    fun `byTag falls back to the default for an unknown tag`() {
        assertEquals(TranslationLanguageOptions.DEFAULT, TranslationLanguageOptions.byTag("xx"))
    }

    @Test
    fun `byTag falls back to the default for null`() {
        assertEquals(TranslationLanguageOptions.DEFAULT, TranslationLanguageOptions.byTag(null))
    }

    @Test
    fun `default is English`() {
        assertEquals("en", TranslationLanguageOptions.DEFAULT.tag)
        assertTrue(TranslationLanguageOptions.ALL.contains(TranslationLanguageOptions.DEFAULT))
    }
}
