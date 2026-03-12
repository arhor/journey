package com.github.arhor.journey.core.common

/**
 * A [StateError] adapter that wraps a plain [Throwable].
 *
 * Use this when you want to keep the `State<T, E>` API strongly typed (with `E : StateError`),
 * but still need to represent failures that originate as exceptions (network stack, parsing,
 * unexpected runtime errors, etc.).
 *
 * - [cause] preserves the original exception and its stack trace for logging / crash reporting.
 * - [message] defaults to [Throwable.message] but can be overridden if you want a friendlier text.
 */
data class ThrowableStateError(
    override val cause: Throwable,
    override val message: String? = cause.message
) : StateError

/**
 * Converts this [Throwable] into a typed [State.Failure] using [ThrowableStateError].
 *
 * This is a convenience for returning/propagating exception-based failures without making your
 * `State` error type `Throwable`-only.
 */
fun <T> Throwable.asFailure(): State<T, ThrowableStateError> =
    State.Failure(ThrowableStateError(cause = this))
