package com.github.arhor.journey.data.repository

import com.github.arhor.journey.data.local.db.dao.ActivityLogDao
import com.github.arhor.journey.data.mapper.toDomain
import com.github.arhor.journey.data.mapper.toEntity
import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.model.RecordedActivity
import com.github.arhor.journey.domain.model.Reward
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomActivityLogRepository @Inject constructor(
    private val dao: ActivityLogDao,
) : ActivityLogRepository {

    override fun observeHistory(): Flow<List<ActivityLogEntry>> =
        dao.observeRecent()
            .map { items -> items.map { it.toDomain() } }

    override suspend fun insert(
        recorded: RecordedActivity,
        reward: Reward,
    ): Long = dao.insert(recorded.toEntity(reward))
}

