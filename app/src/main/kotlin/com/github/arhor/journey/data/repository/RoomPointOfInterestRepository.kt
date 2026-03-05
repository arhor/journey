package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.PoiDao
import com.github.arhor.journey.data.local.seed.PointOfInterestSeed
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.PointOfInterest
import com.github.arhor.journey.domain.repository.PointOfInterestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomPointOfInterestRepository @Inject constructor(
    private val dao: PoiDao,
) : PointOfInterestRepository {

    override fun observeAll(): Flow<List<PointOfInterest>> =
        dao.observeAll()
            .map { items -> items.map { it.toDomain() } }

    override suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(PointOfInterestSeed.items.map { it.toEntity() })
        }
    }
}

