package com.github.arhor.journey.domain.model

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.max
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

    fun playerLightContributionsAt(
        point: GeoPoint,
        zoom: Int = ExplorationTilePrototype.CANONICAL_ZOOM,
    ): Set<ExplorationTileLight> {
        val centerTile = tileAt(
            point = point,
            zoom = zoom,
        )
        val maxTileIndex = tilesPerAxis(zoom) - 1

        return buildSet {
            PLAYER_LIGHT_BY_RING.forEachIndexed { ring, light ->
                val minX = (centerTile.x - ring).coerceAtLeast(0)
                val maxX = (centerTile.x + ring).coerceAtMost(maxTileIndex)
                val minY = (centerTile.y - ring).coerceAtLeast(0)
                val maxY = (centerTile.y + ring).coerceAtMost(maxTileIndex)

                for (y in minY..maxY) {
                    for (x in minX..maxX) {
                        val distance = max(
                            abs(x - centerTile.x),
                            abs(y - centerTile.y),
                        )

                        if (distance == ring) {
                            add(
                                ExplorationTileLight(
                                    tile = ExplorationTile(
                                        zoom = zoom,
                                        x = x,
                                        y = y,
                                    ),
                                    light = light,
                                ),
                            )
                        }
                    }
                }
            }
        }
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

    private const val HALF_LONGITUDE_SPAN = 180.0
    private const val FULL_LONGITUDE_SPAN = 360.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0 - 1e-9
    private const val MIN_LATITUDE = -85.05112878
    private const val MAX_LATITUDE = 85.05112878
    private const val DEGREES_PER_RADIAN = 180.0 / PI
    private const val LATITUDE_EPSILON = 1e-9
    private const val LONGITUDE_EPSILON = 1e-9
    private val PLAYER_LIGHT_BY_RING = listOf(
        ExplorationTilePrototype.CURRENT_TILE_LIGHT,
        ExplorationTilePrototype.FIRST_RING_LIGHT,
        ExplorationTilePrototype.SECOND_RING_LIGHT,
    )
}
