package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StopExplorationTrackingSessionUseCase @Inject constructor(
    private val controller: ExplorationTrackingSessionController,
) {
    suspend operator fun invoke() {
        controller.stopSession()
    }
}
