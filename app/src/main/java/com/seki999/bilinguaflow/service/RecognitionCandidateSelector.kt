package com.seki999.bilinguaflow.service

/**
 * Picks the best transcript out of up to 5 alternatives [android.speech.SpeechRecognizer] can
 * return for a single utterance (`RecognizerIntent.EXTRA_MAX_RESULTS`).
 *
 * Unlike EchoFluent's selector, BilinguaFlow has no known "expected text" to compare against —
 * this is general-purpose transcription, not pronunciation practice — so selection relies only
 * on the recognizer's own per-candidate confidence score (`SpeechRecognizer.CONFIDENCE_SCORES`),
 * falling back safely to the recognizer's own top-ranked guess when confidence is unavailable.
 *
 * Pure Kotlin, no Android dependency, so it's unit-testable without an emulator/device.
 */
class RecognitionCandidateSelector {

    /**
     * [candidates] should be in the recognizer's own returned order (best guess first) — used as
     * the fallback ordering when no confidence scores are available. [confidences], if provided,
     * must be the same length as [candidates]; entries outside `0f..1f` (some engines report -1
     * for "unknown") are treated as unknown for that candidate. [previousFinalResult], if given,
     * lets the selector prefer a distinct alternative when the top pick would otherwise repeat
     * the immediately preceding final result verbatim.
     */
    fun selectBest(
        candidates: List<String>,
        confidences: FloatArray? = null,
        previousFinalResult: String? = null
    ): String {
        if (candidates.isEmpty()) return ""

        val confidencesAvailable = confidences != null && confidences.any { it in 0f..1f }

        val entries = candidates
            .mapIndexed { index, raw -> Entry(raw.trim(), confidenceAt(confidences, index), index) }
            .filter { it.text.isNotEmpty() }

        if (entries.isEmpty()) return ""

        // Duplicate removal: keep one entry per distinct (case-insensitive) text, preferring
        // whichever occurrence has the higher confidence.
        val deduped = LinkedHashMap<String, Entry>()
        for (entry in entries) {
            val key = entry.text.lowercase()
            val existing = deduped[key]
            if (existing == null || entry.confidence > existing.confidence) {
                deduped[key] = entry
            }
        }

        val comparator = if (confidencesAvailable) {
            compareByDescending<Entry> { it.confidence }
                .thenByDescending { it.text.length } // text completeness as a tie-breaker
                .thenBy { it.originalIndex }
        } else {
            // No confidence signal at all: fall back safely to Android's own top guess.
            compareBy { it.originalIndex }
        }

        val ranked = deduped.values.sortedWith(comparator)
        val top = ranked.first()

        // Avoid repeating the same final result twice in a row when a distinct alternative exists.
        if (previousFinalResult != null &&
            ranked.size > 1 &&
            top.text.equals(previousFinalResult.trim(), ignoreCase = true)
        ) {
            return ranked[1].text
        }

        return top.text
    }

    private fun confidenceAt(confidences: FloatArray?, index: Int): Float {
        val value = confidences?.getOrNull(index) ?: return DEFAULT_CONFIDENCE
        return if (value in 0f..1f) value else DEFAULT_CONFIDENCE
    }

    private data class Entry(val text: String, val confidence: Float, val originalIndex: Int)

    companion object {
        /** Neutral confidence used when a candidate has no (or an out-of-range) score. */
        private const val DEFAULT_CONFIDENCE = 0.5f
    }
}
