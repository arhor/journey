package com.github.arhor.journey.feature.map

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.domain.model.PoiCategory

@Immutable
data class AddPoiUiState(
    val name: String = "",
    val description: String = "",
    val selectedCategory: PoiCategory = PoiCategory.LANDMARK,
    val radiusMeters: String = "50",
    val latitude: String,
    val longitude: String,
    val isSaving: Boolean = false,
)
