package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.PointOfInterest
import kotlinx.coroutines.flow.Flow

interface PointOfInterestRepository {

    fun observeAll(): Flow<List<PointOfInterest>>

    /**
     * Ensures a small seed set of POIs exists.
     *
     * This is expected to be idempotent.
     */
    suspend fun ensureSeeded()
}

