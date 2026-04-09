package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.mapobject.MapObjectAreaStore
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.repository.ResourceSpawnRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicResourceSpawnRepository @Inject constructor(
    private val areaStore: MapObjectAreaStore,
) : ResourceSpawnRepository {

    override fun observeActiveSpawns(query: ResourceSpawnQuery): Flow<List<ResourceSpawn>> =
        areaStore.observeActiveResourceSpawns(query)

    override suspend fun getActiveSpawns(query: ResourceSpawnQuery): List<ResourceSpawn> =
        areaStore.getActiveResourceSpawns(query)

    override suspend fun getActiveSpawn(
        spawnId: String,
        at: Instant,
    ): ResourceSpawn? =
        areaStore.getActiveResourceSpawn(
            spawnId = spawnId,
            asOf = at,
        )
}
