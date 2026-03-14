package com.github.arhor.journey.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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
 * and delegated to [handleIntent]. Subclasses are expected to react to intents.
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
 * @param S immutable UI state type
 * @param E one-off effect type emitted through [effects]
 * @param I intent type consumed by [handleIntent]
 * @param initialState initial value of the UI state exposed by [uiState]
 * @param started [SharingStarted] strategy for the [uiState] StateFlow
 * @param intentsBuffer capacity of the internal [MutableSharedFlow] for intents
 * @param effectsBuffer capacity of the internal [MutableSharedFlow] for effects
 * @property uiState A hot flow containing the latest UI state.
 * @property effects A hot flow of one-time UI effects.
 */
abstract class MviViewModel<S : Any, E : Any, I : Any>(
    initialState: S,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
    intentsBuffer: Int = 64,
    effectsBuffer: Int = 16,
) : ViewModel() {

    private val _effects = createMutableSharedFlow<E>(bufferCapacity = effectsBuffer)
    private val _intents = createMutableSharedFlow<I>(bufferCapacity = intentsBuffer)

    val uiState: StateFlow<S> by lazy(LazyThreadSafetyMode.NONE) {
        buildUiState().stateIn(viewModelScope, started, initialState)
    }
    val effects: SharedFlow<E> = _effects

    init {
        viewModelScope.launch {
            _intents.collect(::handleIntent)
        }
    }

    protected abstract fun buildUiState(): Flow<S>
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
        if (!_intents.tryEmit(intent)) {
            viewModelScope.launch {
                _intents.emit(intent)
            }
        }
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

    companion object {
        private fun <T> createMutableSharedFlow(bufferCapacity: Int) = MutableSharedFlow<T>(
            replay = 0,
            extraBufferCapacity = bufferCapacity,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )
    }
}
