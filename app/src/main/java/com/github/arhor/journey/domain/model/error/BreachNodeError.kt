package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface BreachNodeError : DomainError {

    data object NotFound : BreachNodeError {
        override val message: String = "Breach node not found."
    }

    data class Unexpected(
        val operation: String,
        override val cause: Throwable,
    ) : BreachNodeError {
        override val message: String? = cause.message
    }
}
