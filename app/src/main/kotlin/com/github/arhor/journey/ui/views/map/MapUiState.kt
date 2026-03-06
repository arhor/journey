package com.github.arhor.journey.ui.views.map

import androidx.compose.runtime.Immutable

@Immutable
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

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
    val cameraTarget: LatLng,
    val zoom: Double,
    val visibleObjects: List<MapObjectUiModel>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val isAttributionVisible: Boolean,
) {
    companion object {
        val Loading = MapUiState(
            cameraTarget = LatLng(
                latitude = 0.0,
                longitude = 0.0,
            ),
            zoom = 12.0,
            visibleObjects = emptyList(),
            isLoading = true,
            errorMessage = null,
            isAttributionVisible = true,
        )
    }
}
