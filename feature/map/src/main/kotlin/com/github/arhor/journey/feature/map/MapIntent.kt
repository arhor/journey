package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapViewportSize

sealed interface MapIntent {
    data object MapOpened : MapIntent

    data class CameraViewportChanged(
        val visibleBounds: GeoBounds,
    ) : MapIntent

    data class MapViewportSizeChanged(
        val viewportSize: MapViewportSize,
    ) : MapIntent

    data class CameraGestureStarted(
        val position: CameraPositionState,
    ) : MapIntent

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

    data object DismissWatchtowerSheet : MapIntent

    data object ClaimSelectedWatchtower : MapIntent

    data object UpgradeSelectedWatchtower : MapIntent

    data object AddPoiClicked : MapIntent

    data class MapLoadFailed(
        val message: String? = null,
    ) : MapIntent
}
