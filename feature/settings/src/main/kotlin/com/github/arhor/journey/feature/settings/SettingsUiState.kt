package com.github.arhor.journey.feature.settings

import androidx.compose.runtime.Immutable

sealed interface SettingsUiState {

    @Immutable
    data object Loading : SettingsUiState

    @Immutable
    data class Failure(val errorMessage: String) : SettingsUiState

    @Immutable
    data object Content : SettingsUiState
}
