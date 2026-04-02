package com.github.arhor.journey.domain.tracking

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import kotlinx.coroutines.flow.Flow

interface ExplorationTrackingSessionController {
    fun observeSession(): Flow<ExplorationTrackingSession>

    suspend fun startSessionIfNeeded(): Output<Unit, StartExplorationTrackingSessionError>

    suspend fun stopSession()

    suspend fun setCadence(cadence: ExplorationTrackingCadence)
}
