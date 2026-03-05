package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.HealthDataSyncFailure
import com.github.arhor.journey.domain.model.HealthDataSyncMode
import com.github.arhor.journey.domain.model.HealthDataSyncPayload
import com.github.arhor.journey.domain.model.HealthDataSyncRequest
import com.github.arhor.journey.domain.model.HealthDataSyncResult
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.repository.HealthDataSyncRepository
import com.github.arhor.journey.domain.repository.HealthPermissionRepository

class SyncHealthDataUseCase(
    private val healthDataSyncRepository: HealthDataSyncRepository,
    private val healthPermissionRepository: HealthPermissionRepository? = null,
) {

    suspend operator fun invoke(
        timeRange: HealthDataTimeRange,
        selectedDataTypes: Set<HealthDataType>,
        syncMode: HealthDataSyncMode,
    ): SyncHealthDataUseCaseResult {
        if (selectedDataTypes.isEmpty()) {
            return SyncHealthDataUseCaseResult.Failure(HealthDataSyncFailure.EmptyData)
        }

        val hasReadPermissions = healthPermissionRepository
            ?.hasReadPermissions(selectedDataTypes = selectedDataTypes)
            ?: true

        if (!hasReadPermissions) {
            return SyncHealthDataUseCaseResult.Failure(HealthDataSyncFailure.PermissionMissing)
        }

        val result = healthDataSyncRepository.syncHealthData(
            request = HealthDataSyncRequest(
                timeRange = timeRange,
                selectedDataTypes = selectedDataTypes,
                mode = syncMode,
            ),
        )

        return when (result) {
            is HealthDataSyncResult.Success -> SyncHealthDataUseCaseResult.Success(result.payload)
            is HealthDataSyncResult.Failure -> SyncHealthDataUseCaseResult.Failure(result.reason)
        }
    }
}

sealed interface SyncHealthDataUseCaseResult {

    data class Success(val payload: HealthDataSyncPayload) : SyncHealthDataUseCaseResult

    data class Failure(val reason: HealthDataSyncFailure) : SyncHealthDataUseCaseResult
}
