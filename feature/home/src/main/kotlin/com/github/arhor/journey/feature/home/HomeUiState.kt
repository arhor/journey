package com.github.arhor.journey.feature.home

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.ActivityType

sealed interface HomeUiState {

    @Immutable
    data object Loading : HomeUiState

    @Immutable
    data class Failure(val errorMessage: String) : HomeUiState

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
    ) : HomeUiState
}
