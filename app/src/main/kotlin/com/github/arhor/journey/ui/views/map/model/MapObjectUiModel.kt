package com.github.arhor.journey.ui.views.map.model

import androidx.compose.runtime.Immutable

@Immutable
data class MapObjectUiModel(
    val id: String,
    val title: String,
    val description: String?,
    val position: LatLng,
    val radiusMeters: Int,
    val isDiscovered: Boolean,
)
