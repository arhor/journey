package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.coroutines.flow.Flow

interface ExplorationTileRepository {

    fun observeExploredTiles(range: ExplorationTileRange): Flow<Set<MapTile>>

    suspend fun getExploredTiles(range: ExplorationTileRange): Set<MapTile>

    fun observePackedExploredTiles(range: ExplorationTileRange): Flow<LongArray>

    suspend fun getPackedExploredTiles(range: ExplorationTileRange): LongArray

    suspend fun markExplored(tiles: Collection<MapTile>): Set<MapTile>

    suspend fun clear()
}
