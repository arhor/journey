package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.DomainError
import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.core.common.toOutputFlow
import com.github.arhor.journey.domain.model.error.UseCaseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal inline fun <T> runUseCaseCatching(
    operation: String,
    block: () -> T,
): Output<T, UseCaseError> = try {
    Output.Success(block())
} catch (exception: Throwable) {
    if (exception is CancellationException) {
        throw exception
    }

    Output.Failure(
        UseCaseError.Unexpected(
            operation = operation,
            cause = exception,
        ),
    )
}

internal suspend inline fun <T> runSuspendingUseCaseCatching(
    operation: String,
    crossinline block: suspend () -> T,
): Output<T, UseCaseError> = try {
    Output.Success(block())
} catch (exception: Throwable) {
    if (exception is CancellationException) {
        throw exception
    }

    Output.Failure(
        UseCaseError.Unexpected(
            operation = operation,
            cause = exception,
        ),
    )
}

internal fun <T> Flow<T>.toUseCaseOutputFlow(
    operation: String,
): Flow<Output<T, UseCaseError>> = toOutputFlow(
    onSuccess = { it },
    onFailure = { throwable ->
        UseCaseError.Unexpected(
            operation = operation,
            cause = throwable,
        )
    },
)

internal fun <T> invalidUseCaseInput(
    message: String,
): Output<T, UseCaseError> = Output.Failure(UseCaseError.InvalidInput(message))

internal fun <T> useCaseNotFound(
    subject: String,
    identifier: Any?,
): Output<T, UseCaseError> = Output.Failure(
    UseCaseError.NotFound(
        subject = subject,
        identifier = identifier?.toString(),
    ),
)

internal fun DomainError.asThrowable(
    fallbackMessage: String,
): Throwable = cause ?: IllegalStateException(message ?: fallbackMessage)

internal class TransactionAbortException(
    val error: DomainError,
) : RuntimeException(null, null, false, false)

internal fun abortTransaction(
    error: DomainError,
): Nothing = throw TransactionAbortException(error)
