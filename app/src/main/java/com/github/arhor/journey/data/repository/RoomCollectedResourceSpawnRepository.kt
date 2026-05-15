package com.github.arhor.journey.data.repository

import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCollectedResourceSpawnRepository @Inject constructor(
) : CollectedResourceSpawnRepository {
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<SpawnKey, CollectedResourceSpawn>>(emptyMap())

    override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>> =
        state.map { values ->
            values.values
                .filter { spawn -> spawn.heroId == heroId }
                .sortedBy(CollectedResourceSpawn::spawnId)
        }

    override suspend fun isCollected(
        heroId: String,
        spawnId: String,
    ): Boolean =
        state.value.containsKey(SpawnKey(heroId = heroId, spawnId = spawnId))

    override suspend fun markCollected(
        heroId: String,
        spawnId: String,
        resourceTypeId: String,
        collectedAt: Instant,
    ): Boolean {
        val key = SpawnKey(heroId = heroId, spawnId = spawnId)
        return mutex.withLock {
            if (state.value.containsKey(key)) {
                return@withLock false
            }
            val updated = state.value.toMutableMap()
            updated[key] = CollectedResourceSpawn(
                heroId = heroId,
                spawnId = spawnId,
                typeId = resourceTypeId,
                collectedAt = collectedAt,
            )
            state.value = updated.toMap()
            true
        }
    }

    private data class SpawnKey(
        val heroId: String,
        val spawnId: String,
    )
}
