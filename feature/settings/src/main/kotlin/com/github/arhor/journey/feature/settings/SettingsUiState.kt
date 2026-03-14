package com.github.arhor.journey.feature.settings

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.DistanceUnit
import com.github.arhor.journey.domain.model.MapStyle

sealed interface SettingsUiState {

    @Immutable
    data object Loading : SettingsUiState

    @Immutable
    data class Failure(val errorMessage: String) : SettingsUiState

    @Immutable
    data class Content(
        val isUpdating: Boolean,
        val distanceUnit: DistanceUnit,
        val selectedMapStyleId: String?,
        val availableMapStyles: List<MapStyle>,
    ) : SettingsUiState
}
