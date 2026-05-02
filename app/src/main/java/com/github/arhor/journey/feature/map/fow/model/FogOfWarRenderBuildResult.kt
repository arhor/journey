package com.github.arhor.journey.feature.map.fow.model

internal data class FogOfWarRenderBuildResult(
    val renderData: FogOfWarRenderData,
    val renderMode: FogOfWarRenderMode,
    val expandedFogCellCount: Long,
    val connectedRegionCount: Int,
    val boundaryEdgeCount: Int,
    val loopCount: Int,
    val featureCount: Int,
    val ringPointCount: Int,
    val resolvedAmbiguousVertexCount: Int,
)
