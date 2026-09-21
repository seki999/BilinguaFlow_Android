package com.seki999.bilinguaflow

import com.seki999.bilinguaflow.service.TranscriptManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptManagerTest {

    @Test
    fun `appends final results in order separated by a blank line`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Hello everyone.")
        manager.appendFinalResult("Today I want to talk about our new project.")
        assertEquals("Hello everyone.\n\nToday I want to talk about our new project.", manager.fullText)
    }

    @Test
    fun `blank text is not appended`() {
        val manager = TranscriptManager()
        assertFalse(manager.appendFinalResult(""))
        assertFalse(manager.appendFinalResult("   "))
        assertTrue(manager.isEmpty)
    }

    @Test
    fun `identical consecutive final result is rejected`() {
        val manager = TranscriptManager()
        assertTrue(manager.appendFinalResult("The meeting starts at 10 AM."))
        assertFalse(manager.appendFinalResult("The meeting starts at 10 AM."))
        assertEquals("The meeting starts at 10 AM.", manager.fullText)
    }

    @Test
    fun `duplicate rejection is case-insensitive`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Hello there")
        assertFalse(manager.appendFinalResult("hello there"))
    }

    @Test
    fun `same text is allowed again once a different entry is appended in between`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Hello")
        manager.appendFinalResult("World")
        assertTrue(manager.appendFinalResult("Hello"))
        assertEquals("Hello\n\nWorld\n\nHello", manager.fullText)
    }

    @Test
    fun `clear empties the transcript`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Some text")
        manager.clear()
        assertTrue(manager.isEmpty)
        assertEquals("", manager.fullText)
    }

    @Test
    fun `restore replaces the transcript from a persisted string`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("stale entry")
        manager.restore("Hello everyone.\n\nToday I want to talk about our new project.")
        assertEquals("Hello everyone.\n\nToday I want to talk about our new project.", manager.fullText)
    }
}
