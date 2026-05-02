package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.di.DefaultDispatcher
import com.github.arhor.journey.domain.internal.bounds
import com.github.arhor.journey.domain.internal.tileRange
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.GeoPoint
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.model.WatchtowerDefinition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import kotlin.math.cos

private const val MAP_OBJECT_CHUNK_ZOOM = 15
private const val MAX_AREA_CHUNKS = 512L
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

@Singleton
class MapObjectAreaStore @Inject constructor(
    private val source: MapObjectAreaSource,
    private val cache: InMemoryMapObjectChunkCache,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) {
    private val fetchLocks = ConcurrentHashMap<MapObjectAreaFetchKey, Mutex>()

    fun observeActiveResourceSpawns(
        query: ResourceSpawnQuery,
    ): Flow<List<ResourceSpawn>> = flow {
        val chunkRequest = withContext(defaultDispatcher) { query.toResourceChunkRequest() }
        val cached = withContext(defaultDispatcher) { readCachedResourceSpawns(chunkRequest) }
        if (cached.shouldEmitCacheFirst) {
            emit(cached.items)
        }

        if (!cached.isComplete && !chunkRequest.isOverLimit) {
            val refreshed = withContext(defaultDispatcher) {
                loadChunks(chunkRequest.keys, query.at)
                readCachedResourceSpawns(chunkRequest).items
            }
            if (!cached.shouldEmitCacheFirst || refreshed != cached.items) {
                emit(refreshed)
            }
        } else if (!cached.shouldEmitCacheFirst) {
            emit(cached.items)
        }
    }.distinctUntilChanged()

    suspend fun getActiveResourceSpawns(
        query: ResourceSpawnQuery,
    ): List<ResourceSpawn> = withContext(defaultDispatcher) {
        val chunkRequest = query.toResourceChunkRequest()
        val cached = readCachedResourceSpawns(chunkRequest)
        if (cached.isComplete || chunkRequest.isOverLimit) {
            return@withContext cached.items
        }

        loadChunks(chunkRequest.keys, query.at)
        readCachedResourceSpawns(chunkRequest).items
    }

    suspend fun getActiveResourceSpawn(
        spawnId: String,
        asOf: Instant,
    ): ResourceSpawn? =
        source.fetchActiveResourceSpawn(
            spawnId = spawnId,
            asOf = asOf,
        )

    fun observeWatchtowerDefinitions(
        bounds: GeoBounds,
        asOf: Instant,
    ): Flow<List<WatchtowerDefinition>> = flow {
        val chunkRequest = withContext(defaultDispatcher) { bounds.toWatchtowerChunkRequest() }
        val cached = withContext(defaultDispatcher) { readCachedWatchtowerDefinitions(chunkRequest) }
        if (cached.shouldEmitCacheFirst) {
            emit(cached.items)
        }

        if (!cached.isComplete && !chunkRequest.isOverLimit) {
            val refreshed = withContext(defaultDispatcher) {
                loadChunks(chunkRequest.keys, asOf)
                readCachedWatchtowerDefinitions(chunkRequest).items
            }
            if (!cached.shouldEmitCacheFirst || refreshed != cached.items) {
                emit(refreshed)
            }
        } else if (!cached.shouldEmitCacheFirst) {
            emit(cached.items)
        }
    }.distinctUntilChanged()

    suspend fun getWatchtowerDefinitions(
        bounds: GeoBounds,
        asOf: Instant,
    ): List<WatchtowerDefinition> = withContext(defaultDispatcher) {
        val chunkRequest = bounds.toWatchtowerChunkRequest()
        val cached = readCachedWatchtowerDefinitions(chunkRequest)
        if (cached.isComplete || chunkRequest.isOverLimit) {
            return@withContext cached.items
        }

        loadChunks(chunkRequest.keys, asOf)
        readCachedWatchtowerDefinitions(chunkRequest).items
    }

    suspend fun getWatchtowerDefinition(id: String): WatchtowerDefinition? =
        source.fetchWatchtowerDefinition(id)

    private suspend fun readCachedResourceSpawns(
        request: ResourceChunkRequest,
    ): AreaCacheRead<ResourceSpawn> {
        val cached = cache.readResourceChunks(request.keys)
        return AreaCacheRead(
            items = cached.items
                .distinctBy(ResourceSpawn::id)
                .filter { spawn -> request.matches(spawn) }
                .sortedBy(ResourceSpawn::id),
            isComplete = cached.isCompleteFor(request.keys),
            shouldEmitCacheFirst = cached.hasAnyCachedChunk,
        )
    }

    private suspend fun readCachedWatchtowerDefinitions(
        request: WatchtowerChunkRequest,
    ): AreaCacheRead<WatchtowerDefinition> {
        val cached = cache.readWatchtowerChunks(request.keys)
        return AreaCacheRead(
            items = cached.items
                .distinctBy(WatchtowerDefinition::id)
                .filter { definition -> request.bounds.contains(definition.location) }
                .sortedBy(WatchtowerDefinition::id),
            isComplete = cached.isCompleteFor(request.keys),
            shouldEmitCacheFirst = cached.hasAnyCachedChunk,
        )
    }

    private suspend fun loadChunks(
        keys: List<MapObjectChunkKey>,
        asOf: Instant,
    ) {
        val activeDayEpoch = asOf.toUtcDayEpoch()
        keys.forEach { key ->
            val fetchKey = key.toAreaFetchKey(activeDayEpoch)
            val fetchLock = fetchLocks.getOrPut(fetchKey) { Mutex() }
            fetchLock.withLock {
                loadChunkIfStillMissing(
                    key = key,
                    asOf = asOf,
                    activeDayEpoch = activeDayEpoch,
                )
            }
        }
    }

    private suspend fun loadChunkIfStillMissing(
        key: MapObjectChunkKey,
        asOf: Instant,
        activeDayEpoch: Long,
    ) {
        val isCached = when (key.family) {
            MapObjectFamily.RESOURCE_SPAWN -> cache.readResourceChunks(listOf(key)).isCompleteFor(listOf(key))
            MapObjectFamily.WATCHTOWER -> cache.readWatchtowerChunks(listOf(key)).isCompleteFor(listOf(key))
        }
        if (isCached) {
            return
        }

        val tile = MapTile(
            zoom = key.zoom,
            x = key.x,
            y = key.y,
        )
        val chunkBounds = bounds(tile)
        val response = source.fetchArea(
            bounds = chunkBounds,
            asOf = asOf,
        )
        val resourceKey = resourceSpawnChunkKey(
            activeDayEpoch = activeDayEpoch,
            zoom = key.zoom,
            x = key.x,
            y = key.y,
        )
        val watchtowerKey = watchtowerChunkKey(
            zoom = key.zoom,
            x = key.x,
            y = key.y,
        )

        cache.putResourceChunk(
            key = resourceKey,
            spawns = response.resourceSpawns.filter { spawn -> chunkBounds.contains(spawn.position) },
        )
        cache.putWatchtowerChunk(
            key = watchtowerKey,
            definitions = response.watchtowerDefinitions.filter { definition ->
                chunkBounds.contains(definition.location)
            },
        )
    }

    private fun ResourceChunkRequest.matches(spawn: ResourceSpawn): Boolean {
        if (bounds != null && !bounds.contains(spawn.position)) {
            return false
        }
        if (center != null && radiusMeters != null) {
            return spawn.position.distanceTo(center) <= radiusMeters
        }
        return true
    }

    private fun ResourceSpawnQuery.toResourceChunkRequest(): ResourceChunkRequest {
        val searchBounds = toSearchBounds()
        val activeDayEpoch = at.toUtcDayEpoch()
        val keys = searchBounds
            ?.toChunkKeys { tile ->
                resourceSpawnChunkKey(
                    activeDayEpoch = activeDayEpoch,
                    zoom = tile.zoom,
                    x = tile.x,
                    y = tile.y,
                )
            }
            .orEmpty()

        return ResourceChunkRequest(
            keys = keys,
            bounds = bounds,
            center = center,
            radiusMeters = radiusMeters,
            isOverLimit = keys.size.toLong() > MAX_AREA_CHUNKS,
        )
    }

    private fun GeoBounds.toWatchtowerChunkRequest(): WatchtowerChunkRequest {
        val keys = toChunkKeys { tile ->
            watchtowerChunkKey(
                zoom = tile.zoom,
                x = tile.x,
                y = tile.y,
            )
        }

        return WatchtowerChunkRequest(
            keys = keys,
            bounds = this,
            isOverLimit = keys.size.toLong() > MAX_AREA_CHUNKS,
        )
    }

    private fun ResourceSpawnQuery.toSearchBounds(): GeoBounds? {
        val queryBounds = bounds
        val centerBounds = center?.let { location ->
            val radius = radiusMeters ?: 0.0
            location.toBounds(radius)
        }

        return when {
            queryBounds != null && centerBounds != null -> queryBounds.intersect(centerBounds)
            queryBounds != null -> queryBounds
            centerBounds != null -> centerBounds
            else -> null
        }
    }

    private fun GeoPoint.toBounds(radiusMeters: Double): GeoBounds {
        val latitudeOffsetDegrees = radiusMeters / METERS_PER_LATITUDE_DEGREE
        val longitudeMetersPerDegree = (
            METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(lat)).absoluteValue
        ).coerceAtLeast(1.0)
        val longitudeOffsetDegrees = radiusMeters / longitudeMetersPerDegree

        return GeoBounds(
            south = lat - latitudeOffsetDegrees,
            west = lon - longitudeOffsetDegrees,
            north = lat + latitudeOffsetDegrees,
            east = lon + longitudeOffsetDegrees,
        )
    }

    private fun GeoBounds.toChunkKeys(
        transform: (MapTile) -> MapObjectChunkKey,
    ): List<MapObjectChunkKey> {
        val range = tileRange(
            bounds = this,
            zoom = MAP_OBJECT_CHUNK_ZOOM,
        )
        if (range.tileCount > MAX_AREA_CHUNKS) {
            return range.asSequence().take(MAX_AREA_CHUNKS.toInt() + 1).map(transform).toList()
        }
        return range.asSequence().map(transform).toList()
    }

    private fun Instant.toUtcDayEpoch(): Long =
        atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()

    private data class ResourceChunkRequest(
        val keys: List<MapObjectChunkKey>,
        val bounds: GeoBounds?,
        val center: GeoPoint?,
        val radiusMeters: Double?,
        val isOverLimit: Boolean,
    )

    private data class WatchtowerChunkRequest(
        val keys: List<MapObjectChunkKey>,
        val bounds: GeoBounds,
        val isOverLimit: Boolean,
    )

    private data class AreaCacheRead<T>(
        val items: List<T>,
        val isComplete: Boolean,
        val shouldEmitCacheFirst: Boolean,
    )
}
