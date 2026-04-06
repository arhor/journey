package com.github.arhor.journey.feature.exploration

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import com.github.arhor.journey.feature.exploration.location.LocationPermissionChecker
import kotlinx.coroutines.CancellationException
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

    override suspend fun startSessionIfNeeded(): Output<Unit, StartExplorationTrackingSessionError> {
        if (runtime.snapshot().isActive) {
            return Output.Success(Unit)
        }

        if (!permissionChecker.hasAnyLocationPermission()) {
            runtime.markPermissionDenied()
            return Output.Failure(StartExplorationTrackingSessionError.PermissionRequired)
        }

        return try {
            runtime.markStarting()
            serviceLauncher.start()
            Output.Success(Unit)
        } catch (e: Throwable) {
            if (e is CancellationException) {
                throw e
            }
            runtime.stop()
            Output.Failure(StartExplorationTrackingSessionError.LaunchFailed(e))
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
