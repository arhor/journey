package com.github.arhor.journey.domain.model

import java.time.Duration
import java.time.Instant

enum class HealthDataType {
    STEPS,
    SESSIONS,
}

enum class HealthDataSyncMode {
    MANUAL,
    BACKGROUND,
}

data class HealthDataTimeRange(
    val startTime: Instant,
    val endTime: Instant,
)

data class ImportedHealthEntry(
    val sourceId: String,
    val type: HealthDataType,
    val startTime: Instant,
    val endTime: Instant,
    val value: Long,
)

data class HealthDataSyncSummary(
    val importedEntriesCount: Int,
    val importedStepsCount: Long,
    val importedSessionsCount: Int,
    val importedSessionDuration: Duration,
)

data class HealthDataSyncPayload(
    val summary: HealthDataSyncSummary,
    val importedEntries: List<ImportedHealthEntry>,
)

data class HealthDataSyncRequest(
    val timeRange: HealthDataTimeRange,
    val selectedDataTypes: Set<HealthDataType>,
    val mode: HealthDataSyncMode,
)

sealed interface HealthDataSyncFailure {

    data object PermissionMissing : HealthDataSyncFailure

    data object UnavailableProvider : HealthDataSyncFailure

    data object EmptyData : HealthDataSyncFailure

    data class TransientError(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : HealthDataSyncFailure
}

sealed interface HealthDataSyncResult {

    data class Success(val payload: HealthDataSyncPayload) : HealthDataSyncResult

    data class Failure(val reason: HealthDataSyncFailure) : HealthDataSyncResult
}
