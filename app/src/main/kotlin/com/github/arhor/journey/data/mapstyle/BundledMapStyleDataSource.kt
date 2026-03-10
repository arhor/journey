package com.github.arhor.journey.data.mapstyle

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundledMapStyleDataSource @Inject constructor() {

    fun getStyles(): List<MapStyleRecord> = listOf(
        MapStyleRecord(
            id = "default",
            name = "Default",
            source = MapStyleRecord.Source.BUNDLED,
            assetPath = "map/styles/default.json",
            fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
        ),
        MapStyleRecord(
            id = "satellite",
            name = "Satellite",
            source = MapStyleRecord.Source.BUNDLED,
            assetPath = "map/styles/satellite.json",
            fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
        ),
        MapStyleRecord(
            id = "terrain",
            name = "Terrain",
            source = MapStyleRecord.Source.BUNDLED,
            assetPath = "map/styles/terrain.json",
            fallbackUri = DEFAULT_STYLE_FALLBACK_URI,
        ),
    )

    companion object {
        const val DEFAULT_STYLE_FALLBACK_URI = "https://tiles.openfreemap.org/styles/liberty"
    }
}
