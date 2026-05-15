package com.github.arhor.journey.data.mapobject

data class MapObjectChunkKey(
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
        generatorVersion = RESOURCE_SPAWN_GENERATOR_VERSION,
        activeDayEpoch = activeDayEpoch,
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
