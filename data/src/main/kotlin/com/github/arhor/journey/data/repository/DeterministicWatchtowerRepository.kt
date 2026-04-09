package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.WatchtowerStateDao
import com.github.arhor.journey.data.local.db.entity.WatchtowerStateEntity
import com.github.arhor.journey.data.mapobject.MapObjectAreaStore
import com.github.arhor.journey.data.mapobject.WatchtowerDefinitionTileSource
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerDefinition
import com.github.arhor.journey.domain.model.WatchtowerRecord
import com.github.arhor.journey.domain.repository.WatchtowerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicWatchtowerRepository @Inject constructor(
    private val dao: WatchtowerStateDao,
    private val areaStore: MapObjectAreaStore,
    private val tileSource: WatchtowerDefinitionTileSource,
    private val clock: Clock,
) : WatchtowerRepository {
    private companion object {
        const val SQLITE_BIND_CHUNK_SIZE = 900
    }

    override fun observeInBounds(bounds: GeoBounds): Flow<List<WatchtowerRecord>> =
        areaStore.observeWatchtowerDefinitions(
            bounds = bounds,
            asOf = clock.instant(),
        )
            .flatMapLatest { definitions ->
                if (definitions.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    observeStates(definitions.map(WatchtowerDefinition::id))
                        .map { states ->
                            composeRecords(
                                definitions = definitions,
                                states = states,
                            )
                        }
                }
            }

    override suspend fun getInBounds(bounds: GeoBounds): List<WatchtowerRecord> {
        val definitions = areaStore.getWatchtowerDefinitions(
            bounds = bounds,
            asOf = clock.instant(),
        )
        if (definitions.isEmpty()) {
            return emptyList()
        }

        return composeRecords(
            definitions = definitions,
            states = loadStates(definitions.map(WatchtowerDefinition::id)),
        )
    }

    override suspend fun getIntersectingTiles(tiles: Set<MapTile>): List<WatchtowerRecord> {
        if (tiles.isEmpty()) {
            return emptyList()
        }

        val definitions = tileSource.fetchWatchtowerDefinitionsIntersectingTiles(tiles)
        if (definitions.isEmpty()) {
            return emptyList()
        }

        return composeRecords(
            definitions = definitions,
            states = loadStates(definitions.map(WatchtowerDefinition::id)),
        )
    }

    override suspend fun getById(id: String): WatchtowerRecord? {
        val definition = areaStore.getWatchtowerDefinition(id) ?: return null

        return WatchtowerRecord(
            definition = definition,
            state = dao.getById(id)?.toDomain(),
        )
    }

    override suspend fun markDiscovered(
        id: String,
        discoveredAt: Instant,
    ): Boolean {
        if (areaStore.getWatchtowerDefinition(id) == null) {
            return false
        }

        return dao.insertState(
            WatchtowerStateEntity(
                watchtowerId = id,
                discoveredAt = discoveredAt,
                claimedAt = null,
                level = 0,
                updatedAt = discoveredAt,
            ),
        ) != -1L
    }

    override suspend fun markClaimed(
        id: String,
        claimedAt: Instant,
        level: Int,
        updatedAt: Instant,
    ): Boolean = dao.markClaimed(
        watchtowerId = id,
        claimedAt = claimedAt,
        level = level,
        updatedAt = updatedAt,
    ) > 0

    override suspend fun setLevel(
        id: String,
        level: Int,
        updatedAt: Instant,
    ): Boolean = dao.setLevel(
        watchtowerId = id,
        level = level,
        updatedAt = updatedAt,
    ) > 0

    private fun composeRecords(
        definitions: List<WatchtowerDefinition>,
        states: List<WatchtowerStateEntity>,
    ): List<WatchtowerRecord> {
        val statesById = states.associate { state ->
            state.watchtowerId to state.toDomain()
        }

        return definitions.map { definition ->
            WatchtowerRecord(
                definition = definition,
                state = statesById[definition.id],
            )
        }
    }

    private fun observeStates(ids: List<String>): Flow<List<WatchtowerStateEntity>> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return flowOf(emptyList())
        }

        val chunks = distinctIds.chunked(SQLITE_BIND_CHUNK_SIZE)
        if (chunks.size == 1) {
            return dao.observeByIds(chunks.single())
        }

        return combine(chunks.map(dao::observeByIds)) { chunkResults ->
            chunkResults.flatMap { it.asIterable() }
        }
    }

    private suspend fun loadStates(ids: List<String>): List<WatchtowerStateEntity> =
        ids.distinct()
            .chunked(SQLITE_BIND_CHUNK_SIZE)
            .flatMap { chunk ->
                dao.getByIds(chunk)
            }
}
