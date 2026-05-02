package com.github.arhor.journey.data.mapobject

import com.github.arhor.journey.di.DefaultDispatcher
import com.github.arhor.journey.domain.internal.WatchtowerGeneration
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ResourceSpawn
import com.github.arhor.journey.domain.model.ResourceSpawnQuery
import com.github.arhor.journey.domain.model.WatchtowerDefinition
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
) : MapObjectAreaSource,
    WatchtowerDefinitionTileSource {

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
            watchtowerDefinitions = WatchtowerGeneration.definitionsInBounds(bounds),
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

    override suspend fun fetchWatchtowerDefinition(id: String): WatchtowerDefinition? =
        withContext(defaultDispatcher) {
            WatchtowerGeneration.definitionForId(id)
        }

    override suspend fun fetchWatchtowerDefinitionsIntersectingTiles(
        tiles: Set<MapTile>,
    ): List<WatchtowerDefinition> = withContext(defaultDispatcher) {
        val definitionsById = linkedMapOf<String, WatchtowerDefinition>()
        WatchtowerGeneration.intersectingGeneratorRanges(tiles).forEach { range ->
            WatchtowerGeneration.definitionSequenceInRange(range).forEach { definition ->
                definitionsById.putIfAbsent(definition.id, definition)
            }
        }

        definitionsById.values.toList()
    }
}
