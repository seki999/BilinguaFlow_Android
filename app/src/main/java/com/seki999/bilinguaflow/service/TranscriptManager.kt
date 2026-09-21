package com.seki999.bilinguaflow.service

/** Keeps committed utterances and one replaceable live draft, without duplicating revisions. */
class TranscriptManager {
    private val entries = mutableListOf<String>()
    private var pendingText = ""

    /** The displayed transcript, including the current draft. */
    val fullText: String
        get() = (entries + listOf(pendingText).filter { it.isNotBlank() }).joinToString(separator = "\n\n")

    val isEmpty: Boolean
        get() = entries.isEmpty() && pendingText.isBlank()

    /**
     * Replaces the draft with [text], falling back to the draft for an empty final.
     * Appends the result unless it's blank or identical (case-insensitively)
     * to the immediately preceding entry. Returns whether it was actually appended.
     */
    fun appendFinalResult(text: String): Boolean {
        val trimmed = text.trim().ifBlank { pendingText }
        pendingText = ""
        if (trimmed.isEmpty()) return false
        val last = entries.lastOrNull()
        if (last != null && last.equals(trimmed, ignoreCase = true)) return false
        entries.add(trimmed)
        return true
    }

    fun updatePartial(text: String) {
        if (text.isNotBlank()) pendingText = text.trim()
    }

    fun commitPending(): Boolean = appendFinalResult("")

    fun clear() {
        pendingText = ""
        entries.clear()
    }

    /** Replaces the current transcript with [text] (e.g. restoring persisted/saved state). */
    fun restore(text: String) {
        pendingText = ""
        entries.clear()
        if (text.isNotBlank()) {
            entries.addAll(text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() })
        }
    }
}
