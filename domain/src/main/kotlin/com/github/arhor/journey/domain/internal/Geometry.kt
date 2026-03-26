package com.github.arhor.journey.domain.internal

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
