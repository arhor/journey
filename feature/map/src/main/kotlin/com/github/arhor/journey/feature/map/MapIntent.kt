package com.github.arhor.journey.feature.map

import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng

sealed interface MapIntent {
    data object StartLocationTracking : MapIntent

    data object StopLocationTracking : MapIntent

    data class CameraSettled(
        val position: CameraPositionState,
        val origin: CameraUpdateOrigin,
    ) : MapIntent

    data class MapTapped(
        val target: LatLng,
    ) : MapIntent

    data object RecenterClicked : MapIntent

    data class LocationPermissionResult(
        val isGranted: Boolean,
    ) : MapIntent

    data object CurrentLocationUnavailable : MapIntent

    data class ObjectTapped(
        val objectId: String,
    ) : MapIntent

    data class MapLoadFailed(
        val message: String? = null,
    ) : MapIntent
}
