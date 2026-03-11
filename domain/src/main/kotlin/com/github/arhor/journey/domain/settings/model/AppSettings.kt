package com.github.arhor.journey.domain.settings.model

import com.github.arhor.journey.domain.settings.model.DistanceUnit

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
)
