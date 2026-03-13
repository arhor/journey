@file:Suppress("unused")

package com.github.arhor.journey.core.common

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Represents the output of an operation.
 *
 * `Output` models a value that is either:
 * - successfully available ([Success]),
 * - or failed with a domain-specific error ([Failure]).
 *
 * This type is intentionally parameterized by:
 * - `T`: the success payload type
 * - `E`: an error type that implements [DomainError]
 *
 * Making the error type explicit (instead of always using [Throwable]) allows callers to model
 * failures with a sealed hierarchy (e.g. `sealed interface MyError : DomainError`) and use
 * exhaustive `when` expressions to pattern-match error cases, while still optionally carrying
 * an underlying [Throwable] via [DomainError.cause] for logging/diagnostics.
 *
 * ## Variance
 * - `T` is covariant (`out`) because a state producing a subtype can be safely used where a
 *   state producing a supertype is expected.
 * - `E` is covariant (`out`) for the same reason: a state that can fail with a more specific
 *   error can be used where a more general error is expected.
 *
 * ## Type arguments in subtypes
 * - [Success] uses `Nothing` for `E` because it cannot represent a failure.
 * - [Failure] uses `Nothing` for `T` because it cannot contain successful data.
 */
sealed class Output<out T, out E : DomainError> {
    /**
     * Indicates that the value was produced successfully.
     */
    data class Success<out T>(
        val value: T,
    ) : Output<T, Nothing>()

    /**
     * Indicates that producing the value failed.
     *
     * The error is a domain-specific type [E] (often a sealed interface) which may optionally
     * carry an underlying [Throwable] through [DomainError.cause].
     */
    data class Failure<out E : DomainError>(
        val error: E,
    ) : Output<Nothing, E>()
}

/**
 * Maps the success payload ([Output.Success]) while preserving [Output.Failure].
 */
inline fun <T, E : DomainError, R> Output<T, E>.map(transform: (T) -> R): Output<R, E> =
    when (this) {
        is Output.Success -> Output.Success(transform(value))
        is Output.Failure -> this
    }

/**
 * Flat-maps the success payload ([Output.Success]) into another [Output], preserving [Output.Failure].
 */
inline fun <T, E : DomainError, R> Output<T, E>.flatMap(transform: (T) -> Output<R, E>): Output<R, E> =
    when (this) {
        is Output.Success -> transform(value)
        is Output.Failure -> this
    }

/**
 * Folds a [Output] into a single value by providing handlers for each case.
 */
inline fun <T, E : DomainError, R> Output<T, E>.fold(
    onSuccess: (T) -> R,
    onFailure: (E) -> R,
): R = when (this) {
    is Output.Success -> onSuccess(value)
    is Output.Failure -> onFailure(error)
}

/**
 * Invokes [block] if this is [Output.Success], returning the original [Output] unchanged.
 */
inline fun <T, E : DomainError> Output<T, E>.onSuccess(block: (T) -> Unit): Output<T, E> =
    also { if (it is Output.Success) block(it.value) }

/**
 * Invokes [block] if this is [Output.Failure], returning the original [Output] unchanged.
 */
inline fun <T, E : DomainError> Output<T, E>.onFailure(block: (E) -> Unit): Output<T, E> =
    also { if (it is Output.Failure) block(it.error) }

/**
 * Converts a failure into success by providing a fallback value.
 * - If [Output.Success], returns itself
 * - If [Output.Failure], returns [Output.Success] of the fallback
 */
inline fun <T, E : DomainError> Output<T, E>.recover(onFailure: (E) -> T): Output<T, E> =
    when (this) {
        is Output.Success -> this
        is Output.Failure -> Output.Success(onFailure(error))
    }

inline fun <T, R, E : DomainError> Flow<T>.toOutputFlow(
    crossinline onSuccess: (T) -> R,
    crossinline onFailure: (Throwable) -> E,
): Flow<Output<R, E>> =
    this.map<T, Output<R, E>> { Output.Success(onSuccess(it)) }
        .catch { emit(Output.Failure(onFailure(it))) }

/**
 * Converts this [Throwable] into a typed [Output.Failure] with a given [DomainError].
 *
 * This is a convenience for returning/propagating exception-based failures without making your
 * `Output` error type `Throwable`-only.
 */
fun <T> Throwable.asFailure(): Output<T, DomainError> = Output.Failure(object : DomainError {
    override val cause: Throwable = this@asFailure
    override val message: String? = cause.message
})

/**
 * Combines two [Output] values that may have different error types.
 *
 * If both are [Output.Success], applies [transform] to their values.
 * If either is [Output.Failure], returns the first failure in left-to-right order.
 *
 * [E] is the common supertype of [E1] and [E2]. In the most general case it will be [DomainError].
 */
inline fun <A, B, E, E1, E2, R> combine(
    out1: Output<A, E1>,
    out2: Output<B, E2>,
    transform: (A, B) -> R,
): Output<R, E> where E : DomainError, E1 : E, E2 : E = when (out1) {
    is Output.Failure -> out1
    is Output.Success -> when (out2) {
        is Output.Failure -> out2
        is Output.Success -> Output.Success(transform(out1.value, out2.value))
    }
}

/**
 * Combines [out1] with [out2] into a [Pair].
 */
fun <A, B, E, E1, E2> combine(
    out1: Output<A, E1>,
    out2: Output<B, E2>,
): Output<Pair<A, B>, E> where E : DomainError, E1 : E, E2 : E = combine(
    out1 = out1,
    out2 = out2,
    transform = ::Pair,
)
