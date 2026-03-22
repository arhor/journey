package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.core.common.ResourceType

sealed interface MapHudUiState {

    @Immutable
    data object Loading : MapHudUiState

    @Immutable
    data object Unavailable : MapHudUiState

    @Immutable
    data class Content(
        val heroInitial: String,
        val levelLabel: String,
        val resources: List<MapHudResourceUiModel>,
    ) : MapHudUiState
}

@Immutable
data class MapHudResourceUiModel(
    val resourceType: ResourceType,
    val amount: Int,
    val amountLabel: String,
)
