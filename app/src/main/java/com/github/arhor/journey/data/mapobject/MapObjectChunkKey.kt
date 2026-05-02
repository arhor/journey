package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.internal.WatchtowerGeneration

enum class MapObjectFamily {
    RESOURCE_SPAWN,
    WATCHTOWER,
}

data class MapObjectChunkKey(
    val family: MapObjectFamily,
    val generatorVersion: Int,
    val activeDayEpoch: Long?,
    val zoom: Int,
    val x: Int,
    val y: Int,
)

internal data class MapObjectAreaFetchKey(
    val activeDayEpoch: Long,
    val zoom: Int,
    val x: Int,
    val y: Int,
)

internal fun resourceSpawnChunkKey(
    activeDayEpoch: Long,
    zoom: Int,
    x: Int,
    y: Int,
): MapObjectChunkKey =
    MapObjectChunkKey(
        family = MapObjectFamily.RESOURCE_SPAWN,
        generatorVersion = RESOURCE_SPAWN_GENERATOR_VERSION,
        activeDayEpoch = activeDayEpoch,
        zoom = zoom,
        x = x,
        y = y,
    )

internal fun watchtowerChunkKey(
    zoom: Int,
    x: Int,
    y: Int,
): MapObjectChunkKey =
    MapObjectChunkKey(
        family = MapObjectFamily.WATCHTOWER,
        generatorVersion = WatchtowerGeneration.GENERATOR_VERSION,
        activeDayEpoch = null,
        zoom = zoom,
        x = x,
        y = y,
    )

internal fun MapObjectChunkKey.toAreaFetchKey(
    activeDayEpoch: Long,
): MapObjectAreaFetchKey =
    MapObjectAreaFetchKey(
        activeDayEpoch = activeDayEpoch,
        zoom = zoom,
        x = x,
        y = y,
    )
