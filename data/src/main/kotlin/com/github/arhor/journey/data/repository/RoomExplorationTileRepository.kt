package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.ExplorationTile
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

    override fun observeExploredTiles(range: ExplorationTileRange): Flow<Set<ExplorationTile>> =
        dao.observeByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { data ->
            data.map { it.toDomain() }
                .toSet()
        }

    override suspend fun getExploredTiles(range: ExplorationTileRange): Set<ExplorationTile> =
        dao.getByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { it.toDomain() }
            .toSet()

    override suspend fun getPackedExploredTile(tile: ExplorationTile): Long? =
        dao.getPackedByCoordinates(
            zoom = tile.zoom,
            x = tile.x,
            y = tile.y,
        )

    override suspend fun getPackedExploredTiles(range: ExplorationTileRange): LongArray =
        dao.getPackedByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).toLongArray()

    override fun observePackedExploredTiles(range: ExplorationTileRange): Flow<LongArray> =
        dao.observePackedByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { it.toLongArray() }

    override suspend fun markExplored(tiles: Set<ExplorationTile>) {
        if (tiles.isEmpty()) {
            return
        }

        dao.insert(
            entities = tiles
                .map { it.toEntity() }
                .sorted(),
        )
    }

    override suspend fun clear() {
        dao.clear()
    }
}
