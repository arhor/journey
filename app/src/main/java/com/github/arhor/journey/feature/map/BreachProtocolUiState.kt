package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable

sealed interface BreachProtocolUiState {

    @Immutable
    data object Idle : BreachProtocolUiState

    @Immutable
    data object Scanning : BreachProtocolUiState

    @Immutable
    data class SignalLocked(
        val breachNodeId: String,
        val districtName: String,
        val distanceMeters: Int?,
        val canStartUpload: Boolean,
        val disabledReason: String?,
    ) : BreachProtocolUiState

    @Immutable
    data class Uploading(
        val breachNodeId: String,
        val districtName: String,
        val progressPercent: Int,
    ) : BreachProtocolUiState

    @Immutable
    data class Completed(
        val districtName: String,
    ) : BreachProtocolUiState
}
