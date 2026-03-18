package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.ExplorationTileLight
import com.github.arhor.journey.domain.model.ExplorationTileRange
import kotlinx.coroutines.flow.Flow

interface ExplorationTileRepository {

    fun observeExplorationTileLights(range: ExplorationTileRange): Flow<List<ExplorationTileLight>>

    /**
     * Accumulates the provided tile light using max(oldLight, contribution).
     *
     * This operation is expected to be idempotent.
     */
    suspend fun accumulateExplorationTileLights(tileLights: Collection<ExplorationTileLight>)

    suspend fun clearExplorationTileLights()
}
