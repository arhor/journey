package com.github.arhor.journey.feature.settings

import com.github.arhor.journey.domain.model.DistanceUnit

sealed interface SettingsIntent {

    data class SelectDistanceUnit(val unit: DistanceUnit) : SettingsIntent

    data class SelectMapStyle(val styleId: String) : SettingsIntent
}
