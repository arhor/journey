package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.DomainError

sealed interface MapStylesError : DomainError {

    data class AssetReadFailure(
        override val cause: Throwable,
        override val message: String = "Failed to read map styles.",
    ) : MapStylesError

    data class DeserializationFailure(
        override val cause: Throwable,
        override val message: String = "Failed to parse map styles.",
    ) : MapStylesError

    data class UnknownFailure(
        override val cause: Throwable,
        override val message: String = "Failed to load map styles.",
    ) : MapStylesError
}
