package com.github.arhor.journey.domain.model

data class ExplorationTrackingSession(
    val isActive: Boolean = false,
    val status: ExplorationTrackingStatus = ExplorationTrackingStatus.INACTIVE,
    val cadence: ExplorationTrackingCadence = ExplorationTrackingCadence.BACKGROUND,
    val lastKnownLocation: GeoPoint? = null,
)

