package com.github.arhor.journey.domain.model

enum class DistanceUnit {
    METRIC,
    IMPERIAL,
}

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
)

