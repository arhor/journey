package com.github.arhor.journey.feature.map

sealed interface MapEffect {
    data class OpenObjectDetails(
        val objectId: String,
    ) : MapEffect

    data class ShowMessage(
        val message: String,
    ) : MapEffect
}
