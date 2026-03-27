package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.internal.distanceMeters

data class GeoPoint(
    val lat: Double,
    val lon: Double,
) {
    fun distanceTo(that: GeoPoint): Double = distanceMeters(
        lat1 = lat,
        lon1 = lon,
        lat2 = that.lat,
        lon2 = that.lon
    )
}
