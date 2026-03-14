package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.DiscoveredPoiDao
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.DiscoveredPoi
import com.github.arhor.journey.domain.model.ExplorationProgress
import com.github.arhor.journey.domain.repository.ExplorationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomExplorationRepository @Inject constructor(
    private val dao: DiscoveredPoiDao,
) : ExplorationRepository {

    override fun observeProgress(): Flow<ExplorationProgress> =
        dao.observeAll()
            .map { items ->
                ExplorationProgress(
                    discovered = items.map { it.toDomain() }.toSet(),
                )
            }

    override suspend fun discoverPoi(
        poiId: String,
        discoveredAt: Instant,
    ) {
        dao.insert(
            DiscoveredPoi(poiId = poiId, discoveredAt = discoveredAt).toEntity(),
        )
    }
}
