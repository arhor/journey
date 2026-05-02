package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface CollectedResourceSpawnRepository {

    fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>>

    suspend fun isCollected(
        heroId: String,
        spawnId: String,
    ): Boolean

    /**
     * Marks the spawn as collected if it does not exist yet.
     *
     * Returns `true` when the collected marker was newly inserted and `false` when it already existed.
     */
    suspend fun markCollected(
        heroId: String,
        spawnId: String,
        resourceTypeId: String,
        collectedAt: Instant,
    ): Boolean
}
