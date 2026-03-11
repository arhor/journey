package com.github.arhor.journey.ui.views.settings

import com.github.arhor.journey.domain.settings.model.DistanceUnit

sealed interface SettingsIntent {

    data class SelectDistanceUnit(val unit: DistanceUnit) : SettingsIntent

    data class SelectMapStyle(val styleId: String) : SettingsIntent
}
