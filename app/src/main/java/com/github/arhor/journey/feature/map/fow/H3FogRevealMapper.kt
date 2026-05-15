package com.github.arhor.journey.feature.map.fow

import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.internal.tilesInBounds
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.spatial.H3Grid
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class H3FogRevealMapper @Inject constructor(
    private val h3Grid: H3Grid,
) {

    fun revealTilesForCells(
        h3CellIds: Set<String>,
        canonicalZoom: Int,
    ): Set<MapTile> = h3CellIds
        .asSequence()
        .flatMap { cellId ->
            revealTilesForCell(
                cellId = cellId,
                canonicalZoom = canonicalZoom,
            ).asSequence()
        }
        .toCollection(linkedSetOf())

    private fun revealTilesForCell(
        cellId: String,
        canonicalZoom: Int,
    ): Set<MapTile> {
        val boundary = h3Grid.cellBoundary(cellId)
        if (boundary.size < 3) {
            return emptySet()
        }

        return tilesInBounds(
            bounds = boundaryBounds(boundary),
            zoom = canonicalZoom,
        ).filterTo(linkedSetOf()) { tile ->
            tileIntersectsPolygon(
                tile = tile,
                polygon = boundary,
            )
        }
    }

    private fun boundaryBounds(boundary: List<GeoPoint>): GeoBounds = GeoBounds(
        south = boundary.minOf(GeoPoint::lat),
        west = boundary.minOf(GeoPoint::lon),
        north = boundary.maxOf(GeoPoint::lat),
        east = boundary.maxOf(GeoPoint::lon),
    )

    private fun tileIntersectsPolygon(
        tile: MapTile,
        polygon: List<GeoPoint>,
    ): Boolean {
        val tileBounds = bounds(tile)
        val tileCorners = tileCorners(tileBounds)

        return tileCorners.any { corner -> polygonContainsPoint(polygon, corner) } ||
            polygon.any(tileBounds::contains) ||
            polygonEdges(polygon).any { polygonEdge ->
                tileEdges(tileCorners).any { tileEdge ->
                    segmentsIntersect(
                        leftStart = polygonEdge.first,
                        leftEnd = polygonEdge.second,
                        rightStart = tileEdge.first,
                        rightEnd = tileEdge.second,
                    )
                }
            }
    }

    private fun tileCorners(bounds: GeoBounds): List<GeoPoint> = listOf(
        GeoPoint(lat = bounds.north, lon = bounds.west),
        GeoPoint(lat = bounds.north, lon = bounds.east),
        GeoPoint(lat = bounds.south, lon = bounds.east),
        GeoPoint(lat = bounds.south, lon = bounds.west),
    )

    private fun polygonEdges(polygon: List<GeoPoint>): List<Pair<GeoPoint, GeoPoint>> =
        polygon.indices.map { index ->
            polygon[index] to polygon[(index + 1) % polygon.size]
        }

    private fun tileEdges(corners: List<GeoPoint>): List<Pair<GeoPoint, GeoPoint>> =
        corners.indices.map { index ->
            corners[index] to corners[(index + 1) % corners.size]
        }

    private fun polygonContainsPoint(
        polygon: List<GeoPoint>,
        point: GeoPoint,
    ): Boolean {
        var inside = false
        for (index in polygon.indices) {
            val start = polygon[index]
            val end = polygon[(index + 1) % polygon.size]

            if (pointOnSegment(point, start, end)) {
                return true
            }

            val intersects = ((start.lat > point.lat) != (end.lat > point.lat)) &&
                (
                    point.lon <
                        (end.lon - start.lon) * (point.lat - start.lat) / (end.lat - start.lat) + start.lon
                    )
            if (intersects) {
                inside = !inside
            }
        }

        return inside
    }

    private fun segmentsIntersect(
        leftStart: GeoPoint,
        leftEnd: GeoPoint,
        rightStart: GeoPoint,
        rightEnd: GeoPoint,
    ): Boolean {
        val leftOrientationStart = orientation(leftStart, leftEnd, rightStart)
        val leftOrientationEnd = orientation(leftStart, leftEnd, rightEnd)
        val rightOrientationStart = orientation(rightStart, rightEnd, leftStart)
        val rightOrientationEnd = orientation(rightStart, rightEnd, leftEnd)

        if (leftOrientationStart == 0.0 && pointOnSegment(rightStart, leftStart, leftEnd)) {
            return true
        }
        if (leftOrientationEnd == 0.0 && pointOnSegment(rightEnd, leftStart, leftEnd)) {
            return true
        }
        if (rightOrientationStart == 0.0 && pointOnSegment(leftStart, rightStart, rightEnd)) {
            return true
        }
        if (rightOrientationEnd == 0.0 && pointOnSegment(leftEnd, rightStart, rightEnd)) {
            return true
        }

        return (leftOrientationStart > 0.0) != (leftOrientationEnd > 0.0) &&
            (rightOrientationStart > 0.0) != (rightOrientationEnd > 0.0)
    }

    private fun orientation(
        start: GeoPoint,
        end: GeoPoint,
        point: GeoPoint,
    ): Double = (end.lon - start.lon) * (point.lat - start.lat) -
        (end.lat - start.lat) * (point.lon - start.lon)

    private fun pointOnSegment(
        point: GeoPoint,
        start: GeoPoint,
        end: GeoPoint,
    ): Boolean {
        val orientation = orientation(start, end, point)
        if (abs(orientation) > GEOMETRY_EPSILON) {
            return false
        }

        return point.lat in minOf(start.lat, end.lat) - GEOMETRY_EPSILON..maxOf(start.lat, end.lat) + GEOMETRY_EPSILON &&
            point.lon in minOf(start.lon, end.lon) - GEOMETRY_EPSILON..maxOf(start.lon, end.lon) + GEOMETRY_EPSILON
    }

    private companion object {
        const val GEOMETRY_EPSILON = 1e-9
    }
}
