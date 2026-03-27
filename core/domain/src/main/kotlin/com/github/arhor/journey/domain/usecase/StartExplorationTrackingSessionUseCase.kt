package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.model.StartExplorationTrackingSessionResult
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StartExplorationTrackingSessionUseCase @Inject constructor(
    private val controller: ExplorationTrackingSessionController,
) {
    suspend operator fun invoke(): StartExplorationTrackingSessionResult =
        controller.startSessionIfNeeded()
}
