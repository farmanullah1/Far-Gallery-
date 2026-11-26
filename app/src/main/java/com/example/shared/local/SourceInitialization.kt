package com.example.shared.local

/**
 * Small in-memory source initialization guard.
 *
 * This exists so a Recent/library destination can distinguish:
 *  - still initializing
 *  - finished with content
 *  - finished empty
 *  - finished with error
 *
 * This is intentionally small and in-memory for this increment. It does not yet
 * model background indexing jobs, durable resume across process death, or
 * scan checkpoints. That can be added later when the surrounding feature needs it.
 */
internal data class SourceInitialization(
    val source: MediaSource,
    val isInitializing: Boolean = true,
    val finished: Boolean = false,
    val finishedEmpty: Boolean = false,
    val finishedWithError: Boolean = false,
) {
    /** Convenience status helper for UI decisions. */
    val status: SourceStatus
        get() = when {
            isInitializing -> SourceStatus.Initializing
            finishedWithError -> SourceStatus.Error
            finishedEmpty -> SourceStatus.Empty
            finished -> SourceStatus.Content
            else -> SourceStatus.Initializing
        }
}

internal sealed interface SourceStatus {
    object Initializing : SourceStatus
    object Content : SourceStatus
    object Empty : SourceStatus
    object Error : SourceStatus
}

/**
 * Transition helpers.
 *
 * In this increment the destination is responsible for driving the transition
 * from its own read-result handling. These helpers are purely descriptive.
 */
internal fun SourceInitialization.onFinishedContent() = copy(
    isInitializing = false,
    finished = true,
    finishedWithError = false,
    finishedEmpty = false,
)

internal fun SourceInitialization.onFinishedEmpty() = copy(
    isInitializing = false,
    finished = true,
    finishedEmpty = true,
    finishedWithError = false,
)

internal fun SourceInitialization.onFinishedError() = copy(
    isInitializing = false,
    finished = true,
    finishedWithError = true,
    finishedEmpty = false,
)
