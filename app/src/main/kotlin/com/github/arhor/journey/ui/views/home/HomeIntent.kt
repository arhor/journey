package com.github.arhor.journey.ui.views.home

import com.github.arhor.journey.domain.activity.model.ActivityType

sealed interface HomeIntent {
    data class SelectActivityType(
        val type: ActivityType,
    ) : HomeIntent

    data class ChangeDurationMinutes(
        val value: String,
    ) : HomeIntent

    data object SubmitActivity : HomeIntent
}
