package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.di.DefaultDispatcher
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalGeneratedMapObjectAreaSource @Inject constructor(
    private val resourceSpawnGenerator: DeterministicResourceSpawnGenerator,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
) : MapObjectAreaSource {

    override suspend fun fetchArea(
        bounds: GeoBounds,
        asOf: Instant,
    ): MapObjectAreaResponse = withContext(defaultDispatcher) {
        MapObjectAreaResponse(
            resourceSpawns = resourceSpawnGenerator.activeSpawns(
                ResourceSpawnQuery(
                    at = asOf,
                    bounds = bounds,
                ),
            ),
        )
    }

    override suspend fun fetchActiveResourceSpawn(
        spawnId: String,
        asOf: Instant,
    ): ResourceSpawn? = withContext(defaultDispatcher) {
        resourceSpawnGenerator.activeSpawnById(
            spawnId = spawnId,
            at = asOf,
        )
    }
}
