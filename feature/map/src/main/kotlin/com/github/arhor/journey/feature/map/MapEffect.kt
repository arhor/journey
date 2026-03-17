package com.github.arhor.journey.feature.map

sealed interface MapEffect {
    data class OpenObjectDetails(
        val objectId: String,
    ) : MapEffect

    data class OpenAddPoi(
        val latitude: Double,
        val longitude: Double,
    ) : MapEffect

    data class ShowMessage(
        val message: String,
    ) : MapEffect

    data object RequestLocationPermission : MapEffect
}
