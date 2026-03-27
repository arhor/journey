package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ExplorationTileDao
import com.github.arhor.journey.data.local.db.entity.ExploredTileEntity
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.MapTile
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

    override fun observeExploredTiles(range: ExplorationTileRange): Flow<Set<MapTile>> =
        dao.observeByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { it.mapTo(HashSet(), ExploredTileEntity::toDomain) }

    override suspend fun getExploredTiles(range: ExplorationTileRange): Set<MapTile> =
        dao.getByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).mapTo(HashSet(), ExploredTileEntity::toDomain)


    override fun observePackedExploredTiles(range: ExplorationTileRange): Flow<LongArray> =
        dao.observePackedByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).map { it.toLongArray() }

    override suspend fun getPackedExploredTiles(range: ExplorationTileRange): LongArray =
        dao.getPackedByRange(
            zoom = range.zoom,
            minX = range.minX,
            maxX = range.maxX,
            minY = range.minY,
            maxY = range.maxY,
        ).toLongArray()

    override suspend fun markExplored(tiles: Collection<MapTile>) {
        if (tiles.isEmpty()) {
            return
        }
        dao.insert(
            entities = tiles
                .sorted()
                .map { it.toEntity() }
        )
    }

    override suspend fun clear() {
        dao.clear()
    }
}
