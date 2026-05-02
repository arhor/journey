package com.github.arhor.journey.domain.model

/**
 * Geographic bounds in WGS84 coordinates.
 *
 * The prototype keeps bounds north-aligned and does not model antimeridian wrapping.
 */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "south must be <= north" }
        require(west <= east) { "west must be <= east" }
    }

    fun contains(point: GeoPoint): Boolean =
        point.lat in south..north && point.lon in west..east

    fun intersect(other: GeoBounds): GeoBounds? {
        val intersectedSouth = maxOf(south, other.south)
        val intersectedWest = maxOf(west, other.west)
        val intersectedNorth = minOf(north, other.north)
        val intersectedEast = minOf(east, other.east)

        return if (intersectedSouth <= intersectedNorth && intersectedWest <= intersectedEast) {
            GeoBounds(
                south = intersectedSouth,
                west = intersectedWest,
                north = intersectedNorth,
                east = intersectedEast,
            )
        } else {
            null
        }
    }
}
