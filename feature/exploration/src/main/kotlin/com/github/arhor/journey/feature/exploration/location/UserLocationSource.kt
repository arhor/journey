package com.github.arhor.journey.feature.exploration.location

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.UserLocationFix
import kotlinx.coroutines.flow.Flow

interface UserLocationSource {
    fun observeLocations(
        cadence: Flow<ExplorationTrackingCadence>,
    ): Flow<UserLocationUpdate>
}

sealed interface UserLocationUpdate {
    data class Available(
        val fix: UserLocationFix,
    ) : UserLocationUpdate {
        @Deprecated(
            message = "Use the UserLocationFix constructor so accuracy, speed, bearing, and timestamp metadata are explicit.",
            replaceWith = ReplaceWith("Available(UserLocationFix(location = location))"),
        )
        constructor(location: GeoPoint) : this(UserLocationFix(location = location))

        val location: GeoPoint
            get() = fix.location
    }

    data object PermissionDenied : UserLocationUpdate

    data object LocationServicesDisabled : UserLocationUpdate

    data object TemporarilyUnavailable : UserLocationUpdate
}
