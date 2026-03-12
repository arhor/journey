package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.ActivityLogEntry
import com.github.arhor.journey.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveActivityLogUseCase @Inject constructor(
    private val activityLogRepository: ActivityLogRepository,
) {
    operator fun invoke(): Flow<List<ActivityLogEntry>> = activityLogRepository.observeHistory()
}
