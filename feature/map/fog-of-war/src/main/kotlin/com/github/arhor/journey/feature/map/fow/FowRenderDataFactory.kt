package com.github.arhor.journey.feature.map.fow

import androidx.collection.LruCache
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderBuildResult
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderCacheEntry
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderData
import com.github.arhor.journey.feature.map.fow.model.FogOfWarRenderKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.time.TimeSource

/**
 * Prepares and memoizes render-ready fog overlay geometry.
 *
 * The factory keeps the expensive tile expansion, boundary extraction, and
 * polygon conversion out of the composable render path.
 */
class FowRenderDataFactory @Inject constructor() {
    private val renderCache = object : LruCache<FogOfWarRenderKey, FogOfWarRenderCacheEntry>(CACHE_CAPACITY) {
        override fun create(key: FogOfWarRenderKey): FogOfWarRenderCacheEntry? {
            return super.create(key)
        }
    }
    private val fullRangeCache = LruCache<ExplorationTileRange, FogOfWarRenderData>(CACHE_CAPACITY)

    fun create(
        fogRanges: List<ExplorationTileRange>,
        checkCancelled: () -> Unit = {},
    ): FogOfWarRenderData? = createDetailed(
        fogRanges = fogRanges,
        checkCancelled = checkCancelled,
    )?.renderData

    internal fun createDetailed(
        fogRanges: List<ExplorationTileRange>,
        checkCancelled: () -> Unit = {},
    ): FogOfWarRenderBuildResult? {
        if (fogRanges.isEmpty()) {
            return null
        }
        checkCancelled()
        val key = FogOfWarRenderKey.of(fogRanges)

        synchronized(renderCache) {
            renderCache[key]?.let { entry ->
                return FogOfWarRenderBuildResult(
                    renderData = entry.renderData,
                )
            }
        }

        val geometryResult = key.ranges.toTileRegionGeometriesBuildResult(checkCancelled = checkCancelled)
        if (geometryResult.geometries.isEmpty()) {
            return null
        }

        val featureCollection = geometryResult.geometries.toPolygonFeatureCollection(checkCancelled = checkCancelled)
        val cacheEntry = FogOfWarRenderCacheEntry(
            renderData = FogOfWarRenderData(geoJsonData = GeoJsonData.Features(featureCollection)),
            expandedFogCellCount = geometryResult.metrics.expandedCellCount,
            connectedRegionCount = geometryResult.metrics.connectedRegionCount,
            boundaryEdgeCount = geometryResult.metrics.boundaryEdgeCount,
            loopCount = geometryResult.metrics.loopCount,
            featureCount = featureCollection.features.size,
            ringPointCount = geometryResult.metrics.ringPointCount,
        )

        synchronized(renderCache) {
            renderCache[key]?.let { entry ->
                return FogOfWarRenderBuildResult(
                    renderData = entry.renderData,
                )
            }
            renderCache.put(key, cacheEntry)

            return FogOfWarRenderBuildResult(
                renderData = cacheEntry.renderData,
            )
        }
    }

    fun createFullRange(
        fogRange: ExplorationTileRange,
    ): FogOfWarRenderData {
        synchronized(fullRangeCache) {
            fullRangeCache[fogRange]?.let {
                return it
            }
        }

        val bounds = ExplorationTileGrid.bounds(fogRange)
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
                            id = JsonPrimitive("${fogRange.zoom}:full"),
                        ),
                    ),
                ),
            ),
        )

        synchronized(fullRangeCache) {
            fullRangeCache[fogRange]?.let {
                return it
            }
            fullRangeCache.put(fogRange, renderData)

            return renderData
        }
    }

    private companion object {
        private const val CACHE_CAPACITY = 16
    }
}
