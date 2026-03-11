package com.github.arhor.journey.domain.model

sealed interface HealthDataSyncResult {

    data class Success(val payload: HealthDataSyncPayload) : HealthDataSyncResult

    data class Failure(val reason: HealthDataSyncFailure) : HealthDataSyncResult
}
