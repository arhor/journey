package com.github.arhor.journey.domain.model

sealed interface StartExplorationTrackingSessionResult {
    data object Started : StartExplorationTrackingSessionResult

    data object AlreadyActive : StartExplorationTrackingSessionResult

    data object PermissionRequired : StartExplorationTrackingSessionResult

    data class Failed(val message: String? = null) : StartExplorationTrackingSessionResult
}
