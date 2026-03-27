package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ResourceSpawnRepository {

    fun observeActiveSpawns(query: ResourceSpawnQuery): Flow<List<ResourceSpawn>>

    suspend fun getActiveSpawns(query: ResourceSpawnQuery): List<ResourceSpawn>

    suspend fun getActiveSpawn(
        spawnId: String,
        at: Instant,
    ): ResourceSpawn?
}
