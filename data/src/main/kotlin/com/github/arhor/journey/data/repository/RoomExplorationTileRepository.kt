package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRange
import com.github.arhor.journey.domain.repository.ExplorationTileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExplorationTileRepository @Inject constructor(
    private val dao: ExplorationTileDao,
) : ExplorationTileRepository {

    override fun observeExplorationTileLights(range: ExplorationTileRange): Flow<List<ExplorationTileLight>> =
        dao.observeByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { data ->
            data.map { it.toDomain() }
        }

    override suspend fun accumulateExplorationTileLights(tileLights: Collection<ExplorationTileLight>) {
        if (tileLights.isEmpty()) {
            return
        }

        dao.accumulate(
            entities = tileLights
                .groupBy(ExplorationTileLight::tile)
                .map { (tile, groupedLights) ->
                    ExplorationTileLight(
                        tile = tile,
                        light = groupedLights.maxOf(ExplorationTileLight::light),
                    )
                }
                .map(ExplorationTileLight::toEntity)
                .sorted(),
        )
    }

    override suspend fun clearExplorationTileLights() {
        dao.clear()
    }
}
