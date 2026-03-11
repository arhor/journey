package com.github.arhor.journey.domain.settings.model

enum class DistanceUnit {
    METRIC,
    IMPERIAL,
    ;

    companion object {
        private val distanceUnitMap = entries.associateBy(DistanceUnit::name)

        fun fromString(type: String): DistanceUnit? = distanceUnitMap[type]
    }
}
