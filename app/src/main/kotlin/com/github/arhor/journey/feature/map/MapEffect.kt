package com.github.arhor.journey.feature.map

sealed interface MapEffect {
    data class ShowMessage(
        val message: String,
    ) : MapEffect

    data object RequestLocationPermission : MapEffect
}
