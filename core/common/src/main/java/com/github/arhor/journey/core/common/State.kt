package com.github.arhor.journey.core.common

/**
 * Represents the state of an asynchronous or deferred value.
 *
 * `State` models a value that is either:
 * - currently being produced ([Loading]),
 * - successfully available ([Content]),
 * - or failed with a domain-specific error ([Failure]).
 *
 * This type is intentionally parameterized by:
 * - `T`: the success payload type
 * - `E`: an error type that implements [StateError]
 *
 * Making the error type explicit (instead of always using [Throwable]) allows callers to model
 * failures with a sealed hierarchy (e.g. `sealed interface MyError : StateError`) and use
 * exhaustive `when` expressions to pattern-match error cases, while still optionally carrying
 * an underlying [Throwable] via [StateError.cause] for logging/diagnostics.
 *
 * ## Variance
 * - `T` is covariant (`out`) because a state producing a subtype can be safely used where a
 *   state producing a supertype is expected.
 * - `E` is covariant (`out`) for the same reason: a state that can fail with a more specific
 *   error can be used where a more general error is expected.
 *
 * ## Type arguments in subtypes
 * - [Loading] uses `Nothing` for both `T` and `E` because it contains neither data nor error.
 * - [Content] uses `Nothing` for `E` because it cannot represent a failure.
 * - [Failure] uses `Nothing` for `T` because it cannot contain successful data.
 */
sealed class State<out T, out E : StateError> {
    /**
     * Indicates that the value is currently being loaded or computed.
     */
    data object Loading : State<Nothing, Nothing>()

    /**
     * Indicates that the value was produced successfully.
     */
    data class Content<out T>(val value: T) : State<T, Nothing>()

    /**
     * Indicates that producing the value failed.
     *
     * The error is a domain-specific type [E] (often a sealed interface) which may optionally
     * carry an underlying [Throwable] through [StateError.cause].
     */
    data class Failure<out E : StateError>(val error: E) : State<Nothing, E>()
}

/**
 * Maps the success payload ([State.Content]) while preserving [State.Loading] and [State.Failure].
 */
inline fun <T, E : StateError, R> State<T, E>.map(transform: (T) -> R): State<R, E> =
    when (this) {
        State.Loading -> State.Loading
        is State.Content -> State.Content(transform(value))
        is State.Failure -> this
    }

/**
 * Flat-maps the success payload ([State.Content]) into another [State], preserving
 * [State.Loading] and [State.Failure].
 */
inline fun <T, E : StateError, R> State<T, E>.flatMap(transform: (T) -> State<R, E>): State<R, E> =
    when (this) {
        State.Loading -> State.Loading
        is State.Content -> transform(value)
        is State.Failure -> this
    }

/**
 * Folds a [State] into a single value by providing handlers for each case.
 */
inline fun <T, E : StateError, R> State<T, E>.fold(
    onLoading: () -> R,
    onContent: (T) -> R,
    onFailure: (E) -> R,
): R = when (this) {
    State.Loading -> onLoading()
    is State.Content -> onContent(value)
    is State.Failure -> onFailure(error)
}

/**
 * Invokes [block] if this is [State.Content], returning the original [State] unchanged.
 */
inline fun <T, E : StateError> State<T, E>.onContent(block: (T) -> Unit): State<T, E> =
    also { if (it is State.Content) block(it.value) }

/**
 * Invokes [block] if this is [State.Failure], returning the original [State] unchanged.
 */
inline fun <T, E : StateError> State<T, E>.onFailure(block: (E) -> Unit): State<T, E> =
    also { if (it is State.Failure) block(it.error) }

/**
 * Invokes [block] if this is [State.Loading], returning the original [State] unchanged.
 */
inline fun <T, E : StateError> State<T, E>.onLoading(block: () -> Unit): State<T, E> =
    also { if (it === State.Loading) block() }

/**
 * Converts a failure into success by providing a fallback value.
 * - If [State.Content], returns itself
 * - If [State.Failure], returns [State.Content] of the fallback
 * - If [State.Loading], stays loading
 */
inline fun <T, E : StateError> State<T, E>.recover(onFailure: (E) -> T): State<T, E> =
    when (this) {
        State.Loading -> State.Loading
        is State.Content -> this
        is State.Failure -> State.Content(onFailure(error))
    }
