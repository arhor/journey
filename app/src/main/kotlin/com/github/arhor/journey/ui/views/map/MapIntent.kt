package com.github.arhor.journey.ui.views.map

import com.github.arhor.journey.ui.views.map.model.CameraPositionState
import com.github.arhor.journey.ui.views.map.model.CameraUpdateOrigin
import com.github.arhor.journey.ui.views.map.model.LatLng

sealed interface MapIntent {
    data class CameraSettled(
        val position: CameraPositionState,
        val origin: CameraUpdateOrigin,
    ) : MapIntent

    data class MapTapped(
        val target: LatLng,
    ) : MapIntent

    data object RecenterClicked : MapIntent

    data class ObjectTapped(
        val objectId: String,
    ) : MapIntent

    data class MapLoadFailed(
        val message: String? = null,
    ) : MapIntent
}
