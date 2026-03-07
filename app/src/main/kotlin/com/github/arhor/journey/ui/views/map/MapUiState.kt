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

@Immutable
data class MapUiState(
    val cameraPosition: CameraPositionState,
    val cameraUpdateOrigin: CameraUpdateOrigin,
    val selectedStyle: MapStyle,
    val resolvedStyle: MapResolvedStyle,
    val styleLoadErrorMessage: String?,
    val styleReloadToken: Int,
    val visibleObjects: List<MapObjectUiModel>,
    val isLoading: Boolean,
    val errorMessage: String?,
) {
    companion object {
        val Loading = MapUiState(
            cameraPosition = CameraPositionState(
                target = LatLng(
                    latitude = 0.0,
                    longitude = 0.0,
                ),
                zoom = 12.0,
            ),
            cameraUpdateOrigin = CameraUpdateOrigin.PROGRAMMATIC,
            selectedStyle = MapStyle.DEFAULT,
            resolvedStyle = MapResolvedStyle.Uri(MapStyleRepository.DEFAULT_STYLE_FALLBACK_URI),
            styleLoadErrorMessage = null,
            styleReloadToken = 0,
            visibleObjects = emptyList(),
            isLoading = true,
            errorMessage = null,
        )
    }
}
