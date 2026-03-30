package com.github.arhor.journey.feature.map.fow

import android.util.Log
import androidx.collection.LruCache
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderBuildResult
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderCacheEntry
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderKey
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import javax.inject.Inject

private const val TAG = "FowRenderDataFactory"


/**
 * Prepares and memoizes render-ready fog overlay geometry.
 *
 * The factory keeps the expensive tile expansion, boundary extraction, and
 * polygon conversion out of the composable render path.
 */
class FowRenderDataFactory @Inject constructor() {
    private val renderCache = LruCacheWithFactory(factory = ::createRenderCacheEntry)
    private val fullRangeCache = LruCacheWithFactory(factory = ::createFowRenderDataFromRange)
    private val fullRangesCache = LruCacheWithFactory(factory = ::createFowRenderDataFromRanges)

    fun create(fogRanges: List<ExplorationTileRange>): FogOfWarRenderData? =
        createDetailed(fogRanges = fogRanges)
            ?.renderData

    internal fun createDetailed(fogRanges: List<ExplorationTileRange>): FogOfWarRenderBuildResult? {
        if (fogRanges.isEmpty()) {
            return null
        }
        val key = FogOfWarRenderKey.of(fogRanges)
        return renderCache[key]?.toBuildResult()
    }

    internal fun createDetailedUncached(
        fogRanges: List<ExplorationTileRange>,
        smoothedEntryFactory: (FogOfWarRenderKey) -> FogOfWarRenderCacheEntry? = this::createSmoothedRenderCacheEntry,
    ): FogOfWarRenderBuildResult? {
        if (fogRanges.isEmpty()) {
            return null
        }
        val key = FogOfWarRenderKey.of(fogRanges)

        return createRenderCacheEntry(
            key = key,
            smoothedEntryFactory = smoothedEntryFactory,
        )?.toBuildResult()
    }

    fun createFullRange(fogRange: ExplorationTileRange): FogOfWarRenderData {
        return fullRangeCache[fogRange]!!
    }

    fun createFullRanges(fogRanges: List<ExplorationTileRange>): FogOfWarRenderData? {
        if (fogRanges.isEmpty()) {
            return null
        }
        if (fogRanges.size == 1) {
            return createFullRange(fogRanges.single())
        }
        val key = FogOfWarRenderKey.of(fogRanges)
        return fullRangesCache[key]
    }

    /* ------------------------------------------ Internal implementation ------------------------------------------- */

    private fun createRenderCacheEntry(
        key: FogOfWarRenderKey,
        smoothedEntryFactory: (FogOfWarRenderKey) -> FogOfWarRenderCacheEntry? = this::createSmoothedRenderCacheEntry,
    ): FogOfWarRenderCacheEntry? = try {
        smoothedEntryFactory(key)
    } catch (e: RuntimeException) {
        // Keep rendering resilient if geometry extraction or validation rejects an unsupported topology.
        logRenderFailure(key = key, error = e)
        createFallbackRenderCacheEntry(key)
    }

    private fun createSmoothedRenderCacheEntry(key: FogOfWarRenderKey): FogOfWarRenderCacheEntry? {
        val geometryResult = key.ranges.toTileRegionGeometriesBuildResult()
        if (geometryResult.geometries.isEmpty()) {
            return null
        }

        val featureCollection = geometryResult.geometries.toPolygonFeatureCollection()
        val cacheEntry = FogOfWarRenderCacheEntry(
            renderData = FogOfWarRenderData(geoJsonData = GeoJsonData.Features(featureCollection)),
            renderMode = FogOfWarRenderMode.SMOOTHED,
            expandedFogCellCount = geometryResult.metrics.expandedCellCount,
            connectedRegionCount = geometryResult.metrics.connectedRegionCount,
            boundaryEdgeCount = geometryResult.metrics.boundaryEdgeCount,
            loopCount = geometryResult.metrics.loopCount,
            featureCount = featureCollection.features.size,
            ringPointCount = geometryResult.metrics.ringPointCount,
            resolvedAmbiguousVertexCount = geometryResult.metrics.resolvedAmbiguousVertexCount,
        )

        return cacheEntry
    }

    private fun createFallbackRenderCacheEntry(key: FogOfWarRenderKey): FogOfWarRenderCacheEntry {
        val renderData = createFowRenderDataFromRanges(key)
        val expandedCellCount = key.ranges.sumOf(ExplorationTileRange::tileCount)

        return FogOfWarRenderCacheEntry(
            renderData = renderData,
            renderMode = FogOfWarRenderMode.FALLBACK,
            expandedFogCellCount = expandedCellCount,
            connectedRegionCount = key.ranges.size,
            boundaryEdgeCount = 0,
            loopCount = 0,
            featureCount = key.ranges.size,
            ringPointCount = 0,
            resolvedAmbiguousVertexCount = 0,
        )
    }

    private fun createFowRenderDataFromRanges(key: FogOfWarRenderKey): FogOfWarRenderData {
        val renderData = FogOfWarRenderData(
            geoJsonData = GeoJsonData.Features(
                FeatureCollection(
                    features = key.ranges.mapIndexed { index, fogRange ->
                        val bounds = bounds(fogRange)
                        Feature(
                            geometry = Polygon(
                                coordinates = listOf(
                                    listOf(
                                        Position(longitude = bounds.west, latitude = bounds.north),
                                        Position(longitude = bounds.east, latitude = bounds.north),
                                        Position(longitude = bounds.east, latitude = bounds.south),
                                        Position(longitude = bounds.west, latitude = bounds.south),
                                        Position(longitude = bounds.west, latitude = bounds.north),
                                    ),
                                ),
                            ),
                            properties = buildJsonObject { },
                            id = JsonPrimitive("${fogRange.zoom}:full#$index"),
                        )
                    },
                ),
            ),
        )
        return renderData
    }

    private fun createFowRenderDataFromRange(key: ExplorationTileRange): FogOfWarRenderData {
        val bounds = bounds(key)
        val renderData = FogOfWarRenderData(
            geoJsonData = GeoJsonData.Features(
                FeatureCollection(
                    features = listOf(
                        Feature(
                            geometry = Polygon(
                                coordinates = listOf(
                                    listOf(
                                        Position(longitude = bounds.west, latitude = bounds.north),
                                        Position(longitude = bounds.east, latitude = bounds.north),
                                        Position(longitude = bounds.east, latitude = bounds.south),
                                        Position(longitude = bounds.west, latitude = bounds.south),
                                        Position(longitude = bounds.west, latitude = bounds.north),
                                    ),
                                ),
                            ),
                            properties = buildJsonObject { },
                            id = JsonPrimitive("${key.zoom}:full"),
                        ),
                    ),
                ),
            ),
        )
        return renderData
    }

    private class LruCacheWithFactory<K : Any, V : Any>(
        capacity: Int = DEFAULT_CACHE_CAPACITY,
        private val factory: (K) -> V?,
    ) : LruCache<K, V>(capacity) {
        override fun create(key: K): V? = factory(key)

        companion object {
            const val DEFAULT_CACHE_CAPACITY = 16
        }
    }
}

private fun FogOfWarRenderCacheEntry.toBuildResult(): FogOfWarRenderBuildResult = FogOfWarRenderBuildResult(
    renderData = renderData,
    renderMode = renderMode,
    expandedFogCellCount = expandedFogCellCount,
    connectedRegionCount = connectedRegionCount,
    boundaryEdgeCount = boundaryEdgeCount,
    loopCount = loopCount,
    featureCount = featureCount,
    ringPointCount = ringPointCount,
    resolvedAmbiguousVertexCount = resolvedAmbiguousVertexCount,
)

private fun logRenderFailure(
    key: FogOfWarRenderKey,
    error: RuntimeException,
) {
    runCatching {
        Log.e(TAG, "Failed to render fog of war for $key.", error)
    }
}
