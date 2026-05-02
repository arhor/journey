package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTrackingSession
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObserveExplorationTrackingSessionUseCase @Inject constructor(
    private val controller: ExplorationTrackingSessionController,
) {
    operator fun invoke(): Flow<Output<ExplorationTrackingSession, UseCaseError>> =
        controller.observeSession().toUseCaseOutputFlow("observe exploration tracking session")
}
