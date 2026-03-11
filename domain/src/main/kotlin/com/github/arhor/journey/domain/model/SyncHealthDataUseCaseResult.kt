package com.github.arhor.journey.domain.model

sealed interface SyncHealthDataUseCaseResult {

    data class Success(val payload: HealthDataSyncPayload) : SyncHealthDataUseCaseResult

    data class Failure(val reason: HealthDataSyncFailure) : SyncHealthDataUseCaseResult
}
