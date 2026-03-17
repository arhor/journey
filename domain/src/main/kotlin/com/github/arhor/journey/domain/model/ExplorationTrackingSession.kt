package com.github.arhor.journey.domain.model

data class ExplorationTrackingSession(
    val isActive: Boolean = false,
    val status: ExplorationTrackingStatus = ExplorationTrackingStatus.INACTIVE,
    val cadence: ExplorationTrackingCadence = ExplorationTrackingCadence.BACKGROUND,
    val lastKnownLocation: GeoPoint? = null,
)

enum class ExplorationTrackingStatus {
    INACTIVE,
    STARTING,
    TRACKING,
    PERMISSION_DENIED,
    LOCATION_SERVICES_DISABLED,
    TEMPORARILY_UNAVAILABLE,
}

enum class ExplorationTrackingCadence {
    FOREGROUND,
    BACKGROUND,
}

sealed interface StartExplorationTrackingSessionResult {
    data object Started : StartExplorationTrackingSessionResult

    data object AlreadyActive : StartExplorationTrackingSessionResult

    data object PermissionRequired : StartExplorationTrackingSessionResult

    data class Failed(
        val message: String? = null,
    ) : StartExplorationTrackingSessionResult
}
