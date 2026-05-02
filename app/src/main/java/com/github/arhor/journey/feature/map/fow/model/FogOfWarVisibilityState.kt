package com.github.arhor.journey.feature.map.fow.model

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.MapTile

@Immutable
data class FogOfWarVisibilityState(
    val canonicalZoom: Int = 0,
    val visibilityTileMask: Set<MapTile> = emptySet(),
)
