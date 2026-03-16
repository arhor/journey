package com.github.arhor.journey.feature.hero

import com.github.arhor.journey.domain.model.ActivityType

sealed interface HomeIntent {
    data class SelectActivityType(
        val type: ActivityType,
    ) : HomeIntent

    data class ChangeDurationMinutes(
        val value: String,
    ) : HomeIntent

    data object SubmitActivity : HomeIntent
}
