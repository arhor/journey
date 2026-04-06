package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.GeoBounds
import com.github.arhor.journey.domain.model.MapTile
import com.github.arhor.journey.domain.model.WatchtowerRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WatchtowerRepository {

    fun observeInBounds(bounds: GeoBounds): Flow<List<WatchtowerRecord>>

    suspend fun getInBounds(bounds: GeoBounds): List<WatchtowerRecord>

    suspend fun getIntersectingTiles(tiles: Set<MapTile>): List<WatchtowerRecord>

    suspend fun getById(id: String): WatchtowerRecord?

    suspend fun markDiscovered(
        id: String,
        discoveredAt: Instant,
    ): Boolean

    suspend fun markClaimed(
        id: String,
        claimedAt: Instant,
        level: Int,
        updatedAt: Instant,
    ): Boolean

    suspend fun setLevel(
        id: String,
        level: Int,
        updatedAt: Instant,
    ): Boolean
}
