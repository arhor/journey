package com.github.arhor.journey.domain.spatial

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint

interface H3Grid {
    fun cellId(lat: Double, lon: Double, resolution: Int): String
    fun cellCenter(cellId: String): GeoPoint
    fun cellBoundary(cellId: String): List<GeoPoint>
    fun gridDisk(cellId: String, radius: Int): List<String>
    fun gridDistance(originCellId: String, destinationCellId: String): Long
    fun averageEdgeLengthMeters(resolution: Int): Double
    fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String>
}
