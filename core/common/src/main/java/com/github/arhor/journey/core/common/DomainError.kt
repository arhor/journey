package com.github.arhor.journey.core.common

/**
 * Base contract for domain-level errors carried by [Output.Failure].
 *
 * Unlike using [Throwable] directly, `DomainError` lets you model failures with your own error types
 * (often a sealed interface with multiple implementations) so callers can pattern-match errors in
 * an exhaustive `when`.
 *
 * Both properties are optional:
 * - [message] is a human-readable description suitable for UI or logging.
 * - [cause] can carry an underlying [Throwable] for diagnostics (stack trace, crash reporting),
 *   while still keeping the public error model domain-specific.
 *
 * Implementations are encouraged to override one or both properties as needed.
 *
 * Example:
 * ```
 * sealed interface LoginError : DomainError {
 *     data object InvalidCredentials : LoginError {
 *         override val message = "Wrong email or password"
 *     }
 *
 *     data class Network(override val cause: Throwable) : LoginError {
 *         override val message = "Network error"
 *     }
 * }
 * ```
 */
interface DomainError {
    /**
     * Optional, human-readable description of the error.
     */
    val message: String?
        get() = null

    /**
     * Optional underlying exception for diagnostics and logging.
     */
    val cause: Throwable?
        get() = null
}
