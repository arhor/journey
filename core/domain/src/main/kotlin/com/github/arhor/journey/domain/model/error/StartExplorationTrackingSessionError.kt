package com.github.arhor.journey.domain.model.error

import com.github.arhor.journey.core.common.DomainError

sealed interface StartExplorationTrackingSessionError : DomainError {

    data object PermissionRequired : StartExplorationTrackingSessionError

    data class LaunchFailed(
        override val cause: Throwable,
    ) : StartExplorationTrackingSessionError {
        override val message: String? = cause.message
    }
}
