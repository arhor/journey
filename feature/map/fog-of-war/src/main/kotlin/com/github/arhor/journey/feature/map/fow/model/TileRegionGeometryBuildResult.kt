package com.github.arhor.journey.feature.map.fow.model

internal data class TileRegionGeometryBuildResult(
    val geometries: List<TileRegionGeometry>,
    val metrics: TileRegionGeometryMetrics,
)
