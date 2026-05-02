package com.github.arhor.journey.feature.map.fow.model

internal data class FogOfWarGeometryMetrics(
    val expandedCellCount: Long = 0,
    val connectedRegionCount: Int = 0,
    val boundaryEdgeCount: Int = 0,
    val loopCount: Int = 0,
    val ringPointCount: Int = 0,
    val resolvedAmbiguousVertexCount: Int = 0,
)
