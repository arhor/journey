package com.github.arhor.journey.ui.views.map

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

    data object RetryStyleLoad : MapIntent
}
