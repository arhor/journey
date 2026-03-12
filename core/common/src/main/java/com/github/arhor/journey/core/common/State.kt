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
    data class Content<out T>(val data: T) : State<T, Nothing>()

    /**
     * Indicates that producing the value failed.
     *
     * The error is a domain-specific type [E] (often a sealed interface) which may optionally
     * carry an underlying [Throwable] through [StateError.cause].
     */
    data class Failure<out E : StateError>(val error: E) : State<Nothing, E>()
}
