package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.domain.model.ResourceSpawn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InMemoryMapObjectChunkCache @Inject constructor() {
    private val mutex = Mutex()
    private val resourceChunks = linkedMapOf<MapObjectChunkKey, List<ResourceSpawn>>()

    suspend fun readResourceChunks(
        keys: List<MapObjectChunkKey>,
    ): CachedMapObjectChunks<ResourceSpawn> = mutex.withLock {
        val cachedKeys = keys.filterTo(linkedSetOf()) { it in resourceChunks }
        CachedMapObjectChunks(
            items = keys.flatMap { key -> resourceChunks[key].orEmpty() },
            cachedKeys = cachedKeys,
        )
    }

    suspend fun putResourceChunk(
        key: MapObjectChunkKey,
        spawns: List<ResourceSpawn>,
    ) {
        mutex.withLock {
            resourceChunks[key] = spawns.sortedBy(ResourceSpawn::id)
        }
    }
}

data class CachedMapObjectChunks<T>(
    val items: List<T>,
    val cachedKeys: Set<MapObjectChunkKey>,
) {
    fun isCompleteFor(keys: List<MapObjectChunkKey>): Boolean =
        keys.all { it in cachedKeys }

    val hasAnyCachedChunk: Boolean
        get() = cachedKeys.isNotEmpty()
}
