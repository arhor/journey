package com.github.arhor.journey.feature.settings

sealed interface SettingsIntent {

    data class SelectMapStyle(val styleId: String) : SettingsIntent
}
