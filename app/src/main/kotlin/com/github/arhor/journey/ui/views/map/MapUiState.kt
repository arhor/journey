package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.ui.views.map.model.CameraPositionState
import com.github.arhor.journey.ui.views.map.model.CameraUpdateOrigin
import com.github.arhor.journey.ui.views.map.model.MapObjectUiModel

sealed interface MapUiState {

    @Immutable
    data object Loading : MapUiState

    @Immutable
    data class Failure(
        val errorMessage: String,
    ) : MapUiState

    @Immutable
    data class Content(
        val cameraPosition: CameraPositionState,
        val cameraUpdateOrigin: CameraUpdateOrigin,
        val recenterRequestToken: Int,
        val selectedStyle: MapStyle?,
        val visibleObjects: List<MapObjectUiModel>,
    ) : MapUiState
}
