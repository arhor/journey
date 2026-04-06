package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface UseCaseError : DomainError {

    data class InvalidInput(
        override val message: String,
    ) : UseCaseError

    data class NotFound(
        val subject: String,
        val identifier: String? = null,
    ) : UseCaseError {
        override val message: String = buildString {
            append(subject)
            append(" not found")
            if (identifier != null) {
                append(": ")
                append(identifier)
            }
            append('.')
        }
    }

    data class Unexpected(
        val operation: String,
        override val cause: Throwable,
    ) : UseCaseError {
        override val message: String? = cause.message
    }
}
