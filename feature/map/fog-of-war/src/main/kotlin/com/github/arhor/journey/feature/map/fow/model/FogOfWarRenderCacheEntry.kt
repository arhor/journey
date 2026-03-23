package com.github.arhor.journey.feature.map.fow.model

internal data class FogOfWarRenderCacheEntry(
    val renderData: FogOfWarRenderData,
    val expandedFogCellCount: Long,
    val connectedRegionCount: Int,
    val boundaryEdgeCount: Int,
    val loopCount: Int,
    val featureCount: Int,
    val ringPointCount: Int,
)
