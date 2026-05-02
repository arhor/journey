package com.github.arhor.journey.feature.map.presentation

import com.github.arhor.journey.core.common.ResourceType
import com.github.arhor.journey.domain.internal.tileAt
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.Watchtower
import com.github.arhor.journey.domain.model.WatchtowerPhase
import com.github.arhor.journey.feature.map.model.LatLng
import com.github.arhor.journey.feature.map.model.MapObjectKind
import com.github.arhor.journey.feature.map.model.MapObjectUiModel
import com.github.arhor.journey.feature.map.model.WatchtowerMarkerState
import javax.inject.Inject
import kotlin.math.roundToInt

class MapWorldObjectPresenter @Inject constructor() {

    fun presentResourceSpawns(
        resourceSpawns: List<ResourceSpawn>,
        canonicalZoom: Int,
        visibilityTileMask: Set<MapTile>,
    ): List<MapObjectUiModel> =
        resourceSpawns.map { resourceSpawn ->
            presentResourceSpawn(
                resourceSpawn = resourceSpawn,
                isHiddenByFog = resourceSpawn.isHiddenByFog(
                    canonicalZoom = canonicalZoom,
                    visibilityTileMask = visibilityTileMask,
                ),
            )
        }

    fun presentResourceSpawn(
        resourceSpawn: ResourceSpawn,
        isHiddenByFog: Boolean,
    ): MapObjectUiModel {
        val resourceType = ResourceType.fromTypeId(resourceSpawn.typeId)

        return MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.ResourceSpawn,
                rawId = resourceSpawn.id,
            ),
            kind = MapObjectKind.ResourceSpawn,
            title = resourceType?.displayName ?: resourceSpawn.typeId,
            description = null,
            position = resourceSpawn.position.toLatLng(),
            radiusMeters = resourceSpawn.collectionRadiusMeters.toInt(),
            isDiscovered = false,
            isHiddenByFog = isHiddenByFog,
            resourceType = resourceType,
        )
    }

    fun presentWatchtower(watchtower: Watchtower): MapObjectUiModel =
        MapObjectUiModel(
            id = mapObjectId(
                kind = MapObjectKind.Watchtower,
                rawId = watchtower.id,
            ),
            kind = MapObjectKind.Watchtower,
            title = watchtower.name,
            description = watchtower.description,
            position = watchtower.location.toLatLng(),
            radiusMeters = (watchtower.revealRadiusMeters ?: watchtower.interactionRadiusMeters).roundToInt(),
            isDiscovered = true,
            watchtowerMarkerState = watchtower.toMarkerState(),
            watchtowerLevel = watchtower.level ?: 1,
        )

    internal fun parseObjectId(id: String): ParsedMapObjectId? {
        val parts = id.split(MAP_OBJECT_ID_SEPARATOR, limit = 2)
        if (parts.size != 2) {
            return null
        }

        val kind = MapObjectKind.entries.firstOrNull { it.idPrefix == parts[0] } ?: return null
        return ParsedMapObjectId(
            kind = kind,
            rawId = parts[1],
        )
    }

    private fun mapObjectId(
        kind: MapObjectKind,
        rawId: String,
    ): String = "${kind.idPrefix}$MAP_OBJECT_ID_SEPARATOR$rawId"

    private fun Watchtower.toMarkerState(): WatchtowerMarkerState = when {
        phase == WatchtowerPhase.CLAIMED && canUpgrade -> WatchtowerMarkerState.UPGRADE_AVAILABLE
        phase == WatchtowerPhase.CLAIMED -> WatchtowerMarkerState.CLAIMED
        canClaim -> WatchtowerMarkerState.CLAIMABLE
        else -> WatchtowerMarkerState.DISCOVERED_DORMANT
    }

    private fun ResourceSpawn.isHiddenByFog(
        canonicalZoom: Int,
        visibilityTileMask: Set<MapTile>,
    ): Boolean = tileAt(
        point = position,
        zoom = canonicalZoom,
    ) !in visibilityTileMask

    private fun com.github.arhor.journey.domain.model.GeoPoint.toLatLng(): LatLng =
        LatLng(
            latitude = lat,
            longitude = lon,
        )

    private companion object {
        const val MAP_OBJECT_ID_SEPARATOR = ":"
    }
}

internal data class ParsedMapObjectId(
    val kind: MapObjectKind,
    val rawId: String,
)
