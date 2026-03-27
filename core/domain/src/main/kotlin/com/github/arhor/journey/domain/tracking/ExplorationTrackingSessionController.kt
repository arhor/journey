package com.github.arhor.journey.domain.tracking

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import kotlinx.coroutines.flow.Flow

interface ExplorationTrackingSessionController {
    fun observeSession(): Flow<ExplorationTrackingSession>

    suspend fun startSessionIfNeeded(): StartExplorationTrackingSessionResult

    suspend fun stopSession()

    suspend fun setCadence(cadence: ExplorationTrackingCadence)
}
