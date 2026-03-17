package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable

@Immutable
sealed interface PoiDetailsUiState {
    @Immutable
    data object Loading : PoiDetailsUiState

    @Immutable
    data class Failure(
        val errorMessage: String,
    ) : PoiDetailsUiState

    @Immutable
    data class Content(
        val id: String,
        val name: String,
        val description: String?,
        val category: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Int,
    ) : PoiDetailsUiState
}
