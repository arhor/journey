package com.github.arhor.journey.domain.model

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val selectedMapStyleId: String = MapStyle.DEFAULT_ID,
)
