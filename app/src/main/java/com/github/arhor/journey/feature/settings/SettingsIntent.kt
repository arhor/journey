package com.github.arhor.journey.feature.settings

sealed interface SettingsIntent {
    data class MapStyleSelected(
        val styleId: String,
    ) : SettingsIntent
}
