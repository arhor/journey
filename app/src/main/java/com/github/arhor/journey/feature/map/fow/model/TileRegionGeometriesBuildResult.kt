package com.github.arhor.journey.feature.map.fow.model

import com.github.arhor.journey.feature.map.fow.model.FogOfWarGeometryMetrics

internal data class TileRegionGeometriesBuildResult(
    val geometries: List<TileRegionGeometry>,
    val metrics: FogOfWarGeometryMetrics,
)
