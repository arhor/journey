package com.github.arhor.journey.feature.map.model

import androidx.compose.runtime.Immutable

@Immutable
data class MapObjectUiModel(
    val id: String,
    val kind: MapObjectKind,
    val title: String,
    val description: String?,
    val position: LatLng,
    val radiusMeters: Int,
    val isDiscovered: Boolean,
    val isHiddenByFog: Boolean = false,
)

enum class MapObjectKind(
    val idPrefix: String,
) {
    GenericObject(idPrefix = "obj"),
}
