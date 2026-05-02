package com.github.arhor.journey.feature.map

import com.github.arhor.journey.domain.model.GeoBounds
import javax.inject.Inject

private const val DEFAULT_QUERY_BUFFER_FRACTION = 0.5
private const val MIN_LONGITUDE = -180.0
private const val MAX_LONGITUDE = 180.0 - 1e-9
private const val MIN_LATITUDE = -85.05112878
private const val MAX_LATITUDE = 85.05112878

class MapObjectQueryWindowPolicy @Inject constructor() {

    fun resolveQueryWindow(
        visibleBounds: GeoBounds,
        currentQueryWindow: GeoBounds?,
    ): GeoBounds = currentQueryWindow
        ?.takeIf { it.containsInclusive(visibleBounds) }
        ?: visibleBounds.expandedBy(
            horizontalFraction = DEFAULT_QUERY_BUFFER_FRACTION,
            verticalFraction = DEFAULT_QUERY_BUFFER_FRACTION,
        )

    private fun GeoBounds.expandedBy(
        horizontalFraction: Double,
        verticalFraction: Double,
    ): GeoBounds {
        val longitudePadding = (east - west) * horizontalFraction
        val latitudePadding = (north - south) * verticalFraction

        return GeoBounds(
            south = (south - latitudePadding).coerceAtLeast(MIN_LATITUDE),
            west = (west - longitudePadding).coerceAtLeast(MIN_LONGITUDE),
            north = (north + latitudePadding).coerceAtMost(MAX_LATITUDE),
            east = (east + longitudePadding).coerceAtMost(MAX_LONGITUDE),
        )
    }

    private fun GeoBounds.containsInclusive(other: GeoBounds): Boolean =
        other.south >= south &&
            other.west >= west &&
            other.north <= north &&
            other.east <= east
}
