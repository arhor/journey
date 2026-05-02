package com.github.arhor.journey.domain.model

data class UserLocationFix(
    val location: GeoPoint,
    val horizontalAccuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val bearingDegrees: Double? = null,
    val bearingAccuracyDegrees: Double? = null,
    val elapsedRealtimeNanos: Long? = null,
)
