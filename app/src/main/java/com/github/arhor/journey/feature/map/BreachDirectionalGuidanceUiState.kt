package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable

sealed interface BreachDirectionalGuidanceUiState {

    @Immutable
    data object Hidden : BreachDirectionalGuidanceUiState

    @Immutable
    data class Unavailable(
        val breachNodeId: String,
        val districtName: String,
        val message: String,
    ) : BreachDirectionalGuidanceUiState

    @Immutable
    data class FloatingArrow(
        val breachNodeId: String,
        val districtName: String,
        val bearingDegrees: Double,
        val distanceMeters: Int,
        val canStartUpload: Boolean,
    ) : BreachDirectionalGuidanceUiState

    @Immutable
    data class OnTarget(
        val breachNodeId: String,
        val districtName: String,
        val distanceMeters: Int,
        val canStartUpload: Boolean,
    ) : BreachDirectionalGuidanceUiState
}
