package com.github.arhor.journey.domain.repository

import java.time.Instant

interface HealthSyncCheckpointRepository {

    suspend fun getLastSuccessfulSyncAt(): Instant?

    suspend fun setLastSuccessfulSyncAt(timestamp: Instant)
}
