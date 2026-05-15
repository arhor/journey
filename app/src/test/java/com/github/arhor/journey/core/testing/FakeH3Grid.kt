package com.github.arhor.journey.core.testing

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.spatial.H3Grid
import kotlin.math.abs

class FakeH3Grid(
    private val originCell: String = "origin",
    private val disk: List<String> = listOf(originCell),
    private val centers: Map<String, GeoPoint> = emptyMap(),
    private val boundaries: Map<String, List<GeoPoint>> = emptyMap(),
    private val cellsInBounds: List<String> = disk,
    private val averageEdgeLengthMeters: Double = 180.0,
) : H3Grid {

    override fun cellId(lat: Double, lon: Double, resolution: Int): String = originCell

    override fun cellCenter(cellId: String): GeoPoint =
        centers[cellId] ?: GeoPoint(
            lat = 50.45 + (abs(cellId.hashCode()) % 100) * 0.00001,
            lon = 30.52,
        )

    override fun cellBoundary(cellId: String): List<GeoPoint> =
        boundaries[cellId] ?: hexAround(cellCenter(cellId))

    override fun gridDisk(cellId: String, radius: Int): List<String> = disk

    override fun gridDistance(originCellId: String, destinationCellId: String): Long =
        disk.indexOf(destinationCellId).takeIf { it >= 0 }?.toLong() ?: Long.MAX_VALUE

    override fun averageEdgeLengthMeters(resolution: Int): Double = averageEdgeLengthMeters

    override fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String> = cellsInBounds

    companion object {
        fun withRepeatedCells(): FakeH3Grid =
            FakeH3Grid(
                disk = (0..200).map { index -> "cell-$index" },
                cellsInBounds = (0..200).map { index -> "cell-$index" },
            )
    }
}

fun hexAround(center: GeoPoint): List<GeoPoint> =
    listOf(
        GeoPoint(center.lat + 0.001, center.lon),
        GeoPoint(center.lat + 0.0005, center.lon + 0.001),
        GeoPoint(center.lat - 0.0005, center.lon + 0.001),
        GeoPoint(center.lat - 0.001, center.lon),
        GeoPoint(center.lat - 0.0005, center.lon - 0.001),
        GeoPoint(center.lat + 0.0005, center.lon - 0.001),
    )
