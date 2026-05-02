package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds

@Immutable
internal data class FogBufferRegion(
    val triggerBounds: GeoBounds,
    val bufferedBounds: GeoBounds,
    val triggerTileRange: ExplorationTileRange,
    val bufferedTileRange: ExplorationTileRange,
)
