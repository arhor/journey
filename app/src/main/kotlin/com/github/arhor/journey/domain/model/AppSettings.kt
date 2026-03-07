package com.github.arhor.journey.domain.model

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val mapStyle: MapStyle = MapStyle.DEFAULT,
)
