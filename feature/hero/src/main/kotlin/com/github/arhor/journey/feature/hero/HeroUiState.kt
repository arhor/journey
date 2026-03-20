package com.github.arhor.journey.feature.hero

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.core.common.ResourceType

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
        val resources: List<HeroResourceAmountUiModel>,
    ) : HeroUiState
}

@Immutable
data class HeroResourceAmountUiModel(
    val resourceType: ResourceType,
    val amount: Int,
)
