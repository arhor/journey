package com.github.arhor.journey.domain.internal

import com.github.arhor.journey.domain.CANONICAL_ZOOM
import com.github.arhor.journey.domain.REVEAL_RADIUS_METERS
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan

const val EARTH_RADIUS_METERS = 6_371_000.0
const val EARTH_DIAMETER_METERS = EARTH_RADIUS_METERS * 2.0
const val HALF_LONGITUDE_SPAN = 180.0
const val FULL_LONGITUDE_SPAN = 360.0

const val MIN_LON = -180.0
const val MAX_LON = 180.0 - 1e-9

const val MIN_LAT = -85.05112878
const val MAX_LAT = 85.05112878

const val DEGREES_PER_RADIAN = 180.0 / PI
const val METERS_PER_LAT_DEGREE = EARTH_RADIUS_METERS / DEGREES_PER_RADIAN

const val COS_EPSILON = 1e-6
const val LAT_EPSILON = 1e-9
const val LON_EPSILON = 1e-9

fun haversine(
    srcDegrees: Double,
    dstDegrees: Double,
): Double {
    val dDeg = dstDegrees - srcDegrees
    val dRad = Math.toRadians(dDeg)
    val sinHalfDelta = sin(dRad / 2.0)

    return sinHalfDelta * sinHalfDelta
}

fun haversine(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double,
): Double {
    val latHav = haversine(srcDegrees = lat1, dstDegrees = lat2)
    val lonHav = haversine(srcDegrees = lon1, dstDegrees = lon2)
    val lat1Cos = cos(Math.toRadians(lat1))
    val lat2Cos = cos(Math.toRadians(lat2))

    return latHav + lat1Cos * lat2Cos * lonHav
}

fun distanceMeters(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double,
): Double {
    val hav = haversine(lat1 = lat1, lon1 = lon1, lat2 = lat2, lon2 = lon2)
    val havSafe = hav.coerceIn(0.0, 1.0)

    return EARTH_DIAMETER_METERS * asin(sqrt(havSafe))
}

fun tileAt(
    point: GeoPoint,
    zoom: Int = CANONICAL_ZOOM,
): MapTile = MapTile(
    zoom = zoom,
    x = longitudeToTileX(point.lon, zoom),
    y = latitudeToTileY(point.lat, zoom),
)

fun tileRange(
    bounds: GeoBounds,
    zoom: Int = CANONICAL_ZOOM,
): ExplorationTileRange = ExplorationTileRange(
    zoom = zoom,
    minX = longitudeToTileX(bounds.west, zoom),
    maxX = longitudeToTileX(bounds.east - LON_EPSILON, zoom),
    minY = latitudeToTileY(bounds.north, zoom),
    maxY = latitudeToTileY(bounds.south + LAT_EPSILON, zoom),
)

fun bounds(tile: MapTile): GeoBounds {
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
        MapTile(
            zoom = range.zoom,
            x = range.minX,
            y = range.minY,
        ),
    )
    val southeastBounds = bounds(
        MapTile(
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
    zoom: Int = CANONICAL_ZOOM,
): Set<MapTile> = tileRange(bounds, zoom).asSequence().toSet()

fun revealTilesAround(
    point: GeoPoint,
    radiusMeters: Double = REVEAL_RADIUS_METERS,
    zoom: Int = CANONICAL_ZOOM,
): Set<MapTile> {
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
    tile: MapTile,
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
    val distanceSquaredMeters = closestXMeters * closestXMeters + closestYMeters * closestYMeters

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
