package com.github.arhor.journey.feature.map.model

import androidx.compose.runtime.Immutable
import com.github.arhor.journey.core.common.ResourceType

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
    val resourceType: ResourceType? = null,
)

enum class MapObjectKind(
    val idPrefix: String,
) {
    PointOfInterest(idPrefix = "poi"),
    ResourceSpawn(idPrefix = "spawn"),
}
