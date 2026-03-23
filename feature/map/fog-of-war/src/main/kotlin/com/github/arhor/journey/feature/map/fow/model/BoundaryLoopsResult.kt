package com.github.arhor.journey.feature.map.fow.model

internal data class BoundaryLoopsResult(
    val loops: List<List<GridPoint>>,
    val boundaryEdgeCount: Int,
)
