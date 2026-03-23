package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds

@Immutable
internal data class FogViewportSnapshot(
    val visibleBounds: GeoBounds,
    val visibleTileRange: ExplorationTileRange,
    val visibleTileCount: Long,
)
