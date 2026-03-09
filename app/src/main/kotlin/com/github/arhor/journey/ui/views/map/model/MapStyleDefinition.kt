package com.github.arhor.journey.ui.views.map.model

internal sealed interface MapStyleDefinition {
    data class Remote(val uri: String) : MapStyleDefinition

    data class Asset(
        val path: String,
        val fallbackUri: String,
    ) : MapStyleDefinition
}
