package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.error.StartExplorationTrackingSessionError
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartExplorationTrackingSessionUseCase @Inject constructor(
    private val controller: ExplorationTrackingSessionController,
) {
    suspend operator fun invoke(): Output<Unit, StartExplorationTrackingSessionError> =
        controller.startSessionIfNeeded()
}
