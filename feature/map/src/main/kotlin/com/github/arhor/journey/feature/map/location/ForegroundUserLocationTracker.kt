package com.github.arhor.journey.feature.map.location

import com.github.arhor.journey.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

interface ForegroundUserLocationTracker {
    fun observeLocations(): Flow<UserLocationUpdate>
}

sealed interface UserLocationUpdate {
    data class Available(
        val location: GeoPoint,
    ) : UserLocationUpdate

    data object PermissionDenied : UserLocationUpdate

    data object LocationServicesDisabled : UserLocationUpdate

    data object TemporarilyUnavailable : UserLocationUpdate
}
