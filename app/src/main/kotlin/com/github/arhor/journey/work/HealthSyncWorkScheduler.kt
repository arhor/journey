package com.github.arhor.journey.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthSyncWorkScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(REPEAT_INTERVAL)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY.inWholeMinutes,
                TimeUnit.MINUTES,
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "health_sync_periodic"
        val REPEAT_INTERVAL: Duration = Duration.ofHours(6)
        val BACKOFF_DELAY: Duration = Duration.ofMinutes(30)
    }
}
