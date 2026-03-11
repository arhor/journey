package com.github.arhor.journey.domain.settings.model

import com.github.arhor.journey.domain.map.model.MapStyle

data class AppSettings(
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val selectedMapStyleId: String = MapStyle.DEFAULT_ID,
)
