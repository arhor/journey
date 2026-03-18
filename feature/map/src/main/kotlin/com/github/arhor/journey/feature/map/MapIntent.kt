package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng

sealed interface MapIntent {
    data object MapOpened : MapIntent

    data object DebugControlsClicked : MapIntent

    data object DebugControlsDismissed : MapIntent

    data class DebugInfoVisibilityChanged(
        val item: MapDebugInfoItem,
        val isVisible: Boolean,
    ) : MapIntent

    data class FogOfWarOverlayToggled(
        val isEnabled: Boolean,
    ) : MapIntent

    data class TilesGridOverlayToggled(
        val isEnabled: Boolean,
    ) : MapIntent

    data class CanonicalZoomChanged(
        val value: Int,
    ) : MapIntent

    data class MapRenderModeSelected(
        val mode: MapRenderMode,
    ) : MapIntent

    data object ResumeTrackingClicked : MapIntent

    data object StopTrackingClicked : MapIntent

    data class CameraViewportChanged(
        val visibleBounds: GeoBounds,
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

    data object AddPoiClicked : MapIntent

    data object ResetExploredTilesClicked : MapIntent

    data class MapLoadFailed(
        val message: String? = null,
    ) : MapIntent
}
