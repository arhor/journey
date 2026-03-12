package com.github.arhor.journey.domain.activity.usecase

import com.github.arhor.journey.domain.activity.model.ActivityLogEntry
import com.github.arhor.journey.domain.activity.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveActivityLogUseCase @Inject constructor(
    private val activityLogRepository: ActivityLogRepository,
) {
    operator fun invoke(): Flow<List<ActivityLogEntry>> = activityLogRepository.observeHistory()
}
