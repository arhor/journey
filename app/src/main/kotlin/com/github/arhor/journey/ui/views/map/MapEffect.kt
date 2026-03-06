package com.github.arhor.journey.ui.views.map

sealed interface MapEffect {
    data class OpenObjectDetails(
        val objectId: String,
    ) : MapEffect

    data object RequestLocationPermission : MapEffect

    data class ShowMessage(
        val message: String,
    ) : MapEffect
}
