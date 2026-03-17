package com.github.arhor.journey.feature.map

sealed interface AddPoiEffect {
    data class ShowMessage(
        val message: String,
    ) : AddPoiEffect

    data object Saved : AddPoiEffect
}
