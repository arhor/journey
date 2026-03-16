package com.github.arhor.journey.feature.hero

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ActivityType

sealed interface HeroUiState {

    @Immutable
    data object Loading : HeroUiState

    @Immutable
    data class Failure(val errorMessage: String) : HeroUiState

    @Immutable
    data class Content(
        val heroName: String,
        val level: Int,
        val xpInLevel: Long,
        val xpToNextLevel: Long,
        val strength: Int,
        val vitality: Int,
        val dexterity: Int,
        val stamina: Int,
        val selectedActivityType: ActivityType,
        val durationMinutesInput: String,
        val isSubmitting: Boolean,
        val importedTodayActivities: Int,
        val importedTodaySteps: Long,
    ) : HeroUiState
}
