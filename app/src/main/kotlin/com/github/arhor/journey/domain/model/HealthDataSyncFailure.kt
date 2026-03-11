package com.github.arhor.journey.domain.model

sealed interface HealthDataSyncFailure {

    data object PermissionMissing : HealthDataSyncFailure

    data object UnavailableProvider : HealthDataSyncFailure

    data object EmptyData : HealthDataSyncFailure

    data class TransientError(
        val message: String? = null,
        val cause: Throwable? = null,
    ) : HealthDataSyncFailure
}
