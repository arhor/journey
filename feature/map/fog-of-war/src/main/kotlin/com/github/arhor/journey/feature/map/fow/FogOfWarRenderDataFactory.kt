package com.github.arhor.journey.feature.map.fow

import androidx.collection.LruCache
import com.github.arhor.journey.domain.model.ExplorationTileGrid
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.TimeSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import javax.inject.Inject

/**
 * Prepares and memoizes render-ready fog overlay geometry.
 *
 * The factory keeps the expensive tile expansion, boundary extraction, and
 * polygon conversion out of the composable render path.
 */
class FogOfWarRenderDataFactory @Inject constructor() {
    private val cache = LruCache<FogOfWarRenderKey, FogOfWarRenderCacheEntry>(CACHE_CAPACITY)
    private val fullRangeCache = LruCache<ExplorationTileRange, FogOfWarRenderData>(CACHE_CAPACITY)
    private val metricsLock = Any()
    private var renderCacheHits = 0L
    private var renderCacheMisses = 0L
    private var fullRangeCacheHits = 0L
    private var fullRangeCacheMisses = 0L

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

        synchronized(cache) {
            cache[key]?.let { entry ->
                recordRenderCacheHit()
                return FogOfWarRenderBuildResult(
                    renderData = entry.renderData,
                    metrics = entry.toBuildMetrics(cacheHit = true),
                )
            }
        }

        val geometryStartedAt = TimeSource.Monotonic.markNow()
        val geometryResult = fogRanges.toTileRegionGeometriesBuildResult(checkCancelled = checkCancelled)
        val geometryBuildMillis = geometryStartedAt.elapsedNow().inWholeMilliseconds
        if (geometryResult.geometries.isEmpty()) {
            return null
        }

        val featureCollectionStartedAt = TimeSource.Monotonic.markNow()
        val featureCollection = geometryResult.geometries.toPolygonFeatureCollection(checkCancelled = checkCancelled)
        val featureCollectionBuildMillis = featureCollectionStartedAt.elapsedNow().inWholeMilliseconds
        val cacheEntry = FogOfWarRenderCacheEntry(
            renderData = FogOfWarRenderData(geoJsonData = GeoJsonData.Features(featureCollection)),
            expandedFogCellCount = geometryResult.metrics.expandedCellCount,
            connectedRegionCount = geometryResult.metrics.connectedRegionCount,
            boundaryEdgeCount = geometryResult.metrics.boundaryEdgeCount,
            loopCount = geometryResult.metrics.loopCount,
            featureCount = featureCollection.features.size,
            ringPointCount = geometryResult.metrics.ringPointCount,
        )

        synchronized(cache) {
            cache[key]?.let { entry ->
                recordRenderCacheHit()
                return FogOfWarRenderBuildResult(
                    renderData = entry.renderData,
                    metrics = entry.toBuildMetrics(cacheHit = true),
                )
            }
            cache.put(key, cacheEntry)
            recordRenderCacheMiss()

            return FogOfWarRenderBuildResult(
                renderData = cacheEntry.renderData,
                metrics = cacheEntry.toBuildMetrics(
                    cacheHit = false,
                    geometryBuildMillis = geometryBuildMillis,
                    featureCollectionBuildMillis = featureCollectionBuildMillis,
                ),
            )
        }
    }

    fun createFullRange(
        fogRange: ExplorationTileRange,
    ): FogOfWarRenderData {
        synchronized(fullRangeCache) {
            fullRangeCache[fogRange]?.let {
                recordFullRangeCacheHit()
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
                recordFullRangeCacheHit()
                return it
            }
            fullRangeCache.put(fogRange, renderData)
            recordFullRangeCacheMiss()

            return renderData
        }
    }

    fun cacheMetricsSnapshot(): FogOfWarCacheMetrics = synchronized(metricsLock) {
        FogOfWarCacheMetrics(
            renderHits = renderCacheHits,
            renderMisses = renderCacheMisses,
            fullRangeHits = fullRangeCacheHits,
            fullRangeMisses = fullRangeCacheMisses,
        )
    }

    private fun recordRenderCacheHit() {
        synchronized(metricsLock) {
            renderCacheHits += 1
        }
    }

    private fun recordRenderCacheMiss() {
        synchronized(metricsLock) {
            renderCacheMisses += 1
        }
    }

    private fun recordFullRangeCacheHit() {
        synchronized(metricsLock) {
            fullRangeCacheHits += 1
        }
    }

    private fun recordFullRangeCacheMiss() {
        synchronized(metricsLock) {
            fullRangeCacheMisses += 1
        }
    }

    private companion object {
        private const val CACHE_CAPACITY = 16
    }
}

internal data class FogOfWarRenderBuildResult(
    val renderData: FogOfWarRenderData,
    val metrics: FogOfWarRenderBuildMetrics,
)

internal data class FogOfWarRenderBuildMetrics(
    val geometryBuildMillis: Long = 0,
    val featureCollectionBuildMillis: Long = 0,
    val expandedFogCellCount: Long = 0,
    val connectedRegionCount: Int = 0,
    val boundaryEdgeCount: Int = 0,
    val loopCount: Int = 0,
    val featureCount: Int = 0,
    val ringPointCount: Int = 0,
    val cacheHit: Boolean = false,
)

private data class FogOfWarRenderCacheEntry(
    val renderData: FogOfWarRenderData,
    val expandedFogCellCount: Long,
    val connectedRegionCount: Int,
    val boundaryEdgeCount: Int,
    val loopCount: Int,
    val featureCount: Int,
    val ringPointCount: Int,
) {
    fun toBuildMetrics(
        cacheHit: Boolean,
        geometryBuildMillis: Long = 0,
        featureCollectionBuildMillis: Long = 0,
    ): FogOfWarRenderBuildMetrics = FogOfWarRenderBuildMetrics(
        geometryBuildMillis = geometryBuildMillis,
        featureCollectionBuildMillis = featureCollectionBuildMillis,
        expandedFogCellCount = expandedFogCellCount,
        connectedRegionCount = connectedRegionCount,
        boundaryEdgeCount = boundaryEdgeCount,
        loopCount = loopCount,
        featureCount = featureCount,
        ringPointCount = ringPointCount,
        cacheHit = cacheHit,
    )
}

internal fun List<ExplorationTileRange>.toFeatureCollection(
    checkCancelled: () -> Unit = {},
): FeatureCollection<Polygon, JsonObject?> = toTileRegionGeometriesBuildResult(
    checkCancelled = checkCancelled,
).geometries.toPolygonFeatureCollection(checkCancelled = checkCancelled)

private fun List<TileRegionGeometry>.toPolygonFeatureCollection(
    checkCancelled: () -> Unit = {},
): FeatureCollection<Polygon, JsonObject?> =
    FeatureCollection(
        features = mapIndexed { index, region ->
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
