package com.github.arhor.journey.ui.views.settings

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.DistanceUnit

sealed interface SettingsUiState {

    @Immutable
    data object Loading : SettingsUiState

    @Immutable
    data class Failure(val errorMessage: String) : SettingsUiState

    @Immutable
    data class Content(val isUpdating: Boolean, val distanceUnit: DistanceUnit) : SettingsUiState
}
