package com.github.arhor.journey.data.spatial

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.spatial.H3Grid
import com.uber.h3core.H3Core
import com.uber.h3core.LengthUnit
import com.uber.h3core.PolygonToCellsFlags
import com.uber.h3core.util.LatLng
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UberH3Grid internal constructor(
    private val h3: H3Core,
) : H3Grid {

    @Inject
    constructor() : this(H3Core.newInstance())

    override fun cellId(lat: Double, lon: Double, resolution: Int): String =
        h3.latLngToCellAddress(lat, lon, resolution)

    override fun cellCenter(cellId: String): GeoPoint {
        val center = h3.cellToLatLng(cellId)
        return GeoPoint(lat = center.lat, lon = center.lng)
    }

    override fun cellBoundary(cellId: String): List<GeoPoint> =
        h3.cellToBoundary(cellId).map { point ->
            GeoPoint(lat = point.lat, lon = point.lng)
        }

    override fun gridDisk(cellId: String, radius: Int): List<String> =
        h3.gridDisk(cellId, radius.coerceAtLeast(0))

    override fun gridDistance(originCellId: String, destinationCellId: String): Long =
        h3.gridDistance(originCellId, destinationCellId)

    override fun averageEdgeLengthMeters(resolution: Int): Double =
        h3.getHexagonEdgeLengthAvg(resolution, LengthUnit.m)

    override fun cellsInBounds(bounds: GeoBounds, resolution: Int): List<String> {
        val outline = listOf(
            LatLng(bounds.south, bounds.west),
            LatLng(bounds.south, bounds.east),
            LatLng(bounds.north, bounds.east),
            LatLng(bounds.north, bounds.west),
        )

        return h3.polygonToCellAddressesExperimental(
            outline,
            null,
            resolution,
            PolygonToCellsFlags.containment_overlapping,
        )
            .distinct()
            .sorted()
    }
}
