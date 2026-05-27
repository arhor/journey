package com.github.arhor.journey.domain.model

import com.github.arhor.journey.domain.internal.distanceMeters
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

    fun bearingTo(that: GeoPoint): Double {
        val lat1 = Math.toRadians(lat)
        val lat2 = Math.toRadians(that.lat)
        val deltaLon = Math.toRadians(that.lon - lon)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val bearing = Math.toDegrees(atan2(y, x))

        return (bearing + 360.0) % 360.0
    }
}
