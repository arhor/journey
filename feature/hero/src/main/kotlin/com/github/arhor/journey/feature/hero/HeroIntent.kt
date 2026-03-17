package com.github.arhor.journey.feature.hero

import com.github.arhor.journey.domain.model.ActivityType

sealed interface HeroIntent {
    data class SelectActivityType(
        val type: ActivityType,
    ) : HeroIntent

    data class ChangeDurationMinutes(
        val value: String,
    ) : HeroIntent

    data object SubmitActivity : HeroIntent
}
