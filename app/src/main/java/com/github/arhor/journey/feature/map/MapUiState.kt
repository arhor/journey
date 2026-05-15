package com.github.arhor.journey.feature.map

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.feature.map.fow.model.FogOfWarUiState
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
        val northResetRequestToken: Int,
        val isExplorationTrackingActive: Boolean,
        val explorationTrackingCadence: ExplorationTrackingCadence,
        val explorationTrackingStatus: ExplorationTrackingStatus,
        val isStartupSplashVisible: Boolean,
        @StringRes val startupSplashMessage: Int,
        val mapStyleUri: String,
        val visibleObjects: List<MapObjectUiModel>,
        val fogOfWar: FogOfWarUiState,
    ) : MapUiState
}

@Immutable
data class CurrentLocationUiModel(
    val position: LatLng,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val bearingDegrees: Double?,
    val bearingAccuracyDegrees: Double?,
    val elapsedRealtimeNanos: Long?,
)
