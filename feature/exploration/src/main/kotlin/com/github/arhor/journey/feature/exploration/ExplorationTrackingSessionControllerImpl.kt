package com.github.arhor.journey.feature.exploration

import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import com.github.arhor.journey.feature.exploration.location.LocationPermissionChecker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExplorationTrackingSessionControllerImpl @Inject constructor(
    private val runtime: ExplorationTrackingRuntime,
    private val permissionChecker: LocationPermissionChecker,
    private val serviceLauncher: ExplorationTrackingServiceLauncher,
) : ExplorationTrackingSessionController {

    override fun observeSession(): Flow<ExplorationTrackingSession> = runtime.observeSession()

    override suspend fun startSessionIfNeeded(): StartExplorationTrackingSessionResult {
        if (runtime.snapshot().isActive) {
            return StartExplorationTrackingSessionResult.AlreadyActive
        }

        if (!permissionChecker.hasAnyLocationPermission()) {
            runtime.markPermissionDenied()
            return StartExplorationTrackingSessionResult.PermissionRequired
        }

        return try {
            runtime.markStarting()
            serviceLauncher.start()
            StartExplorationTrackingSessionResult.Started
        } catch (e: Throwable) {
            runtime.stop()
            StartExplorationTrackingSessionResult.Failed(e.message)
        }
    }

    override suspend fun stopSession() {
        runtime.stop()
        serviceLauncher.stop()
    }

    override suspend fun setCadence(cadence: ExplorationTrackingCadence) {
        runtime.setCadence(cadence)
    }
}
