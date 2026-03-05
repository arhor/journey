package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.ExplorationProgress
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ExplorationRepository {

    fun observeProgress(): Flow<ExplorationProgress>

    /**
     * Marks the POI as discovered.
     *
     * This operation is expected to be idempotent.
     */
    suspend fun discoverPoi(
        poiId: String,
        discoveredAt: Instant,
    )
}

