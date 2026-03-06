package com.github.arhor.journey.ui.views.map

sealed interface MapIntent {
    data class OnMapLoaded(
        val cameraTarget: LatLng,
        val zoom: Double,
    ) : MapIntent

    data class OnMapTapped(
        val target: LatLng,
    ) : MapIntent

    data object OnRecenterClicked : MapIntent

    data class OnObjectTapped(
        val objectId: String,
    ) : MapIntent

    data class OnMapLoadFailed(
        val message: String? = null,
    ) : MapIntent

    data object RetryStyleLoad : MapIntent
}
