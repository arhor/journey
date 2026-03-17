package com.github.arhor.journey.tracking.location

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

interface UserLocationSource {
    fun observeLocations(
        cadence: Flow<ExplorationTrackingCadence>,
    ): Flow<UserLocationUpdate>
}

sealed interface UserLocationUpdate {
    data class Available(
        val location: GeoPoint,
    ) : UserLocationUpdate

    data object PermissionDenied : UserLocationUpdate

    data object LocationServicesDisabled : UserLocationUpdate

    data object TemporarilyUnavailable : UserLocationUpdate
}
