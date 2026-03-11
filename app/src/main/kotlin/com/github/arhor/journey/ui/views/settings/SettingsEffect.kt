package com.github.arhor.journey.ui.views.settings

sealed interface SettingsEffect {
    data class Error(val message: String) : SettingsEffect

    data class Success(val message: String) : SettingsEffect
}
