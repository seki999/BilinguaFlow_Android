package com.seki999.bilinguaflow.model

/** The app's overall recognition session state, reflected directly by the UI's status label. */
enum class ListeningState {
    IDLE,
    LISTENING,
    PAUSED,
    STOPPED,
    ERROR
}

/** Inputs that can move [ListeningState] forward — user taps, timeouts, and recognizer errors. */
enum class ListeningEvent {
    START,
    PAUSE,
    RESUME,
    STOP,
    INACTIVITY_TIMEOUT,
    FATAL_ERROR,
    RECOVERABLE_ERROR
}

/**
 * Pure state-transition table for [ListeningState], kept separate from [com.seki999.bilinguaflow.viewmodel.SpeechViewModel]
 * so the transitions themselves are unit-testable without any Android/coroutine dependencies.
 */
object ListeningStateTransitions {

    fun next(current: ListeningState, event: ListeningEvent): ListeningState = when (event) {
        ListeningEvent.START -> ListeningState.LISTENING
        ListeningEvent.PAUSE -> if (current == ListeningState.LISTENING) ListeningState.PAUSED else current
        ListeningEvent.RESUME -> if (current == ListeningState.PAUSED) ListeningState.LISTENING else current
        ListeningEvent.STOP -> ListeningState.STOPPED
        ListeningEvent.INACTIVITY_TIMEOUT -> if (current == ListeningState.LISTENING) ListeningState.STOPPED else current
        ListeningEvent.FATAL_ERROR -> ListeningState.ERROR
        ListeningEvent.RECOVERABLE_ERROR -> current
    }
}
