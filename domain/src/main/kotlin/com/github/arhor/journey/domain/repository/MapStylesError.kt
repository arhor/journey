package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.core.common.StateError

sealed interface MapStylesError : StateError {

    data class AssetReadFailure(
        override val cause: Throwable,
    ) : MapStylesError {
        override val message: String = "Failed to read map styles."
    }

    data class DeserializationFailure(
        override val cause: Throwable,
    ) : MapStylesError {
        override val message: String = "Failed to parse map styles."
    }

    data class UnknownFailure(
        override val cause: Throwable,
    ) : MapStylesError {
        override val message: String = "Failed to load map styles."
    }
}
