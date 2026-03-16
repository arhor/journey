package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.ExplorationTile
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.coroutines.flow.Flow

interface ExplorationTileRepository {

    fun observeExploredTiles(range: ExplorationTileRange): Flow<Set<ExplorationTile>>

    /**
     * Marks the provided tiles as explored.
     *
     * This operation is expected to be idempotent.
     */
    suspend fun markExplored(tiles: Set<ExplorationTile>)

    suspend fun clear()
}
