package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.coroutines.flow.Flow

interface ExplorationTileRepository {

    fun observeExploredTiles(range: ExplorationTileRange): Flow<Set<ExplorationTile>>

    suspend fun getExploredTiles(range: ExplorationTileRange): Set<ExplorationTile>

    suspend fun getPackedExploredTile(tile: ExplorationTile): Long?

    suspend fun getPackedExploredTiles(range: ExplorationTileRange): LongArray

    fun observePackedExploredTiles(range: ExplorationTileRange): Flow<LongArray>

    /**
     * Marks the provided tiles as explored.
     *
     * This operation is expected to be idempotent.
     */
    suspend fun markExplored(tiles: Set<ExplorationTile>)

    suspend fun clear()
}
