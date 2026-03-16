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
}
