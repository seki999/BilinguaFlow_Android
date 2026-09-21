package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.service.RecognitionCandidateSelector
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionCandidateSelectorTest {

    private val selector = RecognitionCandidateSelector()

    @Test
    fun `empty candidate list returns empty string`() {
        assertEquals("", selector.selectBest(emptyList()))
    }

    @Test
    fun `all blank candidates returns empty string`() {
        assertEquals("", selector.selectBest(listOf("", "   ", "\n")))
    }

    @Test
    fun `single candidate is returned trimmed`() {
        assertEquals("hello there", selector.selectBest(listOf("  hello there  ")))
    }

    @Test
    fun `picks the candidate with the highest confidence`() {
        val candidates = listOf("hello", "hello world", "hallo")
        val confidences = floatArrayOf(0.4f, 0.9f, 0.2f)
        assertEquals("hello world", selector.selectBest(candidates, confidences))
    }

    @Test
    fun `falls back to Android's own top guess when no confidence is available`() {
        val candidates = listOf("short", "a much longer candidate text")
        assertEquals("short", selector.selectBest(candidates, null))
    }

    @Test
    fun `falls back to top guess when confidence array has only unknown values`() {
        val candidates = listOf("short", "a much longer candidate text")
        val confidences = floatArrayOf(-1f, -1f)
        assertEquals("short", selector.selectBest(candidates, confidences))
    }

    @Test
    fun `blank candidates are filtered out before ranking`() {
        val candidates = listOf("", "real result", "   ")
        assertEquals("real result", selector.selectBest(candidates))
    }

    @Test
    fun `duplicate candidates are merged keeping the higher confidence`() {
        val candidates = listOf("Hello there", "hello there", "goodbye")
        val confidences = floatArrayOf(0.3f, 0.95f, 0.5f)
        assertEquals("hello there", selector.selectBest(candidates, confidences))
    }

    @Test
    fun `avoids repeating the previous final result when a distinct alternative exists`() {
        val candidates = listOf("same as before", "a different sentence")
        val confidences = floatArrayOf(0.9f, 0.8f)
        val result = selector.selectBest(candidates, confidences, previousFinalResult = "same as before")
        assertEquals("a different sentence", result)
    }

    @Test
    fun `returns the only candidate even if it repeats the previous final result`() {
        val result = selector.selectBest(listOf("same as before"), null, previousFinalResult = "same as before")
        assertEquals("same as before", result)
    }

    @Test
    fun `previous final result comparison is case-insensitive`() {
        val candidates = listOf("Same As Before", "a different sentence")
        val confidences = floatArrayOf(0.9f, 0.1f)
        val result = selector.selectBest(candidates, confidences, previousFinalResult = "same as before")
        assertEquals("a different sentence", result)
    }
}
