package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.ExplorationTrackingCadence
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.tracking.ExplorationTrackingSessionController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetExplorationTrackingCadenceUseCase @Inject constructor(
    private val controller: ExplorationTrackingSessionController,
) {
    suspend operator fun invoke(cadence: ExplorationTrackingCadence): Output<Unit, UseCaseError> =
        runSuspendingUseCaseCatching("set exploration tracking cadence") {
            controller.setCadence(cadence)
        }
}
