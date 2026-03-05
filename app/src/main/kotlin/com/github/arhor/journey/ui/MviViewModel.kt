package com.github.arhor.journey.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base [ViewModel] for screens following the Model-View-Intent (MVI) pattern.
 *
 * This class provides:
 * - a hot [StateFlow] with the current UI state
 * - a hot [SharedFlow] for one-off UI effects
 * - an internal [SharedFlow] for intents dispatched from the UI
 *
 * Intents submitted via [dispatch] are collected sequentially in [viewModelScope]
 * and delegated to [handleIntent]. Subclasses are expected to react to intents by
 * updating state via [setState] and optionally emitting one-time effects.
 *
 * Typical usage:
 * - expose screen state through [uiState]
 * - send user actions to [dispatch]
 * - handle transient events such as navigation or snackbars through [effects]
 *
 * Type parameters:
 * - [S] the immutable UI state type
 * - [I] the intent type representing user or system actions
 * - [E] the effect type representing one-off events
 *
 * Example:
 * `dispatch(Intent.SubmitClicked)` ->
 * `handleIntent(...)` ->
 * `setState { copy(isLoading = true) }` ->
 * `emitEffect(Effect.NavigateBack)`
 *
 * @param S immutable UI state type
 * @param E one-off effect type emitted through [effects]
 * @param I intent type consumed by [handleIntent]
 * @param initialState initial value of the UI state exposed by [uiState]
 * @property uiState A hot flow containing the latest UI state.
 * @property effects A hot flow of one-time UI effects.
 */
abstract class MviViewModel<S : Any, E : Any, I : Any>(
    initialState: S,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState

    /**
     * Effects:
     * - no replay by default, so effects are not repeated to new collectors
     * - small buffer to survive short timing gaps / bursts
     * - SUSPEND avoids silently dropping effects
     */
    private val _effects = MutableSharedFlow<E>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    val effects: SharedFlow<E> = _effects

    /**
     * Intents:
     * - no replay, because old intents should not be re-delivered to new collectors
     * - extra buffer to absorb UI bursts
     * - SUSPEND keeps ordering and avoids silent loss when buffer is full
     */
    private val intents = MutableSharedFlow<I>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    init {
        viewModelScope.launch {
            intents.collect(::handleIntent)
        }
    }

    /**
     * Handles a single [intent] dispatched via [dispatch].
     *
     * Subclasses implement this function to define feature-specific business logic.
     * Typical responsibilities include:
     * - updating UI state via [setState]
     * - invoking domain or data layer operations
     * - emitting one-off effects via [emitEffect]
     *
     * Intents are collected and delivered sequentially by the base class.
     *
     * @param intent Intent to handle.
     */
    protected abstract suspend fun handleIntent(intent: I)

    /**
     * Dispatches an [intent] to be processed by [handleIntent].
     *
     * The intent is emitted asynchronously in [viewModelScope]. Intents are
     * processed sequentially by the internal collector started in this base class.
     *
     * This function is intended to be called from the UI layer in response to
     * user actions or external UI-facing events.
     *
     * @param intent Intent to process.
     */
    fun dispatch(intent: I) {
        if (!intents.tryEmit(intent)) {
            viewModelScope.launch {
                intents.emit(intent)
            }
        }
    }

    /**
     * Atomically updates the current UI state using the provided [reducer].
     *
     * The [reducer] receives the current state and must return a new immutable state.
     * This is the primary mechanism for state transitions in subclasses.
     *
     * Example:
     * `setState { copy(isLoading = true) }`
     *
     * @param reducer Function that transforms the current state into a new state.
     */
    protected fun setState(reducer: S.() -> S) {
        _uiState.update(reducer)
    }

    /**
     * Emits a one-off [effect] to [effects].
     *
     * This function is intended for transient events that should not become part
     * of persistent UI state, such as navigation, snackbar messages, or opening
     * a dialog.
     *
     * The effect is emitted asynchronously in [viewModelScope].
     *
     * @param effect Effect to emit.
     */
    protected fun emitEffect(effect: E) {
        if (!_effects.tryEmit(effect)) {
            viewModelScope.launch {
                _effects.emit(effect)
            }
        }
    }
}
