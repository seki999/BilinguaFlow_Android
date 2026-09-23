package com.seki999.bilinguaflow.service

/**
 * Keeps committed utterances and one replaceable live draft.
 *
 * A live draft is never silently discarded. It's only cleared once its content is fully covered
 * by what was just committed (the same text, ignoring case/punctuation, or a superset of it). If
 * an incoming final result is shorter than, or unrelated to, the current draft — which happens
 * whenever the recognizer's final pass trims or revises what it had shown as a partial — the
 * draft is left in place so content already visible to the user never just vanishes.
 */
class TranscriptManager {
    private val entries = mutableListOf<String>()
    private var pendingText = ""

    /** The displayed transcript, including the current draft. */
    val fullText: String
        get() = (entries + listOf(pendingText).filter { it.isNotBlank() }).joinToString(separator = "\n\n")

    val isEmpty: Boolean
        get() = entries.isEmpty() && pendingText.isBlank()

    /** The most recently committed entry, if any — e.g. to feed a translator after each commit. */
    val lastEntry: String?
        get() = entries.lastOrNull()

    /**
     * Commits [text] as a new final result, falling back to the current draft when [text] is
     * blank. Returns whether a new entry was actually appended (false for blank/duplicate).
     */
    fun appendFinalResult(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return commitDraft()

        val added = addEntryIfNew(trimmed)
        if (isCoveredByCommit(trimmed)) {
            pendingText = ""
        }
        return added
    }

    fun updatePartial(text: String) {
        if (text.isNotBlank()) pendingText = text.trim()
    }

    fun commitPending(): Boolean = commitDraft()

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

    private fun commitDraft(): Boolean {
        val trimmed = pendingText.trim()
        pendingText = ""
        if (trimmed.isEmpty()) return false
        return addEntryIfNew(trimmed)
    }

    private fun addEntryIfNew(trimmed: String): Boolean {
        val last = entries.lastOrNull()
        if (last != null && last.equals(trimmed, ignoreCase = true)) return false
        entries.add(trimmed)
        return true
    }

    /** Whether [committedText] already says everything the current draft says (punctuation aside). */
    private fun isCoveredByCommit(committedText: String): Boolean {
        if (pendingText.isBlank()) return true
        val normalizedCommitted = normalize(committedText)
        val normalizedPending = normalize(pendingText)
        return normalizedCommitted == normalizedPending || normalizedCommitted.startsWith(normalizedPending)
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")
}
