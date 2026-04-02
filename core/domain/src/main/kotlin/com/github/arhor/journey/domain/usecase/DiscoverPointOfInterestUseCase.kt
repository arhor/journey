package com.github.arhor.journey.domain.usecase

import com.github.arhor.journey.core.common.Output
import com.github.arhor.journey.domain.model.error.UseCaseError
import com.github.arhor.journey.domain.repository.ExplorationRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiscoverPointOfInterestUseCase @Inject constructor(
    private val repository: ExplorationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(poiId: Long): Output<Unit, UseCaseError> =
        runSuspendingUseCaseCatching("discover point of interest") {
            repository.discoverPoi(
                poiId = poiId,
                discoveredAt = clock.instant(),
            )
        }
}
