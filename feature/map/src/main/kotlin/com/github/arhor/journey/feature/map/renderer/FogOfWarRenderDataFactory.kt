package com.github.arhor.journey.feature.map.renderer

import androidx.collection.LruCache
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.model.FogOfWarRenderData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import javax.inject.Inject

/**
 * Prepares and memoizes render-ready fog overlay geometry.
 *
 * The factory keeps the expensive tile expansion, boundary extraction, and
 * polygon conversion out of the composable render path.
 */
class FogOfWarRenderDataFactory @Inject constructor() {
    private val cache = LruCache<FogOfWarRenderKey, FogOfWarRenderData>(CACHE_CAPACITY)

    fun create(
        fogRanges: List<ExplorationTileRange>,
        checkCancelled: () -> Unit = {},
    ): FogOfWarRenderData? {
        if (fogRanges.isEmpty()) {
            return null
        }
        checkCancelled()
        val key = FogOfWarRenderKey.of(fogRanges)

        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val geoJsonData = fogRanges.toGeoJsonDataOrNull(checkCancelled) ?: return null
        val renderData = FogOfWarRenderData(geoJsonData)

        synchronized(cache) {
            cache[key]?.let { return it }
            cache.put(key, renderData)

            return renderData
        }
    }

    private companion object {
        private const val CACHE_CAPACITY = 16
    }
}

internal fun List<ExplorationTileRange>.toFeatureCollection(
    checkCancelled: () -> Unit = {},
): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = toTileRegionGeometries(checkCancelled = checkCancelled).mapIndexed { index, region ->
            checkCancelled()
            Feature(
                geometry = region.toPolygon(),
                properties = buildJsonObject { },
                id = JsonPrimitive("${region.zoom}#$index"),
            )
        },
    )

internal fun List<ExplorationTileRange>.toGeoJsonDataOrNull(
    checkCancelled: () -> Unit = {},
): GeoJsonData.Features? =
    takeIf { it.isNotEmpty() }
        ?.toFeatureCollection(checkCancelled = checkCancelled)
        ?.let(GeoJsonData::Features)

private data class FogOfWarRenderKey(
    val ranges: List<ExplorationTileRange>,
) {
    companion object {
        private val ExplorationTileRangeComparator = compareBy(
            ExplorationTileRange::zoom,
            ExplorationTileRange::minY,
            ExplorationTileRange::minX,
            ExplorationTileRange::maxY,
            ExplorationTileRange::maxX,
        )

        fun of(ranges: List<ExplorationTileRange>): FogOfWarRenderKey = FogOfWarRenderKey(
            ranges = ranges.sortedWith(ExplorationTileRangeComparator),
        )
    }
}
