package com.github.arhor.journey.domain.repository

import com.github.arhor.journey.domain.model.HealthDataSyncPayload
import com.github.arhor.journey.domain.model.HealthDataSyncRequest
import com.github.arhor.journey.domain.model.HealthDataSyncResult
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType

interface HealthDataSyncRepository {

    suspend fun syncHealthData(request: HealthDataSyncRequest): HealthDataSyncResult

    suspend fun readHealthData(
        timeRange: HealthDataTimeRange,
        selectedDataTypes: Set<HealthDataType>,
    ): HealthDataSyncPayload
}
