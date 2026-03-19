package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.CollectedResourceSpawnDao
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.CollectedResourceSpawn
import com.github.arhor.journey.domain.repository.CollectedResourceSpawnRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomCollectedResourceSpawnRepository @Inject constructor(
    private val dao: CollectedResourceSpawnDao,
) : CollectedResourceSpawnRepository {

    override fun observeAll(heroId: String): Flow<List<CollectedResourceSpawn>> =
        dao.observeAll(heroId)
            .map { entities -> entities.map { it.toDomain() } }

    override suspend fun isCollected(
        heroId: String,
        spawnId: String,
    ): Boolean =
        dao.exists(
            heroId = heroId,
            spawnId = spawnId,
        )

    override suspend fun recordClaim(
        heroId: String,
        spawnId: String,
        resourceTypeId: String,
        collectedAt: Instant,
    ): Boolean =
        dao.insert(
            CollectedResourceSpawn(
                heroId = heroId,
                spawnId = spawnId,
                resourceTypeId = resourceTypeId,
                collectedAt = collectedAt,
            ).toEntity(),
        ) != -1L
}
