package com.github.arhor.journey.feature.map.fow.model

internal data class TileRegionGeometryMetrics(
    val boundaryEdgeCount: Int,
    val loopCount: Int,
    val ringPointCount: Int,
    val resolvedAmbiguousVertexCount: Int,
)
