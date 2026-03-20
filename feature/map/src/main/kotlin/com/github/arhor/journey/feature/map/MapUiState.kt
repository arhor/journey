package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingStatus
import com.github.arhor.journey.domain.model.MapStyle
import com.github.arhor.journey.feature.map.model.CameraPositionState
import com.github.arhor.journey.feature.map.model.CameraUpdateOrigin
import com.github.arhor.journey.feature.map.model.FogOfWarRenderData
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
        val cameraPosition: CameraPositionState?,
        val cameraUpdateOrigin: CameraUpdateOrigin,
        val recenterRequestToken: Int,
        val userLocation: LatLng?,
        val isExplorationTrackingActive: Boolean,
        val explorationTrackingCadence: ExplorationTrackingCadence,
        val explorationTrackingStatus: ExplorationTrackingStatus,
        val selectedStyle: MapStyle?,
        val visibleObjects: List<MapObjectUiModel>,
        val fogOfWar: FogOfWarUiState,
        val debug: MapDebugUiState,
    ) : MapUiState
}

@Immutable
data class FogOfWarUiState(
    val canonicalZoom: Int,
    val visibleTileRange: ExplorationTileRange?,
    val fogRanges: List<ExplorationTileRange>,
    val renderData: FogOfWarRenderData?,
    val visibleTileCount: Long,
    val exploredVisibleTileCount: Int,
    val isSuppressedByVisibleTileLimit: Boolean,
    val minimumZoom: Double? = null,
)
