package com.github.arhor.journey.domain.model

data class HealthDataSyncRequest(
    val timeRange: HealthDataTimeRange,
    val selectedDataTypes: Set<HealthDataType>,
    val mode: HealthDataSyncMode,
)
