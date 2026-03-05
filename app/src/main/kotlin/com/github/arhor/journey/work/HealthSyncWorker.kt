package com.github.arhor.journey.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.arhor.journey.domain.model.HealthConnectAvailability
import com.github.arhor.journey.domain.model.HealthDataSyncFailure
import com.github.arhor.journey.domain.model.HealthDataSyncMode
import com.github.arhor.journey.domain.model.HealthDataTimeRange
import com.github.arhor.journey.domain.model.HealthDataType
import com.github.arhor.journey.domain.repository.HealthConnectAvailabilityRepository
import com.github.arhor.journey.domain.repository.HealthSyncCheckpointRepository
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCase
import com.github.arhor.journey.domain.usecase.SyncHealthDataUseCaseResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock
import java.time.Duration

@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncHealthData: SyncHealthDataUseCase,
    private val availabilityRepository: HealthConnectAvailabilityRepository,
    private val checkpointRepository: HealthSyncCheckpointRepository,
    private val clock: Clock,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (availabilityRepository.checkAvailability() != HealthConnectAvailability.AVAILABLE) {
            return Result.success()
        }

        val end = clock.instant()
        val start = maxOf(
            end.minus(MAX_INCREMENTAL_LOOKBACK),
            checkpointRepository.getLastSuccessfulSyncAt() ?: end.minus(DEFAULT_INITIAL_LOOKBACK),
        )

        val result = syncHealthData(
            timeRange = HealthDataTimeRange(startTime = start, endTime = end),
            selectedDataTypes = setOf(HealthDataType.SESSIONS),
            syncMode = HealthDataSyncMode.BACKGROUND,
        )

        return when (result) {
            is SyncHealthDataUseCaseResult.Success -> {
                checkpointRepository.setLastSuccessfulSyncAt(end)
                Result.success()
            }

            is SyncHealthDataUseCaseResult.Failure -> when (result.reason) {
                HealthDataSyncFailure.PermissionMissing,
                HealthDataSyncFailure.UnavailableProvider,
                HealthDataSyncFailure.EmptyData,
                -> Result.success()

                is HealthDataSyncFailure.TransientError -> Result.retry()
            }
        }
    }

    private companion object {
        val DEFAULT_INITIAL_LOOKBACK: Duration = Duration.ofDays(3)
        val MAX_INCREMENTAL_LOOKBACK: Duration = Duration.ofDays(14)
    }
}
