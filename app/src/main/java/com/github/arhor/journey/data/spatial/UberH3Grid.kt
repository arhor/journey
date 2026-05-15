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
class UberH3Grid @Inject constructor() : H3Grid {

    override fun cellId(lat: Double, lon: Double, resolution: Int): String =
        h3.latLngToCellAddress(lat, lon, resolution)

    override fun cellResolution(cellId: String): Int =
        h3.getResolution(cellId)

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

    companion object {
        private val h3: H3Core by lazy {
            // Workaround for h3-android native artifact missing NEEDED libm.so.
            // libh3-java.so references math symbol `cos`, but the published Android
            // library does not declare libm.so in DT_NEEDED. Loading libc++_shared
            // first makes the required runtime dependency available before H3 loads.
            System.loadLibrary("c++_shared")
            H3Core.newSystemInstance()
        }
    }
}
