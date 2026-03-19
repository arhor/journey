package com.github.arhor.journey.domain.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_METERS = 6_371_000.0

fun GeoPoint.distanceTo(other: GeoPoint): Double {
    val latitudeDeltaRadians = Math.toRadians(other.lat - lat)
    val longitudeDeltaRadians = Math.toRadians(other.lon - lon)
    val startLatitudeRadians = Math.toRadians(lat)
    val endLatitudeRadians = Math.toRadians(other.lat)

    val haversine = sin(latitudeDeltaRadians / 2.0).pow(2.0) +
        cos(startLatitudeRadians) * cos(endLatitudeRadians) *
        sin(longitudeDeltaRadians / 2.0).pow(2.0)

    return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
