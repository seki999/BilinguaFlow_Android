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

    @Test
    fun `partial revisions replace the draft without duplicating it`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Previous sentence.")
        manager.updatePartial("Hello")
        manager.updatePartial("Hello world")
        assertEquals("Previous sentence.\n\nHello world", manager.fullText)
        manager.appendFinalResult("Hello, world!")
        assertEquals("Previous sentence.\n\nHello, world!", manager.fullText)
    }

    @Test
    fun `interrupted utterance is retained exactly once`() {
        val manager = TranscriptManager()
        manager.updatePartial("Still speaking")
        assertTrue(manager.commitPending())
        assertFalse(manager.commitPending())
        manager.updatePartial("Next sentence")
        assertEquals("Still speaking\n\nNext sentence", manager.fullText)
    }

    @Test
    fun `empty final falls back to latest nonblank partial`() {
        val manager = TranscriptManager()
        manager.updatePartial("Recognized words")
        manager.updatePartial("  ")
        assertTrue(manager.appendFinalResult(""))
        assertEquals("Recognized words", manager.fullText)
    }

    @Test
    fun `clear discards pending text as well as final text`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Old")
        manager.updatePartial("Pending")
        manager.clear()
        assertFalse(manager.commitPending())
        assertTrue(manager.isEmpty)
    }

    @Test
    fun `duplicate final also removes its pending draft`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Hello")
        manager.updatePartial("hello")
        assertFalse(manager.appendFinalResult("Hello"))
        assertEquals("Hello", manager.fullText)
    }

    @Test
    fun `restore discards previous draft`() {
        val manager = TranscriptManager()
        manager.updatePartial("Pending")
        manager.restore("Saved")
        assertFalse(manager.commitPending())
        assertEquals("Saved", manager.fullText)
    }

    @Test
    fun `a shorter final result does not discard the drafts extra tail`() {
        val manager = TranscriptManager()
        manager.updatePartial("hello everyone this is a test")
        manager.appendFinalResult("hello everyone")
        assertTrue(manager.fullText.contains("hello everyone"))
        assertTrue(manager.fullText.contains("this is a test"))
    }

    @Test
    fun `an unrelated final result does not wipe out a newer unrelated draft`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("Hello")
        manager.updatePartial("World")
        assertFalse(manager.appendFinalResult("Hello"))
        assertEquals("Hello\n\nWorld", manager.fullText)
    }

    @Test
    fun `a final result that only differs by punctuation still clears the draft`() {
        val manager = TranscriptManager()
        manager.updatePartial("Hello world")
        manager.appendFinalResult("Hello, world!")
        assertEquals("Hello, world!", manager.fullText)
    }

    @Test
    fun `lastEntry is null until something is committed`() {
        val manager = TranscriptManager()
        assertEquals(null, manager.lastEntry)
        manager.updatePartial("still just a draft")
        assertEquals(null, manager.lastEntry)
    }

    @Test
    fun `lastEntry reflects the most recently committed sentence`() {
        val manager = TranscriptManager()
        manager.appendFinalResult("First sentence.")
        assertEquals("First sentence.", manager.lastEntry)
        manager.appendFinalResult("Second sentence.")
        assertEquals("Second sentence.", manager.lastEntry)
    }
}
