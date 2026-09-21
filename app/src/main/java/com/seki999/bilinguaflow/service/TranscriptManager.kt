package com.seki999.bilinguaflow.service

/**
 * Accumulates final speech-recognition results into a growing transcript. This is the last line
 * of defense against duplicate consecutive text — even if [RecognitionCandidateSelector] already
 * tried to avoid repeating the previous final result, this guarantees it never actually lands in
 * the transcript twice in a row.
 *
 * Pure Kotlin, no Android dependency, so it's unit-testable without an emulator/device.
 */
class TranscriptManager {
    private val entries = mutableListOf<String>()

    /** The full transcript so far, with a blank line between each recognized utterance. */
    val fullText: String
        get() = entries.joinToString(separator = "\n\n")

    val isEmpty: Boolean
        get() = entries.isEmpty()

    /**
     * Appends [text] as a new final result unless it's blank or identical (case-insensitively)
     * to the immediately preceding entry. Returns whether it was actually appended.
     */
    fun appendFinalResult(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val last = entries.lastOrNull()
        if (last != null && last.equals(trimmed, ignoreCase = true)) return false
        entries.add(trimmed)
        return true
    }

    fun clear() {
        entries.clear()
    }

    /** Replaces the current transcript with [text] (e.g. restoring persisted/saved state). */
    fun restore(text: String) {
        entries.clear()
        if (text.isNotBlank()) {
            entries.addAll(text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() })
        }
    }
}
