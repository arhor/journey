package com.github.arhor.journey.feature.map.fow.model

internal data class TileRegionGeometry(
    val zoom: Int,
    val outerRing: TileRegionRing,
    val holeRings: List<TileRegionRing> = emptyList(),
)
