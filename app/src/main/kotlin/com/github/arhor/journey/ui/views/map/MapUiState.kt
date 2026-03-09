package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.MapStyle

@Immutable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

@Immutable
data class CameraPositionState(
    val target: LatLng,
    val zoom: Double,
)

enum class CameraUpdateOrigin {
    USER,
    PROGRAMMATIC,
}

@Immutable
data class MapObjectUiModel(
    val id: String,
    val title: String,
    val description: String?,
    val position: LatLng,
    val radiusMeters: Int,
    val isDiscovered: Boolean,
)

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
        val selectedStyle: MapStyle,
        val resolvedStyle: MapResolvedStyle,
        val visibleObjects: List<MapObjectUiModel>,
    ) : MapUiState
}
