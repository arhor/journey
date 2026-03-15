package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectUiModel

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
        val userLocation: LatLng?,
        val userLocationTrackingStatus: UserLocationTrackingStatus,
        val selectedStyle: MapStyle?,
        val visibleObjects: List<MapObjectUiModel>,
    ) : MapUiState
}

enum class UserLocationTrackingStatus {
    INACTIVE,
    TRACKING,
    PERMISSION_DENIED,
    LOCATION_SERVICES_DISABLED,
    TEMPORARILY_UNAVAILABLE,
}
