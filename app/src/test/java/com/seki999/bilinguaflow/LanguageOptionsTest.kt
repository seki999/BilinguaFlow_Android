package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.model.LanguageOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageOptionsTest {

    @Test
    fun `contains all nine required languages`() {
        val tags = LanguageOptions.ALL.map { it.tag }
        assertEquals(
            listOf("en-US", "en-GB", "ja-JP", "zh-CN", "zh-TW", "ko-KR", "fr-FR", "de-DE", "es-ES"),
            tags
        )
    }

    @Test
    fun `byTag finds the matching option`() {
        val option = LanguageOptions.byTag("ja-JP")
        assertEquals("Japanese", option.displayName)
    }

    @Test
    fun `byTag falls back to the default for an unknown tag`() {
        assertEquals(LanguageOptions.DEFAULT, LanguageOptions.byTag("xx-XX"))
    }

    @Test
    fun `byTag falls back to the default for null`() {
        assertEquals(LanguageOptions.DEFAULT, LanguageOptions.byTag(null))
    }

    @Test
    fun `default is English (US)`() {
        assertEquals("en-US", LanguageOptions.DEFAULT.tag)
        assertTrue(LanguageOptions.ALL.contains(LanguageOptions.DEFAULT))
    }
}
