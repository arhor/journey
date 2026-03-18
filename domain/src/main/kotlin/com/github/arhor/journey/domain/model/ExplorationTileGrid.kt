package com.github.arhor.journey.domain.model

import kotlin.math.PI
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
        maxX = longitudeToTileX(bounds.east - LONGITUDE_EPSILON, zoom),
        minY = latitudeToTileY(bounds.north, zoom),
        maxY = latitudeToTileY(bounds.south + LATITUDE_EPSILON, zoom),
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
    ): Set<ExplorationTile> = tilesInBounds(
        bounds = revealBounds(point, radiusMeters),
        zoom = zoom,
    )

    private fun revealBounds(
        point: GeoPoint,
        radiusMeters: Double,
    ): GeoBounds {
        val clampedLatitude = point.lat.coerceIn(MIN_LATITUDE, MAX_LATITUDE)
        val latitudeRadians = clampedLatitude.toRadians()
        val latitudeDelta = radiusMeters / EARTH_RADIUS_METERS
        val longitudeDelta = radiusMeters / (EARTH_RADIUS_METERS * cos(latitudeRadians).coerceAtLeast(COSINE_EPSILON))

        return GeoBounds(
            south = (clampedLatitude - latitudeDelta.toDegrees()).coerceIn(MIN_LATITUDE, MAX_LATITUDE),
            west = (point.lon - longitudeDelta.toDegrees()).coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
            north = (clampedLatitude + latitudeDelta.toDegrees()).coerceIn(MIN_LATITUDE, MAX_LATITUDE),
            east = (point.lon + longitudeDelta.toDegrees()).coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
        )
    }

    private fun longitudeToTileX(
        longitude: Double,
        zoom: Int,
    ): Int {
        val tilesPerAxis = tilesPerAxis(zoom)
        val normalizedLongitude = longitude.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE)
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
        val clampedLatitude = latitude.coerceIn(MIN_LATITUDE, MAX_LATITUDE)
        val latitudeRadians = clampedLatitude.toRadians()
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
        return atan(sinh(mercator)).toDegrees()
    }

    private fun tilesPerAxis(zoom: Int): Int = 1 shl zoom

    private fun Double.toRadians(): Double = this / DEGREES_PER_RADIAN

    private fun Double.toDegrees(): Double = this * DEGREES_PER_RADIAN

    private const val EARTH_RADIUS_METERS = 6_378_137.0
    private const val HALF_LONGITUDE_SPAN = 180.0
    private const val FULL_LONGITUDE_SPAN = 360.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0 - 1e-9
    private const val MIN_LATITUDE = -85.05112878
    private const val MAX_LATITUDE = 85.05112878
    private const val DEGREES_PER_RADIAN = 180.0 / PI
    private const val COSINE_EPSILON = 1e-6
    private const val LATITUDE_EPSILON = 1e-9
    private const val LONGITUDE_EPSILON = 1e-9
}
