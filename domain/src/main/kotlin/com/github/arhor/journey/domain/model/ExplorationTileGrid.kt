package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.internal.COS_EPSILON
import com.github.arhor.journey.domain.internal.EARTH_RADIUS_METERS
import com.github.arhor.journey.domain.internal.FULL_LONGITUDE_SPAN
import com.github.arhor.journey.domain.internal.HALF_LONGITUDE_SPAN
import com.github.arhor.journey.domain.internal.LAT_EPSILON
import com.github.arhor.journey.domain.internal.LON_EPSILON
import com.github.arhor.journey.domain.internal.MAX_LAT
import com.github.arhor.journey.domain.internal.MAX_LON
import com.github.arhor.journey.domain.internal.METERS_PER_LAT_DEGREE
import com.github.arhor.journey.domain.internal.MIN_LAT
import com.github.arhor.journey.domain.internal.MIN_LON
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sinh
import kotlin.math.tan

object ExplorationTileGrid {
    fun tileAt(
        point: GeoPoint,
        zoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    ): ExplorationTile = ExplorationTile(
        zoom = zoom,
        x = longitudeToTileX(point.lon, zoom),
        y = latitudeToTileY(point.lat, zoom),
    )

    fun tileRange(
        bounds: GeoBounds,
        zoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    ): ExplorationTileRange = ExplorationTileRange(
        zoom = zoom,
        minX = longitudeToTileX(bounds.west, zoom),
        maxX = longitudeToTileX(bounds.east - LON_EPSILON, zoom),
        minY = latitudeToTileY(bounds.north, zoom),
        maxY = latitudeToTileY(bounds.south + LAT_EPSILON, zoom),
    )

    fun bounds(tile: ExplorationTile): GeoBounds {
        val tilesPerAxis = tilesPerAxis(tile.zoom).toDouble()
        val west = tile.x / tilesPerAxis * FULL_LONGITUDE_SPAN - HALF_LONGITUDE_SPAN
        val east = (tile.x + 1) / tilesPerAxis * FULL_LONGITUDE_SPAN - HALF_LONGITUDE_SPAN
        val north = tileYToLatitude(tile.y, tile.zoom)
        val south = tileYToLatitude(tile.y + 1, tile.zoom)

        return GeoBounds(
            south = south,
            west = west,
            north = north,
            east = east,
        )
    }

    fun bounds(range: ExplorationTileRange): GeoBounds {
        val northwestBounds = bounds(
            ExplorationTile(
                zoom = range.zoom,
                x = range.minX,
                y = range.minY,
            ),
        )
        val southeastBounds = bounds(
            ExplorationTile(
                zoom = range.zoom,
                x = range.maxX,
                y = range.maxY,
            ),
        )

        return GeoBounds(
            south = southeastBounds.south,
            west = northwestBounds.west,
            north = northwestBounds.north,
            east = southeastBounds.east,
        )
    }

    fun tilesInBounds(
        bounds: GeoBounds,
        zoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    ): Set<ExplorationTile> = tileRange(bounds, zoom).asSequence().toSet()

    fun revealTilesAround(
        point: GeoPoint,
        radiusMeters: Double = ExplorationTilePrototype.REVEAL_RADIUS_METERS,
        zoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    ): Set<ExplorationTile> {
        val normalizedRadiusMeters = radiusMeters.coerceAtLeast(0.0)

        return tilesInBounds(
            bounds = revealBounds(point, normalizedRadiusMeters),
            zoom = zoom,
        ).filterTo(mutableSetOf()) { tile ->
            intersectsRevealCircle(
                point = point,
                radiusMeters = normalizedRadiusMeters,
                tile = tile,
            )
        }
    }

    fun lonDistanceMeters(
        point: GeoPoint,
        lon: Double,
    ): Double {
        val latRadians = Math.toRadians(point.lat)
        val metersPerLongitudeDegree = cos(latRadians) * METERS_PER_LAT_DEGREE
        return abs(lon - point.lon) * metersPerLongitudeDegree
    }

    fun latDistanceMeters(
        point: GeoPoint,
        latitude: Double,
    ): Double = abs(latitude - point.lat) * METERS_PER_LAT_DEGREE

    private fun revealBounds(
        point: GeoPoint,
        radiusMeters: Double,
    ): GeoBounds {
        val clampedLatitude = point.lat.coerceIn(MIN_LAT, MAX_LAT)
        val latitudeRadians = Math.toRadians(clampedLatitude)
        val latitudeDelta = radiusMeters / EARTH_RADIUS_METERS
        val longitudeDelta = radiusMeters / (EARTH_RADIUS_METERS * cos(latitudeRadians).coerceAtLeast(COS_EPSILON))

        return GeoBounds(
            south = (clampedLatitude - Math.toDegrees(latitudeDelta)).coerceIn(MIN_LAT, MAX_LAT),
            west = (point.lon - Math.toDegrees(longitudeDelta)).coerceIn(MIN_LON, MAX_LON),
            north = (clampedLatitude + Math.toDegrees(latitudeDelta)).coerceIn(MIN_LAT, MAX_LAT),
            east = (point.lon + Math.toDegrees(longitudeDelta)).coerceIn(MIN_LON, MAX_LON),
        )
    }

    private fun intersectsRevealCircle(
        point: GeoPoint,
        radiusMeters: Double,
        tile: ExplorationTile,
    ): Boolean {
        val tileBounds = bounds(tile)
        val clampedLat = point.lat.coerceIn(MIN_LAT, MAX_LAT)
        val metersPerLonDegree = cos(Math.toRadians(clampedLat)).coerceAtLeast(COS_EPSILON) * METERS_PER_LAT_DEGREE

        val westMeters = (tileBounds.west - point.lon) * metersPerLonDegree
        val eastMeters = (tileBounds.east - point.lon) * metersPerLonDegree
        val southMeters = (tileBounds.south - clampedLat) * METERS_PER_LAT_DEGREE
        val northMeters = (tileBounds.north - clampedLat) * METERS_PER_LAT_DEGREE

        val closestXMeters = 0.0.coerceIn(westMeters, eastMeters)
        val closestYMeters = 0.0.coerceIn(southMeters, northMeters)
        val distanceSquaredMeters =
            closestXMeters * closestXMeters + closestYMeters * closestYMeters

        return distanceSquaredMeters <= radiusMeters * radiusMeters
    }

    private fun longitudeToTileX(
        longitude: Double,
        zoom: Int,
    ): Int {
        val tilesPerAxis = tilesPerAxis(zoom)
        val normalizedLongitude = longitude.coerceIn(MIN_LON, MAX_LON)
        val normalizedX = (normalizedLongitude + HALF_LONGITUDE_SPAN) / FULL_LONGITUDE_SPAN * tilesPerAxis
        return floor(normalizedX)
            .toInt()
            .coerceIn(0, tilesPerAxis - 1)
    }

    private fun latitudeToTileY(
        latitude: Double,
        zoom: Int,
    ): Int {
        val tilesPerAxis = tilesPerAxis(zoom)
        val clampedLatitude = latitude.coerceIn(MIN_LAT, MAX_LAT)
        val latitudeRadians = Math.toRadians(clampedLatitude)
        val mercatorY = (1.0 - asinh(tan(latitudeRadians)) / PI) / 2.0 * tilesPerAxis
        return floor(mercatorY)
            .toInt()
            .coerceIn(0, tilesPerAxis - 1)
    }

    private fun tileYToLatitude(
        y: Int,
        zoom: Int,
    ): Double {
        val tilesPerAxis = tilesPerAxis(zoom).toDouble()
        val mercator = PI * (1.0 - 2.0 * y / tilesPerAxis)
        return Math.toDegrees(atan(sinh(mercator)))
    }

    private fun tilesPerAxis(zoom: Int): Int = 1 shl zoom
}
