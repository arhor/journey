package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.BreachNodeRecord
import com.github.arhor.journey.domain.model.GeoBounds
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BreachNodeRepository {

    fun observeInBounds(bounds: GeoBounds): Flow<List<BreachNodeRecord>>

    suspend fun getInBounds(bounds: GeoBounds): List<BreachNodeRecord>

    suspend fun getForCells(h3CellIds: Collection<String>): List<BreachNodeRecord>

    suspend fun getById(id: String): BreachNodeRecord?

    suspend fun getByH3CellId(h3CellId: String): BreachNodeRecord?

    suspend fun upsertDiscovered(
        id: String,
        h3CellId: String,
        discoveredAt: Instant,
        updatedAt: Instant,
    ): Boolean

    suspend fun markControlled(
        id: String,
        h3CellId: String,
        controlledAt: Instant,
        updatedAt: Instant,
    ): Boolean

    fun observeControlledCells(bounds: GeoBounds): Flow<Set<String>>
}
