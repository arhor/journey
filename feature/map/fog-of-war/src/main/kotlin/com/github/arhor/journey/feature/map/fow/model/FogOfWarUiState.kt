package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds

@Immutable
data class FogOfWarUiState(
    val isOverlayEnabled: Boolean = true,
    val canonicalZoom: Int = 0,
    val visibleBounds: GeoBounds? = null,
    val triggerBounds: GeoBounds? = null,
    val bufferedBounds: GeoBounds? = null,
    val visibleTileRange: ExplorationTileRange? = null,
    val fogRanges: List<ExplorationTileRange> = emptyList(),
    val activeRenderData: FogOfWarRenderData? = null,
    val handoffRenderData: FogOfWarRenderData? = null,
    val visibleTileCount: Long = 0,
    val exploredVisibleTileCount: Int = 0,
    val isSuppressedByVisibleTileLimit: Boolean = false,
    val isRecomputing: Boolean = false,
)

@Immutable
data class FogOfWarRenderState(
    val isOverlayEnabled: Boolean = true,
    val activeRenderData: FogOfWarRenderData? = null,
    val handoffRenderData: FogOfWarRenderData? = null,
)

internal val FogOfWarUiState.renderState: FogOfWarRenderState
    get() = FogOfWarRenderState(
        isOverlayEnabled = isOverlayEnabled,
        activeRenderData = activeRenderData,
        handoffRenderData = handoffRenderData,
    )
